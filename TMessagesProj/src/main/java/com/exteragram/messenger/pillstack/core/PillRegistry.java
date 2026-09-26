package com.exteragram.messenger.pillstack.core;

import android.content.Context;

import androidx.annotation.Keep;

import com.exteragram.messenger.pillstack.ui.pills.BasePill;
import com.exteragram.messenger.pillstack.ui.pills.crypto.BtcPill;
import com.exteragram.messenger.pillstack.ui.pills.crypto.GramPill;
import com.exteragram.messenger.pillstack.ui.pills.crypto.UsdPill;
import com.exteragram.messenger.pillstack.ui.pills.system.CachePill;
import com.exteragram.messenger.pillstack.ui.pills.system.ProxyPill;
import com.exteragram.messenger.pillstack.ui.pills.weather.WeatherPill;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.IconBackgroundColors;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public class PillRegistry {

    private static final Map<Integer, PillInfo> registry = new LinkedHashMap<>();
    private static boolean batchRegistration;

    public interface PillCreator {
        BasePill create(Context context, Theme.ResourcesProvider resourcesProvider);
    }

    public record PillInfo(int id, CharSequence name, int iconRes, int iconColorTop, int iconColorBottom, PillCreator creator) {

        public PillInfo(int id, CharSequence name, int iconRes, IconBackgroundColors colors, PillCreator creator) {
            this(id, name, iconRes, colors.top, colors.bottom, creator);
        }
    }

    static {
        beginTransaction();
        registerDefaultPills();
        endTransaction();
    }

    @Keep
    public static void beginTransaction() {
        batchRegistration = true;
    }

    @Keep
    public static void endTransaction() {
        batchRegistration = false;
        if (PillStackConfig.getConfigLoaded()) {
            PillStackConfig.sanitizePills();
            notifyLayoutChanged();
        }
    }

    private static void registerDefaultPills() {
        register(new PillInfo(PillType.WEATHER.getId(), LocaleController.getString(R.string.WeatherPill), R.drawable.weather_cloudy, IconBackgroundColors.BLUE_ALT, WeatherPill::new));
        register(new PillInfo(PillType.GRAM.getId(), "GRAM", R.drawable.settings_gram_24, IconBackgroundColors.BLUE_LIGHT, GramPill::new));
        register(new PillInfo(PillType.BTC.getId(), "BTC", R.drawable.pillstack_btc_settings, IconBackgroundColors.ORANGE_BRIGHT, BtcPill::new));
        register(new PillInfo(PillType.USD.getId(), "USD", R.drawable.pillstack_usd_settings, IconBackgroundColors.GREEN_DEEP, UsdPill::new));
        register(new PillInfo(PillType.CACHE.getId(), LocaleController.getString(R.string.StorageUsage), R.drawable.msg_filled_storageusage, IconBackgroundColors.BLUE_DEEP, CachePill::new));
        register(new PillInfo(PillType.PROXY.getId(), LocaleController.getString(R.string.Proxy), R.drawable.drawer_proxy_on, IconBackgroundColors.GREEN, ProxyPill::new));
    }

    private static void notifyLayoutChanged() {
        AndroidUtilities.runOnUIThread(() -> NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.pillStackLayoutChanged));
    }

    public static void register(PillInfo info) {
        registry.put(info.id(), info);
        if (batchRegistration) {
            return;
        }
        PillStackConfig.sanitizePills();
        notifyLayoutChanged();
    }

    @Keep
    public static void activatePill(int id) {
        if (!isRegistered(id) || PillStackConfig.getActivePills().contains(id)) {
            return;
        }
        PillStackConfig.getHiddenPills().remove(Integer.valueOf(id));
        PillStackConfig.getActivePills().add(id);
        PillStackConfig.savePillsLayout();
        notifyLayoutChanged();
    }

    public static PillInfo getPillInfo(int id) {
        return registry.get(id);
    }

    public static Collection<PillInfo> getRegisteredPills() {
        return registry.values();
    }

    public static boolean isRegistered(int id) {
        return registry.containsKey(id);
    }

    @Keep
    public static void unregister(int id) {
        if (registry.remove(id) == null || batchRegistration) {
            return;
        }
        PillStackConfig.sanitizePills();
        notifyLayoutChanged();
    }
}
