package org.schabi.newpipe.extractor.utils;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.schabi.newpipe.extractor.Page;

/** Follows explicit HTML next-page links, never guesses URLs or follows other hosts. */
public final class HtmlPagination {
    private HtmlPagination() { }

    public static Page nextPage(final Document document) {
        for (final Element link : document.select(
                "a[rel~=next], link[rel~=next], .pagination .next a, "
                + ".pagination a.next, .pagination-next a, a.pagination-next, "
                + ".paging .next a, .paging a.next, .page-next a, a.page-next, "
                + ".pagination a[aria-label*=Next], .pagination a[aria-label*=next], "
                + ".pagination a[title*=Next], .pagination a.next-page, a.next.page-numbers, a.prevnext")) {
            // Tokyo Motion uses the same class for directional pager links.
            if (link.hasClass("prevnext") && !"»".equals(link.text().trim())) continue;
            final String raw = link.attr("href");
            // Some localized pagers encode the entire absolute URL as a path.
            final String address;
            try {
                address = raw.regionMatches(true, 0, "https%3a%2f%2f", 0, 13)
                        ? URLDecoder.decode(raw, StandardCharsets.UTF_8) : link.absUrl("href");
            } catch (final IllegalArgumentException malformedEncoding) {
                // Ignore malformed server markup and try another pager link.
                continue;
            }
            if (isSafeNext(document.location(), address)) return new Page(address);
        }
        return null;
    }

    public static boolean isSafeNext(final String current, final String target) {
        try {
            final URI base = URI.create(current);
            final URI next = URI.create(target);
            return "https".equalsIgnoreCase(next.getScheme())
                    && base.getHost() != null && base.getHost().equalsIgnoreCase(next.getHost())
                    && base.getPort() == next.getPort() && next.getUserInfo() == null
                    && next.getFragment() == null && !base.equals(next);
        } catch (final IllegalArgumentException invalid) {
            return false;
        }
    }
}
