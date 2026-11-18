import CryptoKit
import Darwin
import Foundation
import Network

/// Authenticated loopback HTTP relay for HLS manifests and media segments backed by `URLSession.permissive`.
/// Activated automatically on playback recovery so BunnyCDN `428 CDN-Limit` bursts and strict-ATS/TLS
/// CDN edge failures retry transparently without failing playback.
public final class HLSLoopbackRelayServer: @unchecked Sendable {
    public static let shared = HLSLoopbackRelayServer()

    private struct RelayContext: Sendable {
        let referer: String?
        let headers: [String: String]
    }

    private final class ConnectionLifecycleBox: @unchecked Sendable {
        private let lock = NSLock()
        private var headerCompleted = false
        private var upstreamTask: Task<Void, Never>?

        func markHeaderCompleted() -> Bool {
            lock.lock()
            defer { lock.unlock() }
            if headerCompleted { return false }
            headerCompleted = true
            return true
        }

        var isHeaderCompleted: Bool {
            lock.lock()
            defer { lock.unlock() }
            return headerCompleted
        }

        func setUpstreamTask(_ task: Task<Void, Never>) {
            lock.lock()
            upstreamTask = task
            lock.unlock()
        }

        func cancelUpstreamTask() {
            lock.lock()
            let task = upstreamTask
            upstreamTask = nil
            lock.unlock()
            task?.cancel()
        }
    }

    private static let signingKey = SymmetricKey(size: .bits256)
    private static let maxContextCapacity = 128

    private let queue = DispatchQueue(label: "com.aurorashelf.hls-relay-server", attributes: .concurrent)
    private let listenerQueue = DispatchQueue(label: "com.aurorashelf.hls-relay-listener")
    private let stateLock = NSLock()
    private let session: URLSession

    private var listener: NWListener?
    private var port: UInt16?
    private var waiters: [CheckedContinuation<UInt16?, Never>] = []
    private var contexts: [String: RelayContext] = [:]
    private var contextOrder: [String] = []

    private init() {
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 15
        config.timeoutIntervalForResource = 45
        config.httpMaximumConnectionsPerHost = 4
        config.httpAdditionalHeaders = [
            "User-Agent": PlaybackSourceResult.defaultUserAgent,
            "Accept": "*/*"
        ]
        self.session = URLSession.permissive(configuration: config)
    }

    /// Returns a localhost `http://127.0.0.1:<port>/playlist?...` URL proxying `streamURL`.
    public func relayURL(for streamURL: URL, referer: String?, headers: [String: String]) async -> URL? {
        guard !Self.isBlockedTargetHost(streamURL) else { return nil }
        guard let activePort = await ensureRunningPort() else { return nil }
        let token = registerContext(referer: referer, headers: headers)
        return Self.buildEndpointURL(
            originPrefix: "http://127.0.0.1:\(activePort)",
            endpoint: "playlist",
            targetURL: streamURL,
            token: token
        )
    }

    // MARK: - Playlist Rewriting & Signing (Pure / Unit-Testable)

    static func rewriteM3U8(
        _ content: String,
        baseURL: URL,
        originPrefix: String,
        token: String
    ) -> String {
        let lines = content.components(separatedBy: .newlines)
        var output: [String] = []
        output.reserveCapacity(lines.count)
        var expectingStreamInfURI = false

        for rawLine in lines {
            let trimmed = rawLine.trimmingCharacters(in: .whitespacesAndNewlines)
            if trimmed.isEmpty {
                output.append(rawLine)
                continue
            }
            if trimmed.hasPrefix("#") {
                if trimmed.uppercased().hasPrefix("#EXT-X-STREAM-INF") {
                    expectingStreamInfURI = true
                }
                output.append(rewriteAttributeURIs(in: rawLine, baseURL: baseURL, originPrefix: originPrefix, token: token))
                continue
            }

            let forcePlaylist = expectingStreamInfURI
            expectingStreamInfURI = false

            if let resolved = URL(string: trimmed, relativeTo: baseURL)?.absoluteURL,
               let proxied = buildEndpointURL(
                   originPrefix: originPrefix,
                   endpoint: (forcePlaylist || isPlaylistURL(resolved)) ? "playlist" : "segment",
                   targetURL: resolved,
                   token: token
               ) {
                output.append(proxied.absoluteString)
            } else {
                output.append(rawLine)
            }
        }
        return output.joined(separator: "\n")
    }

