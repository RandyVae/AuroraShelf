package org.schabi.newpipe.extractor.services.missav;

import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandlerFactory;
import org.schabi.newpipe.extractor.search.filter.FilterItem;

import java.util.List;

public final class MissAvKioskLinkHandlerFactory extends ListLinkHandlerFactory {
    private static final MissAvKioskLinkHandlerFactory INSTANCE =
            new MissAvKioskLinkHandlerFactory();

    public static MissAvKioskLinkHandlerFactory getInstance() {
        return INSTANCE;
    }

    private MissAvKioskLinkHandlerFactory() {
    }

    @Override
    public String getId(final String url) {
        if (url != null) {
            if (url.contains("/release")) return "release";
            if (url.contains("/chinese-subtitle")) return "chinese-subtitle";
            if (url.contains("/today-hot")) return "today-hot";
            if (url.contains("/weekly-hot")) return "weekly-hot";
            if (url.contains("/monthly-hot")) return "monthly-hot";
            if (url.contains("/uncensored-leak")) return "uncensored-leak";
            if (url.contains("/siro")) return "siro";
            if (url.contains("/fc2")) return "fc2";
        }
        return "latest";
    }

    @Override
    public String getUrl(final String id, final List<FilterItem> contentFilter,
                         final List<FilterItem> sortFilter) throws ParsingException {
        final String lang = MissAvParsingHelper.DEFAULT_LANGUAGE;
        switch (id) {
            case "release":
            case "popular":
                return MissAvParsingHelper.BASE_URL + "/" + lang + "/release";
            case "chinese-subtitle":
                return MissAvParsingHelper.BASE_URL + "/" + lang + "/chinese-subtitle";
            case "today-hot":
                return MissAvParsingHelper.BASE_URL + "/" + lang + "/today-hot";
            case "weekly-hot":
                return MissAvParsingHelper.BASE_URL + "/" + lang + "/weekly-hot";
            case "monthly-hot":
                return MissAvParsingHelper.BASE_URL + "/" + lang + "/monthly-hot";
            case "uncensored-leak":
                return MissAvParsingHelper.BASE_URL + "/" + lang + "/uncensored-leak";
            case "siro":
                return MissAvParsingHelper.BASE_URL + "/" + lang + "/siro";
            case "fc2":
                return MissAvParsingHelper.BASE_URL + "/" + lang + "/fc2";
            case "latest":
            default:
                return MissAvParsingHelper.BASE_URL + "/" + lang + "/new";
        }
    }

    @Override
    public boolean onAcceptUrl(final String url) {
        return url != null
                && (url.contains("missav.media")
                || url.contains("missav.ws")
                || url.contains("missav.ai")
                || url.contains("missav123.com"));
    }
}
