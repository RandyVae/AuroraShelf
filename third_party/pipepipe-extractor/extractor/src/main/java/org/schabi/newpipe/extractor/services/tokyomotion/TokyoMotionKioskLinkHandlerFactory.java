package org.schabi.newpipe.extractor.services.tokyomotion;

import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandlerFactory;
import org.schabi.newpipe.extractor.search.filter.FilterItem;

import java.util.List;

public final class TokyoMotionKioskLinkHandlerFactory extends ListLinkHandlerFactory {
    private static final TokyoMotionKioskLinkHandlerFactory INSTANCE =
            new TokyoMotionKioskLinkHandlerFactory();

    public static TokyoMotionKioskLinkHandlerFactory getInstance() {
        return INSTANCE;
    }

    private TokyoMotionKioskLinkHandlerFactory() {
    }

    @Override
    public String getId(final String url) {
        if (url != null) {
            if (url.contains("o=bw")) return "watching";
            if (url.contains("o=mv") || url.contains("sort=popular")) return "popular";
            if (url.contains("o=tr") || url.contains("sort=rating")) return "recommended";
            if (url.contains("o=tf")) return "favorited";
            if (url.contains("o=lg")) return "longest";
        }
        return "latest";
    }

    @Override
    public String getUrl(final String id,
                         final List<FilterItem> selectedContentFilter,
                         final List<FilterItem> selectedSortFilter) throws ParsingException {
        switch (id) {
            case "watching":
                return TokyoMotionParsingHelper.BASE_URL + "/videos?o=bw";
            case "popular":
                return TokyoMotionParsingHelper.BASE_URL + "/videos?o=mv";
            case "recommended":
                return TokyoMotionParsingHelper.BASE_URL + "/videos?o=tr";
            case "favorited":
                return TokyoMotionParsingHelper.BASE_URL + "/videos?o=tf";
            case "longest":
                return TokyoMotionParsingHelper.BASE_URL + "/videos?o=lg";
            case "latest":
            default:
                return TokyoMotionParsingHelper.BASE_URL + "/videos?o=mr";
        }
    }

    @Override
    public boolean onAcceptUrl(final String url) {
        return url != null && TokyoMotionParsingHelper.normalizeUrl(url).contains("tokyomotion.net/");
    }
}

