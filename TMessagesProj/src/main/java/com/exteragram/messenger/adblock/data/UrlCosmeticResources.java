package com.exteragram.messenger.adblock.data;

// Instantiated from native code (libetgadblock.so), keep the constructor signature intact.
public class UrlCosmeticResources {
    private final String[] hideSelectors;
    private final String[] proceduralActions;
    private final String[] exceptions;
    private final String injectedScript;
    private final boolean genericHide;

    public UrlCosmeticResources(String[] hideSelectors, String[] proceduralActions, String[] exceptions, String injectedScript, boolean genericHide) {
        this.hideSelectors = hideSelectors;
        this.proceduralActions = proceduralActions;
        this.exceptions = exceptions;
        this.injectedScript = injectedScript;
        this.genericHide = genericHide;
    }

    public String[] getHideSelectors() {
        return hideSelectors;
    }

    public String[] getProceduralActions() {
        return proceduralActions;
    }

    public String[] getExceptions() {
        return exceptions;
    }

    public String getInjectedScript() {
        return injectedScript;
    }

    public boolean isGenericHide() {
        return genericHide;
    }
}
