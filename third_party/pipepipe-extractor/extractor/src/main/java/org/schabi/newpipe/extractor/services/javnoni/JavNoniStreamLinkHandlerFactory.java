package org.schabi.newpipe.extractor.services.javnoni;

import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.LinkHandlerFactory;

public final class JavNoniStreamLinkHandlerFactory extends LinkHandlerFactory {
    private static final JavNoniStreamLinkHandlerFactory INSTANCE =
            new JavNoniStreamLinkHandlerFactory();

    public static JavNoniStreamLinkHandlerFactory getInstance() {
        return INSTANCE;
    }

    private JavNoniStreamLinkHandlerFactory() {
    }

    @Override
    public String getId(final String url) throws ParsingException {
        return JavNoniParsingHelper.extractId(url);
    }

    @Override
    public String getUrl(final String id) {
        return JavNoniParsingHelper.videoUrlFromId(id);
    }

    @Override
    public boolean onAcceptUrl(final String url) {
        if (url == null) return false;
        final String normalized = JavNoniParsingHelper.normalizeUrl(url);
        return (normalized.contains("javnoni.com/archives/") || normalized.contains("jav-noni.vip/archives/"))
                && !normalized.contains("/archives/category/")
                && !normalized.contains("/archives/tag/");
    }
}
