package org.schabi.newpipe.extractor.services.eightyfivepo;

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

public final class EightyFivePoKioskExtractor extends KioskExtractor<StreamInfoItem> {
    private Document document;

    public EightyFivePoKioskExtractor(final StreamingService service,
                                      final ListLinkHandler linkHandler,
                                      final String kioskId) {
        super(service, linkHandler, kioskId);
    }

    @Override
    public void onFetchPage(@Nonnull final Downloader downloader)
            throws IOException, ExtractionException {
        document = EightyFivePoParsingHelper.fetchDocument(getUrl());
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
        final StreamInfoItemsCollector collector = new StreamInfoItemsCollector(getServiceId());
        for (final EightyFivePoSearchResult result
                : EightyFivePoParsingHelper.extractVideoCards(document, 40)) {
            collector.commit(new EightyFivePoInfoItemExtractor(result));
        }
        return new InfoItemsPage<>(collector, nextPage(document));
    }

    /** KVS publishes its async pager parameters on the next link, not in href. */
    public static Page nextPage(final Document document) {
        final Page html = HtmlPagination.nextPage(document);
        if (html != null) return html;
        final org.jsoup.nodes.Element next = document.selectFirst(
                ".pagination a.next[data-block-id][data-parameters]");
        if (next == null) return null;
        final String block = next.attr("data-block-id");
        if (!block.matches("[a-zA-Z0-9_]+")) return null;
        final StringBuilder query = new StringBuilder("mode=async&function=get_block&block_id=")
                .append(block);
        for (final String parameter : next.attr("data-parameters").split(";")) {
            final String[] pair = parameter.split(":", 2);
            if (pair.length != 2 || !pair[0].matches("[a-zA-Z0-9_]+")) return null;
            query.append('&').append(pair[0]).append('=').append(
                    java.net.URLEncoder.encode(pair[1], java.nio.charset.StandardCharsets.UTF_8));
        }
        final String address = document.location().split("[?#]", 2)[0] + "?" + query;
        return HtmlPagination.isSafeNext(document.location(), address) ? new Page(address) : null;
    }

    @Override
    public InfoItemsPage<StreamInfoItem> getPage(final Page page)
            throws IOException, ExtractionException {
        if (page == null || !HtmlPagination.isSafeNext(document.location(), page.getUrl())) {
            throw new org.schabi.newpipe.extractor.exceptions.ParsingException("Invalid next page");
        }
        document = EightyFivePoParsingHelper.fetchDocument(page.getUrl());
        return getInitialPage();
    }
}
