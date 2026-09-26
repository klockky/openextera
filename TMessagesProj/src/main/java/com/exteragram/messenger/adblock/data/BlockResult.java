package com.exteragram.messenger.adblock.data;

// Instantiated from native code (libetgadblock.so), keep the constructor signature intact.
public class BlockResult {
    private final boolean matched;
    private final boolean important;
    private final String redirect;
    private final String rewrittenUrl;
    private final String exception;
    private final String filter;

    public BlockResult(boolean matched, boolean important, String redirect, String rewrittenUrl, String exception, String filter) {
        this.matched = matched;
        this.important = important;
        this.redirect = redirect;
        this.rewrittenUrl = rewrittenUrl;
        this.exception = exception;
        this.filter = filter;
    }

    public boolean isMatched() {
        return matched;
    }

    public boolean isImportant() {
        return important;
    }

    public String getRedirect() {
        return redirect;
    }

    public String getRewrittenUrl() {
        return rewrittenUrl;
    }

    public String getException() {
        return exception;
    }

    public String getFilter() {
        return filter;
    }
}
