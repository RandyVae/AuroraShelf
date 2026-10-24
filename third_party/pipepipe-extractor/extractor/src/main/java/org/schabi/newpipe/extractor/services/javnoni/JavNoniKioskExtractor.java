package org.schabi.newpipe.extractor.services.javnoni;

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

public final class JavNoniKioskExtractor extends KioskExtractor<StreamInfoItem> {
    private Document document;

    public JavNoniKioskExtractor(final StreamingService service,
                                 final ListLinkHandler linkHandler,
                                 final String kioskId) {
        super(service, linkHandler, kioskId);
    }

    @Override
    public void onFetchPage(@Nonnull final Downloader downloader)
            throws IOException, ExtractionException {
        document = JavNoniParsingHelper.fetchDocument(getUrl());
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
        final StreamInfoItemsCollector collector = new StreamInfoItemsCollector(getServiceId());
        for (final JavNoniSearchResult result
                : JavNoniParsingHelper.extractVideoCards(document, 40)) {
            collector.commit(new JavNoniInfoItemExtractor(result));
        }
        return new InfoItemsPage<>(collector, HtmlPagination.nextPage(document));
    }

    @Override
    public InfoItemsPage<StreamInfoItem> getPage(final Page page)
            throws IOException, ExtractionException {
        if (page == null || !HtmlPagination.isSafeNext(document.location(), page.getUrl())) {
            throw new org.schabi.newpipe.extractor.exceptions.ParsingException("Invalid next page");
        }
        document = JavNoniParsingHelper.fetchDocument(page.getUrl());
        return getInitialPage();
    }
}
