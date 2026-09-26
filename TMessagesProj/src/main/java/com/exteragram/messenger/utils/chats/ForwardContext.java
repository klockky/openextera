package com.exteragram.messenger.utils.chats;

import android.os.Bundle;

public interface ForwardContext {

    class ForwardParams {
        public boolean noQuote;
    }

    ForwardParams getForwardParams();

    boolean isForwardNoQuote();

    default void setForwardParams(boolean noQuote) {
        getForwardParams().noQuote = noQuote;
    }

    default void writeForwardParams(Bundle bundle) {
        if (bundle == null) {
            return;
        }
        bundle.putBoolean("forward_noquote", isForwardNoQuote());
    }

    default void readForwardParams(Bundle bundle) {
        if (bundle == null) {
            return;
        }
        setForwardParams(bundle.getBoolean("forward_noquote", false));
    }
}
