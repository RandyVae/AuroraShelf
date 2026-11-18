package org.schabi.newpipe.extractor.services.kissjav;

import org.jsoup.nodes.Document;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.utils.HtmlPagination;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.kiosk.KioskExtractor;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamInfoItemsCollector;

import java.io.IOException;

import javax.annotation.Nonnull;

public final class KissJavKioskExtractor extends KioskExtractor<StreamInfoItem> {
    private Document document;

    public KissJavKioskExtractor(final StreamingService service,
                                 final ListLinkHandler linkHandler,
                                 final String kioskId) {
        super(service, linkHandler, kioskId);
    }

    @Override
    public void onFetchPage(@Nonnull final Downloader downloader)
            throws IOException, ExtractionException {
        document = KissJavParsingHelper.fetchDocument(getUrl());
    }

    @Nonnull
    @Override
    public String getName() {
        switch (getId()) {
            case "popular":
                return "Popular";
            case "recommended":
                return "Recommended";
            case "latest":
            default:
                return "Latest";
        }
    }

    @Nonnull
    @Override
    public InfoItemsPage<StreamInfoItem> getInitialPage()
            throws IOException, ExtractionException {
        assertPageFetched();
        final StreamInfoItemsCollector collector =
                new StreamInfoItemsCollector(getServiceId());
        for (final KissJavSearchResult result
                : KissJavParsingHelper.extractVideoCards(document, 40)) {
            collector.commit(new KissJavInfoItemExtractor(result));
        }
        Page next = HtmlPagination.nextPage(document);
        if (next == null && !collector.getItems().isEmpty()) {
            final String loc = document.location();
            final String pathPart = loc.contains("?") ? loc.substring(0, loc.indexOf('?')) : loc;
            final String queryPart = loc.contains("?") ? loc.substring(loc.indexOf('?')) : "";
            final java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(https://[^/]+/(?:latest-updates|most-popular))(?:/(\\d+))?/?$").matcher(pathPart);
            if (m.find()) {
                final int nextNum = m.group(2) == null ? 2 : Integer.parseInt(m.group(2)) + 1;
                final String nextUrl = m.group(1) + "/" + nextNum + "/" + queryPart;
                if (HtmlPagination.isSafeNext(loc, nextUrl)) {
                    next = new Page(nextUrl);
                }
            }
        }
        return new InfoItemsPage<>(collector, next);
    }

    @Override
    public InfoItemsPage<StreamInfoItem> getPage(final Page page)
            throws IOException, ExtractionException {
        if (page == null || !HtmlPagination.isSafeNext(document.location(), page.getUrl())) {
            throw new org.schabi.newpipe.extractor.exceptions.ParsingException("Invalid next page");
        }
        document = KissJavParsingHelper.fetchDocument(page.getUrl());
        return getInitialPage();
    }
}
