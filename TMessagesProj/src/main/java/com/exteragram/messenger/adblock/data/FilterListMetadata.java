package com.exteragram.messenger.adblock.data;

// Instantiated from native code (libetgadblock.so), keep the constructor signature intact.
public class FilterListMetadata {
    private final String homepage;
    private final String title;
    private final String redirect;
    private final Integer expires;

    public FilterListMetadata(String homepage, String title, String redirect, int expires) {
        this.homepage = homepage;
        this.title = title;
        this.redirect = redirect;
        this.expires = expires == 0 ? null : expires;
    }

    public String getHomepage() {
        return homepage;
    }

    public String getTitle() {
        return title;
    }

    public String getRedirect() {
        return redirect;
    }

    public Integer getExpires() {
        return expires;
    }
}