    private static func rewriteAttributeURIs(
        in line: String,
        baseURL: URL,
        originPrefix: String,
        token: String
    ) -> String {
        guard line.contains("URI=\"") else { return line }
        guard let regex = try? NSRegularExpression(pattern: #"URI="([^"]+)""#) else { return line }
        let nsLine = line as NSString
        let matches = regex.matches(in: line, range: NSRange(location: 0, length: nsLine.length))
        guard !matches.isEmpty else { return line }

        let upperTrimmed = line.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        let fixedEndpoint: String?
        if upperTrimmed.hasPrefix("#EXT-X-MEDIA") || upperTrimmed.hasPrefix("#EXT-X-I-FRAME-STREAM-INF") {
            fixedEndpoint = "playlist"
        } else if upperTrimmed.hasPrefix("#EXT-X-KEY") || upperTrimmed.hasPrefix("#EXT-X-MAP") {
            fixedEndpoint = "segment"
        } else {
            fixedEndpoint = nil
        }

        var mutable = line
        for match in matches.reversed() {
            let uriRange = match.range(at: 1)
            let rawURI = nsLine.substring(with: uriRange)
            guard let resolved = URL(string: rawURI, relativeTo: baseURL)?.absoluteURL else { continue }
            let endpoint = fixedEndpoint ?? (isPlaylistURL(resolved) ? "playlist" : "segment")
            guard let proxied = buildEndpointURL(originPrefix: originPrefix, endpoint: endpoint, targetURL: resolved, token: token),
                  let swiftRange = Range(uriRange, in: mutable) else {
                continue
            }
            mutable.replaceSubrange(swiftRange, with: proxied.absoluteString)
        }
        return mutable
    }

    static func isPlaylistURL(_ url: URL) -> Bool {
        let path = url.path.lowercased()
        return path.hasSuffix(".m3u8") || path.hasSuffix(".m3u")
    }

    static func buildEndpointURL(
        originPrefix: String,
        endpoint: String,
        targetURL: URL,
        token: String
    ) -> URL? {
        let targetString = targetURL.absoluteString
        let encodedURL = base64URLEncode(Data(targetString.utf8))
        let signature = computeSignature(endpoint: endpoint, targetURLString: targetString, token: token)
        var components = URLComponents(string: "\(originPrefix)/\(endpoint)")
        components?.queryItems = [
            URLQueryItem(name: "u", value: encodedURL),
            URLQueryItem(name: "t", value: token),
            URLQueryItem(name: "s", value: signature)
        ]
        return components?.url
    }

    static func computeSignature(endpoint: String, targetURLString: String, token: String) -> String {
        let message = Data("\(endpoint)|\(targetURLString)|\(token)".utf8)
        let mac = HMAC<SHA256>.authenticationCode(for: message, using: signingKey)
        return base64URLEncode(Data(mac))
    }

    static func verifySignature(_ signature: String, endpoint: String, targetURLString: String, token: String) -> Bool {
        guard let sigData = base64URLDecodeData(signature) else { return false }
        let message = Data("\(endpoint)|\(targetURLString)|\(token)".utf8)
        return HMAC<SHA256>.isValidAuthenticationCode(sigData, authenticating: message, using: signingKey)
    }

    /// Blocks loopback, RFC1918 private, link-local, CGNAT, and unspecified target addresses to prevent SSRF.
    static func isBlockedTargetHost(_ url: URL) -> Bool {
        guard let scheme = url.scheme?.lowercased(), scheme == "http" || scheme == "https" else {
            return true
        }
        guard var host = url.host?.trimmingCharacters(in: .whitespacesAndNewlines).lowercased(),
              !host.isEmpty else {
            return true
        }
        if host.hasPrefix("[") && host.hasSuffix("]") {
            host = String(host.dropFirst().dropLast())
        }
        if let zoneIdx = host.firstIndex(of: "%") {
            host = String(host[..<zoneIdx])
        }
        if host == "localhost" || host.hasSuffix(".localhost") || host.hasSuffix(".local") || host == "0.0.0.0" || host == "::" || host == "::1" {
            return true
        }

        var addr4 = in_addr()
        if inet_pton(AF_INET, host, &addr4) == 1 {
            return isBlockedIPv4(UInt32(bigEndian: addr4.s_addr))
        }

        var addr6 = in6_addr()
        if inet_pton(AF_INET6, host, &addr6) == 1 {
            let bytes = withUnsafeBytes(of: &addr6) { Array($0) }
            guard bytes.count == 16 else { return true }
            let first15Zero = bytes[0..<15].allSatisfy { $0 == 0 }
            if first15Zero && (bytes[15] == 0 || bytes[15] == 1) {
                return true // :: or ::1
            }
            if bytes[0] == 0xFE && (bytes[1] & 0xC0) == 0x80 {
                return true // fe80::/10 link-local
            }
            if (bytes[0] & 0xFE) == 0xFC {
                return true // fc00::/7 unique-local
            }
            if bytes[0] == 0xFF {
                return true // multicast
            }
            // ::ffff:a.b.c.d IPv4-mapped IPv6
            if bytes[0..<10].allSatisfy({ $0 == 0 }) && bytes[10] == 0xFF && bytes[11] == 0xFF {
                let mapped = (UInt32(bytes[12]) << 24)
                    | (UInt32(bytes[13]) << 16)
                    | (UInt32(bytes[14]) << 8)
                    | UInt32(bytes[15])
                return isBlockedIPv4(mapped)
            }
        }
        return false
    }

    private static func isBlockedIPv4(_ ip: UInt32) -> Bool {
        let firstOctet = ip >> 24
        if firstOctet == 0 || firstOctet == 10 || firstOctet == 127 || firstOctet >= 224 {
            return true
        }
        if (ip >> 16) == 0xA9FE { // 169.254.0.0/16
            return true
        }
        if (ip & 0xFFF0_0000) == 0xAC10_0000 { // 172.16.0.0/12
            return true
        }
        if (ip >> 16) == 0xC0A8 { // 192.168.0.0/16
            return true
        }
        if (ip & 0xFFC0_0000) == 0x6440_0000 { // 100.64.0.0/10
            return true
        }
        return false
    }

    private static func base64URLEncode(_ data: Data) -> String {
        data.base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
    }

    private static func base64URLDecodeData(_ encoded: String) -> Data? {
        var b64 = encoded
            .replacingOccurrences(of: "-", with: "+")
            .replacingOccurrences(of: "_", with: "/")
        let remainder = b64.count % 4
        if remainder != 0 {
            b64 += String(repeating: "=", count: 4 - remainder)
        }
        return Data(base64Encoded: b64)
    }

    private static func base64URLDecode(_ encoded: String) -> String? {
        guard let data = base64URLDecodeData(encoded) else { return nil }
        return String(data: data, encoding: .utf8)
    }

    // MARK: - Context Registry (LRU = 128)

    func registerContextForTesting(referer: String? = nil, headers: [String: String] = [:]) -> String {
        registerContext(referer: referer, headers: headers)
    }

    private func registerContext(referer: String?, headers: [String: String]) -> String {
        let token = UUID().uuidString.replacingOccurrences(of: "-", with: "").lowercased()
        stateLock.lock()
        contexts[token] = RelayContext(referer: referer, headers: headers)
        contextOrder.append(token)
        while contextOrder.count > Self.maxContextCapacity {
            let evicted = contextOrder.removeFirst()
            contexts.removeValue(forKey: evicted)
        }
        stateLock.unlock()
        return token
    }

    private func context(for token: String?) -> RelayContext? {
        guard let token, !token.isEmpty else { return nil }
        stateLock.lock()
        defer { stateLock.unlock() }
        guard let ctx = contexts[token] else { return nil }
        if let idx = contextOrder.firstIndex(of: token) {
            contextOrder.remove(at: idx)
            contextOrder.append(token)
        }
        return ctx
    }

    // MARK: - Listener Lifecycle

    private func currentPort() -> UInt16? {
        stateLock.withLock { port }
    }

    private func ensureRunningPort() async -> UInt16? {
        if let activePort = currentPort() {
            return activePort
        }

        return await withCheckedContinuation { (cont: CheckedContinuation<UInt16?, Never>) in
            stateLock.lock()
            if let activePort = port {
                stateLock.unlock()
                cont.resume(returning: activePort)
                return
            }
            waiters.append(cont)
            let needsStart = (listener == nil)
            stateLock.unlock()

            if needsStart {
                listenerQueue.async { [weak self] in
                    self?.startListenerIfNeeded()
                }
            }

            listenerQueue.asyncAfter(deadline: .now() + 2.5) { [weak self] in
                guard let self else { return }
                self.stateLock.lock()
                if self.port == nil, !self.waiters.isEmpty {
                    let pending = self.waiters
                    self.waiters.removeAll()
                    self.stateLock.unlock()
                    pending.forEach { $0.resume(returning: nil) }
                } else {
                    self.stateLock.unlock()
                }
            }
        }
    }

    private func startListenerIfNeeded() {
        stateLock.lock()
        if listener != nil {
            stateLock.unlock()
            return
        }
        stateLock.unlock()

        do {
            let params = NWParameters.tcp
            params.allowLocalEndpointReuse = true
            params.requiredLocalEndpoint = NWEndpoint.hostPort(host: .ipv4(.loopback), port: .any)
            let newListener = try NWListener(using: params)
            newListener.stateUpdateHandler = { [weak self] state in
                guard let self else { return }
                switch state {
                case .ready:
                    if let assignedPort = newListener.port?.rawValue {
                        self.stateLock.lock()
                        self.port = assignedPort
                        let pending = self.waiters
                        self.waiters.removeAll()
                        self.stateLock.unlock()
                        pending.forEach { $0.resume(returning: assignedPort) }
                    }
                case .failed, .cancelled:
                    self.stateLock.lock()
                    self.listener = nil
                    self.port = nil
                    let pending = self.waiters
                    self.waiters.removeAll()
                    self.stateLock.unlock()
                    pending.forEach { $0.resume(returning: nil) }
                default:
                    break
                }
            }
            newListener.newConnectionHandler = { [weak self] connection in
                self?.handleConnection(connection)
            }
            stateLock.lock()
            self.listener = newListener
            stateLock.unlock()
            newListener.start(queue: listenerQueue)
        } catch {
            stateLock.lock()
            let pending = waiters
            waiters.removeAll()
            stateLock.unlock()
            pending.forEach { $0.resume(returning: nil) }
        }
    }

    // MARK: - HTTP Connection Handling

    private func handleConnection(_ connection: NWConnection) {
        let lifecycle = ConnectionLifecycleBox()
        connection.stateUpdateHandler = { state in
            switch state {
            case .failed, .cancelled:
                lifecycle.cancelUpstreamTask()
            default:
                break
            }
        }
        connection.start(queue: queue)

        // 5-second header read timeout to prevent stalled loopback sockets
        queue.asyncAfter(deadline: .now() + 5.0) {
            if !lifecycle.isHeaderCompleted {
                connection.cancel()
            }
        }

        receiveRequestHeader(on: connection, buffer: Data(), lifecycle: lifecycle)
    }

    private func receiveRequestHeader(on connection: NWConnection, buffer: Data, lifecycle: ConnectionLifecycleBox) {
        connection.receive(minimumIncompleteLength: 1, maximumLength: 8192) { [weak self] data, _, isComplete, error in
            guard let self else {
                connection.cancel()
                return
            }
            if error != nil {
                connection.cancel()
                return
            }
            var accumulated = buffer
            if let data { accumulated.append(data) }

            let headerEndMarker = Data("\r\n\r\n".utf8)
            if let range = accumulated.range(of: headerEndMarker),
               let headerText = String(data: accumulated[..<range.lowerBound], encoding: .utf8) {
                guard lifecycle.markHeaderCompleted() else { return }
                let task = Task.detached(priority: .userInitiated) {
                    await self.processHTTPRequest(headerText: headerText, on: connection)
                }
                lifecycle.setUpstreamTask(task)
                return
            }

            if isComplete || accumulated.count > 16384 {
                connection.cancel()
                return
            }
            self.receiveRequestHeader(on: connection, buffer: accumulated, lifecycle: lifecycle)
        }
    }

    private func processHTTPRequest(headerText: String, on connection: NWConnection) async {
        let lines = headerText.components(separatedBy: "\r\n")
        guard let requestLine = lines.first else {
            sendSimpleError(statusCode: 400, reason: "Bad Request", on: connection)
            return
        }
        let parts = requestLine.split(separator: " ", omittingEmptySubsequences: true)
        guard parts.count >= 2 else {
            sendSimpleError(statusCode: 400, reason: "Bad Request", on: connection)
            return
        }
        let method = String(parts[0]).uppercased()
        guard method == "GET" || method == "HEAD" else {
            sendSimpleError(statusCode: 405, reason: "Method Not Allowed", on: connection)
            return
        }
        let rawTarget = String(parts[1])

        guard let components = URLComponents(string: "http://127.0.0.1\(rawTarget)"),
              let encodedURL = components.queryItems?.first(where: { $0.name == "u" })?.value,
              let decodedURLString = Self.base64URLDecode(encodedURL),
              let upstreamURL = URL(string: decodedURLString),
              upstreamURL.scheme == "https" || upstreamURL.scheme == "http" else {
            sendSimpleError(statusCode: 400, reason: "Invalid Target", on: connection)
            return
        }

        let token = components.queryItems?.first(where: { $0.name == "t" })?.value ?? ""
        let signature = components.queryItems?.first(where: { $0.name == "s" })?.value ?? ""
        let endpointName = components.path == "/playlist" ? "playlist" : (components.path == "/segment" ? "segment" : "")

        guard (components.path == "/playlist" || components.path == "/segment"),
              let ctx = context(for: token),
              Self.verifySignature(signature, endpoint: endpointName, targetURLString: decodedURLString, token: token),
              !Self.isBlockedTargetHost(upstreamURL) else {
            sendSimpleError(statusCode: 403, reason: "Forbidden", on: connection)
            return
        }

        var rangeHeader: String?
        for line in lines.dropFirst() {
            if let colon = line.firstIndex(of: ":") {
                let name = line[..<colon].trimmingCharacters(in: .whitespaces).lowercased()
                if name == "range" {
                    rangeHeader = line[line.index(after: colon)...].trimmingCharacters(in: .whitespaces)
                }
            }
        }

        guard let activePort = currentPort() else {
            sendSimpleError(statusCode: 503, reason: "Service Unavailable", on: connection)
            return
        }
        let originPrefix = "http://127.0.0.1:\(activePort)"
        let path = components.path
        let upstreamMethod = (path == "/playlist") ? "GET" : method

        do {
            try Task.checkCancellation()
            let (data, httpResp) = try await fetchWithRetries(
                url: upstreamURL,
                method: upstreamMethod,
                rangeHeader: rangeHeader,
                context: ctx
            )
            try Task.checkCancellation()
            let status = httpResp.statusCode
            let finalURL = httpResp.url ?? upstreamURL

            if path == "/playlist" {
                guard let playlistText = String(data: data, encoding: .utf8) else {
                    sendSimpleError(statusCode: 502, reason: "Bad Gateway", on: connection)
                    return
                }
                let rewritten = Self.rewriteM3U8(
                    playlistText,
                    baseURL: finalURL,
                    originPrefix: originPrefix,
                    token: token
                )
                let outData = Data(rewritten.utf8)
                sendResponse(
                    statusCode: 200,
                    contentType: "application/vnd.apple.mpegurl",
                    body: method == "HEAD" ? Data() : outData,
                    contentLength: outData.count,
                    extraHeaders: ["Cache-Control": "no-store"],
                    on: connection
                )
                return
            }

            // /segment response (preserving 200 / 206 partial content & upstream Content-Length on HEAD)
            let contentType = httpResp.value(forHTTPHeaderField: "Content-Type")
                ?? (finalURL.path.lowercased().hasSuffix(".ts") ? "video/mp2t" : "application/octet-stream")
            var extra: [String: String] = ["Accept-Ranges": "bytes"]
            if let cr = httpResp.value(forHTTPHeaderField: "Content-Range") {
                extra["Content-Range"] = cr
            }
            let effectiveContentLength: Int
            if method == "HEAD" {
                if let headerLen = httpResp.value(forHTTPHeaderField: "Content-Length").flatMap(Int.init), headerLen >= 0 {
                    effectiveContentLength = headerLen
                } else if httpResp.expectedContentLength > 0 {
                    effectiveContentLength = Int(httpResp.expectedContentLength)
                } else {
                    effectiveContentLength = data.count
                }
            } else {
                effectiveContentLength = data.count
            }
            sendResponse(
                statusCode: status,
                contentType: contentType,
                body: method == "HEAD" ? Data() : data,
                contentLength: effectiveContentLength,
                extraHeaders: extra,
                on: connection
            )
        } catch is CancellationError {
            connection.cancel()
        } catch let error as RelayHTTPError {
            sendSimpleError(statusCode: error.statusCode, reason: Self.statusReasonPhrase(for: error.statusCode), on: connection)
        } catch {
            sendSimpleError(statusCode: 502, reason: "Bad Gateway", on: connection)
        }
    }

    private struct RelayHTTPError: Error {
        let statusCode: Int
    }

    private func fetchWithRetries(
        url: URL,
        method: String,
        rangeHeader: String?,
        context: RelayContext
    ) async throws -> (Data, HTTPURLResponse) {
        let maxAttempts = 4
        var lastError: Error?

        for attempt in 0..<maxAttempts {
            try Task.checkCancellation()
            var request = URLRequest(url: url, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 15)
            request.httpMethod = method == "HEAD" ? "HEAD" : "GET"
            for (k, v) in context.headers {
                if k.caseInsensitiveCompare("Origin") == .orderedSame,
                   let originHost = URL(string: v)?.host?.lowercased(),
                   let targetHost = url.host?.lowercased(),
                   originHost != targetHost {
                    continue
                }
                request.setValue(v, forHTTPHeaderField: k)
            }
            if let ref = context.referer, !ref.isEmpty {
                request.setValue(ref, forHTTPHeaderField: "Referer")
            }
            if let rangeHeader, !rangeHeader.isEmpty {
                request.setValue(rangeHeader, forHTTPHeaderField: "Range")
            }

            do {
                let (data, response) = try await session.data(for: request)
                guard let http = response as? HTTPURLResponse else {
                    throw URLError(.badServerResponse)
                }
                if let finalURL = http.url, Self.isBlockedTargetHost(finalURL) {
                    throw RelayHTTPError(statusCode: 403)
                }
                if (200...299).contains(http.statusCode) {
                    return (data, http)
                }
                let retryable = http.statusCode == 408 || http.statusCode == 428 || http.statusCode == 429 || (500...599).contains(http.statusCode)
                if !retryable || attempt == maxAttempts - 1 {
                    throw RelayHTTPError(statusCode: http.statusCode)
                }
                let retryAfter = (http.value(forHTTPHeaderField: "Retry-After")).flatMap(Double.init)
                let backoff = retryAfter ?? [0.8, 1.6, 2.5][min(attempt, 2)]
                try await Task.sleep(for: .seconds(min(max(backoff, 0.4), 5.0)))
            } catch is CancellationError {
                throw CancellationError()
            } catch let httpErr as RelayHTTPError {
                throw httpErr
            } catch {
                lastError = error
                if attempt == maxAttempts - 1 {
                    throw error
                }
                let backoff = [0.6, 1.4, 2.2][min(attempt, 2)]
                try await Task.sleep(for: .seconds(backoff))
            }
        }
        throw lastError ?? URLError(.cannotLoadFromNetwork)
    }

    static func statusReasonPhrase(for statusCode: Int) -> String {
        switch statusCode {
        case 200: return "OK"
        case 206: return "Partial Content"
        case 400: return "Bad Request"
        case 403: return "Forbidden"
        case 404: return "Not Found"
        case 405: return "Method Not Allowed"
        case 416: return "Range Not Satisfiable"
        case 502: return "Bad Gateway"
        case 503: return "Service Unavailable"
        default:
            let localized = HTTPURLResponse.localizedString(forStatusCode: statusCode)
            return localized.isEmpty ? "Error" : localized.capitalized
        }
    }

    private func sendSimpleError(statusCode: Int, reason: String, on connection: NWConnection) {
        let body = Data(reason.utf8)
        sendResponse(
            statusCode: statusCode,
            contentType: "text/plain; charset=utf-8",
            body: body,
            contentLength: body.count,
            extraHeaders: [:],
            on: connection
        )
    }

    private func sendResponse(
        statusCode: Int,
        contentType: String,
        body: Data,
        contentLength: Int,
        extraHeaders: [String: String],
        on connection: NWConnection
    ) {
        let reason = Self.statusReasonPhrase(for: statusCode)
        var header = "HTTP/1.1 \(statusCode) \(reason)\r\n"
        header += "Content-Type: \(contentType)\r\n"
        header += "Content-Length: \(contentLength)\r\n"
        header += "Connection: close\r\n"
        for (k, v) in extraHeaders {
            header += "\(k): \(v)\r\n"
        }
        header += "\r\n"

        let headerData = Data(header.utf8)
        if body.isEmpty {
            connection.send(content: headerData, isComplete: true, completion: .contentProcessed { _ in
                connection.cancel()
            })
        } else {
            // Stream header and body in separate frames to avoid reallocating/copying large segment Data
            connection.send(content: headerData, isComplete: false, completion: .idempotent)
            connection.send(content: body, isComplete: true, completion: .contentProcessed { _ in
                connection.cancel()
            })
        }
    }
}
