/*
 * This is the source code of Telegram for Android v. 7.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2020.
 */

package org.telegram.messenger;

import android.os.Build;

import com.android.billingclient.api.ProductDetails;
import com.exteragram.messenger.ExteraConfig;

import java.util.Objects;

public class BuildVars {

    public static boolean DEBUG_VERSION = BuildConfig.DEBUG_VERSION;
    public static boolean LOGS_ENABLED = ApplicationLoader.applicationContext != null && ExteraConfig.getLogging();
    public static boolean DEBUG_PRIVATE_VERSION = BuildConfig.DEBUG_PRIVATE_VERSION;
    public static boolean USE_CLOUD_STRINGS = true;
    public static boolean CHECK_UPDATES = false;
    public static boolean NO_SCOPED_STORAGE = Build.VERSION.SDK_INT <= 29;
    public static String BUILD_VERSION_STRING = BuildConfig.BUILD_VERSION_STRING;
    public static int BUILD_VERSION = BuildConfig.VERSION_CODE;
    public static boolean IS_LITE_VERSION = true;
    public static boolean PM_BUILD = false;

    public static int APP_ID = BuildConfig.APP_ID;
    public static String APP_HASH = BuildConfig.APP_HASH;

    // SafetyNet key for Google Identity SDK, set it to empty to disable
    public static String SAFETYNET_KEY = "";
    public static String PLAYSTORE_APP_URL = "https://play.google.com/store/apps/details?id=org.telegram.messenger";
    public static String HUAWEI_STORE_URL = "https://appgallery.huawei.com/app/C101184875";
    public static String GOOGLE_AUTH_CLIENT_ID = "760348033671-81kmi3pi84p11ub8hp9a1funsv0rn2p9.apps.googleusercontent.com";

    public static String HUAWEI_APP_ID = "101184875";

    public static String GITHUB_APP_URL = "https://github.com/exteraSquad/exteraGram/releases/latest";
    public static String RELEASES_URL = "https://t.me/exteraReleases";

    // You can use this flag to disable Google Play Billing (If you're making fork and want it to be in Google Play)
    public static boolean IS_BILLING_UNAVAILABLE = false;

    // works only on official app ids, disable on your forks
    public static boolean SUPPORTS_PASSKEYS = Build.VERSION.SDK_INT >= 34;

    public static int getExteraAppId() {
        return APP_ID;
    }

    public static String getExteraAppHash() {
        return APP_HASH;
    }

    public static boolean useInvoiceBilling() {
        return true;
    }

    private static boolean hasDirectCurrency() {
        if (!BillingController.getInstance().isReady() || BillingController.PREMIUM_PRODUCT_DETAILS == null) {
            return false;
        }
        for (ProductDetails.SubscriptionOfferDetails offerDetails : BillingController.PREMIUM_PRODUCT_DETAILS.getSubscriptionOfferDetails()) {
            for (ProductDetails.PricingPhase phase : offerDetails.getPricingPhases().getPricingPhaseList()) {
                for (String cur : MessagesController.getInstance(UserConfig.selectedAccount).directPaymentsCurrency) {
                    if (Objects.equals(phase.getPriceCurrencyCode(), cur)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static boolean isBetaApp() {
        return DEBUG_VERSION;
    }


    public static boolean isHuaweiStoreApp() {
        return ApplicationLoader.isHuaweiStoreBuild();
    }

    public static String getSmsHash() {
        return ApplicationLoader.isStandaloneBuild() ? "w0lkcmTZkKh" : (DEBUG_VERSION ? "O2P2z+/jBpJ" : "oLeq9AcOZkT");
    }
}
