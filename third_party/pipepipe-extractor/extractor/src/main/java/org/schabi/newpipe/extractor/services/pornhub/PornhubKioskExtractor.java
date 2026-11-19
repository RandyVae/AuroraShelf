package org.schabi.newpipe.extractor.services.pornhub;

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

public final class PornhubKioskExtractor extends KioskExtractor<StreamInfoItem> {
    private Document document;

    public PornhubKioskExtractor(final StreamingService service,
                                 final ListLinkHandler linkHandler,
                                 final String kioskId) {
        super(service, linkHandler, kioskId);
    }

    @Override
    public void onFetchPage(@Nonnull final Downloader downloader)
            throws IOException, ExtractionException {
        document = PornhubParsingHelper.fetchDocument(getUrl());
    }

    @Nonnull
    @Override
    public String getName() {
        switch (getId()) {
            case "popular":
                return "Popular";
            case "recommended":
                return "Top Rated";
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
        return extractCurrentDocument();
    }

    private InfoItemsPage<StreamInfoItem> extractCurrentDocument()
            throws IOException, ExtractionException {
        if (document == null) {
            throw new org.schabi.newpipe.extractor.exceptions.ParsingException("Pornhub kiosk page was not fetched");
        }
        final StreamInfoItemsCollector collector = new StreamInfoItemsCollector(getServiceId());
        for (final PornhubSearchResult result : PornhubParsingHelper.extractVideoCards(document, 120)) {
            collector.commit(new PornhubInfoItemExtractor(result));
        }
        Page next = HtmlPagination.nextPage(document);
        if (next == null && !collector.getItems().isEmpty()) {
            final String loc = document.location();
            final java.util.regex.Matcher m = java.util.regex.Pattern.compile("([?&])page=(\\d+)").matcher(loc);
            final String nextUrl = m.find()
                    ? m.replaceFirst("$1page=" + (Integer.parseInt(m.group(2)) + 1))
                    : loc + (loc.contains("?") ? "&page=2" : "?page=2");
            if (HtmlPagination.isSafeNext(loc, nextUrl)) {
                next = new Page(nextUrl);
            }
        }
        return new InfoItemsPage<>(collector, next);
    }

    @Override
    public InfoItemsPage<StreamInfoItem> getPage(final Page page)
            throws IOException, ExtractionException {
        final String base = document != null ? document.location() : getUrl();
        if (page == null || !HtmlPagination.isSafeNext(base, page.getUrl())) {
            throw new org.schabi.newpipe.extractor.exceptions.ParsingException("Invalid next page");
        }
        document = PornhubParsingHelper.fetchDocument(page.getUrl());
        return extractCurrentDocument();
    }
}
