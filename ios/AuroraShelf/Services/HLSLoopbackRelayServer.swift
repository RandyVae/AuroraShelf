import Foundation
import Network

/// Loopback HTTP relay for HLS manifests and `.ts` media segments backed by `URLSession.permissive`.
/// Activated automatically on playback recovery so BunnyCDN `428 CDN-Limit` bursts and strict-ATS/TLS
/// CDN edge failures (such as non-standard ports or TLS resets) retry transparently without failing playback.
public final class HLSLoopbackRelayServer: @unchecked Sendable {
    public static let shared = HLSLoopbackRelayServer()

    private struct RelayContext: Sendable {
        let referer: String?
        let headers: [String: String]
    }

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
        guard let activePort = await ensureRunningPort() else { return nil }
        let token = registerContext(referer: referer, headers: headers)
        return Self.buildEndpointURL(
            originPrefix: "http://127.0.0.1:\(activePort)",
            endpoint: "playlist",
            targetURL: streamURL,
            token: token
        )
    }

    // MARK: - Playlist Rewriting (Pure / Unit-Testable)

    static func rewriteM3U8(
        _ content: String,
        baseURL: URL,
        originPrefix: String,
        token: String
    ) -> String {
        let lines = content.components(separatedBy: .newlines)
        var output: [String] = []
        output.reserveCapacity(lines.count)

        for rawLine in lines {
            let trimmed = rawLine.trimmingCharacters(in: .whitespacesAndNewlines)
            if trimmed.isEmpty {
                output.append(rawLine)
                continue
            }
            if trimmed.hasPrefix("#") {
                output.append(rewriteAttributeURIs(in: rawLine, baseURL: baseURL, originPrefix: originPrefix, token: token))
                continue
            }
            if let resolved = URL(string: trimmed, relativeTo: baseURL)?.absoluteURL,
               let proxied = buildEndpointURL(
                   originPrefix: originPrefix,
                   endpoint: isPlaylistURL(resolved) ? "playlist" : "segment",
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

        var mutable = line
        for match in matches.reversed() {
            let uriRange = match.range(at: 1)
            let rawURI = nsLine.substring(with: uriRange)
            guard let resolved = URL(string: rawURI, relativeTo: baseURL)?.absoluteURL else { continue }
            let endpoint = isPlaylistURL(resolved) ? "playlist" : "segment"
            guard let proxied = buildEndpointURL(originPrefix: originPrefix, endpoint: endpoint, targetURL: resolved, token: token),
                  let swiftRange = Range(uriRange, in: mutable) else {
                continue
            }
            mutable.replaceSubrange(swiftRange, with: proxied.absoluteString)
        }
        return mutable
    }

    private static func isPlaylistURL(_ url: URL) -> Bool {
        let path = url.path.lowercased()
        if path.hasSuffix(".m3u8") || path.hasSuffix(".m3u") {
            return true
        }
        return url.absoluteString.lowercased().contains(".m3u8")
    }

    private static func buildEndpointURL(
        originPrefix: String,
        endpoint: String,
        targetURL: URL,
        token: String
    ) -> URL? {
        let encodedURL = base64URLEncode(targetURL.absoluteString)
        var components = URLComponents(string: "\(originPrefix)/\(endpoint)")
        components?.queryItems = [
            URLQueryItem(name: "u", value: encodedURL),
            URLQueryItem(name: "t", value: token)
        ]
        return components?.url
    }

    private static func base64URLEncode(_ text: String) -> String {
        Data(text.utf8)
            .base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
    }

    private static func base64URLDecode(_ encoded: String) -> String? {
        var b64 = encoded
            .replacingOccurrences(of: "-", with: "+")
            .replacingOccurrences(of: "_", with: "/")
        let remainder = b64.count % 4
        if remainder != 0 {
            b64 += String(repeating: "=", count: 4 - remainder)
        }
        guard let data = Data(base64Encoded: b64) else { return nil }
        return String(data: data, encoding: .utf8)
    }

    // MARK: - Context Registry

    private func registerContext(referer: String?, headers: [String: String]) -> String {
        let token = UUID().uuidString.replacingOccurrences(of: "-", with: "").lowercased()
        stateLock.lock()
        contexts[token] = RelayContext(referer: referer, headers: headers)
        contextOrder.append(token)
        while contextOrder.count > 32 {
            let evicted = contextOrder.removeFirst()
            contexts.removeValue(forKey: evicted)
        }
        stateLock.unlock()
        return token
    }

    private func context(for token: String?) -> RelayContext? {
        guard let token, !token.isEmpty else { return nil }
        stateLock.lock()
        let ctx = contexts[token]
        stateLock.unlock()
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
        connection.start(queue: queue)
        receiveRequestHeader(on: connection, buffer: Data())
    }

    private func receiveRequestHeader(on connection: NWConnection, buffer: Data) {
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
                Task.detached(priority: .userInitiated) {
                    await self.processHTTPRequest(headerText: headerText, on: connection)
                }
                return
            }

            if isComplete || accumulated.count > 16384 {
                connection.cancel()
                return
            }
            self.receiveRequestHeader(on: connection, buffer: accumulated)
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
        let ctx = context(for: token)

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
            sendSimpleError(statusCode: 503, reason: "Relay Unavailable", on: connection)
            return
        }
        let originPrefix = "http://127.0.0.1:\(activePort)"

        do {
            let (data, httpResp) = try await fetchWithRetries(
                url: upstreamURL,
                method: method,
                rangeHeader: rangeHeader,
                context: ctx
            )
            let status = httpResp.statusCode
            let finalURL = httpResp.url ?? upstreamURL
            let path = components.path

            if path == "/playlist" {
                guard let playlistText = String(data: data, encoding: .utf8) else {
                    sendSimpleError(statusCode: 502, reason: "Bad Playlist Encoding", on: connection)
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

            // /segment response (preserving 200 / 206 partial content semantics)
            let contentType = httpResp.value(forHTTPHeaderField: "Content-Type")
                ?? (finalURL.path.lowercased().hasSuffix(".ts") ? "video/mp2t" : "application/octet-stream")
            var extra: [String: String] = ["Accept-Ranges": "bytes"]
            if let cr = httpResp.value(forHTTPHeaderField: "Content-Range") {
                extra["Content-Range"] = cr
            }
            sendResponse(
                statusCode: status,
                contentType: contentType,
                body: method == "HEAD" ? Data() : data,
                contentLength: data.count,
                extraHeaders: extra,
                on: connection
            )
        } catch let error as RelayHTTPError {
            sendSimpleError(statusCode: error.statusCode, reason: "Upstream Error", on: connection)
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
        context: RelayContext?
    ) async throws -> (Data, HTTPURLResponse) {
        let maxAttempts = 4
        var lastError: Error?

        for attempt in 0..<maxAttempts {
            var request = URLRequest(url: url, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 15)
            request.httpMethod = method == "HEAD" ? "HEAD" : "GET"
            if let context {
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
            }
            if let rangeHeader, !rangeHeader.isEmpty {
                request.setValue(rangeHeader, forHTTPHeaderField: "Range")
            }

            do {
                let (data, response) = try await session.data(for: request)
                guard let http = response as? HTTPURLResponse else {
                    throw URLError(.badServerResponse)
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
            } catch let httpErr as RelayHTTPError {
                throw httpErr
            } catch {
                lastError = error
                if attempt == maxAttempts - 1 {
                    throw error
                }
                let backoff = [0.6, 1.4, 2.2][min(attempt, 2)]
                try? await Task.sleep(for: .seconds(backoff))
            }
        }
        throw lastError ?? URLError(.cannotLoadFromNetwork)
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
        var header = "HTTP/1.1 \(statusCode) OK\r\n"
        header += "Content-Type: \(contentType)\r\n"
        header += "Content-Length: \(contentLength)\r\n"
        header += "Connection: close\r\n"
        for (k, v) in extraHeaders {
            header += "\(k): \(v)\r\n"
        }
        header += "\r\n"
        var payload = Data(header.utf8)
        payload.append(body)
        connection.send(content: payload, completion: .contentProcessed { _ in
            connection.cancel()
        })
    }
}
