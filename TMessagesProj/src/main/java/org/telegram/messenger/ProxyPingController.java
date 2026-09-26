package org.telegram.messenger;

import android.os.SystemClock;

import org.telegram.tgnet.ConnectionsManager;

public class ProxyPingController {

    private static final long PING_INTERVAL = 10000;

    private static final ProxyPingController INSTANCE = new ProxyPingController();

    private final Runnable pingRunnable = this::checkPing;

    public static void init() {
        INSTANCE.scheduleNextPing();
    }

    private void checkPing() {
        SharedConfig.ProxyInfo proxyInfo = SharedConfig.currentProxy;
        if (SharedConfig.isProxyEnabled() && proxyInfo != null) {
            int ping = ConnectionsManager.native_getCurrentPingTime(UserConfig.selectedAccount);
            if (ping > 0) {
                proxyInfo.ping = ping;
                proxyInfo.availableCheckTime = SystemClock.elapsedRealtime();
                proxyInfo.available = true;
            } else {
                proxyInfo.ping = 0;
                proxyInfo.available = false;
            }
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxyPingUpdated, ping);
        }
        scheduleNextPing();
    }

    private void scheduleNextPing() {
        AndroidUtilities.cancelRunOnUIThread(pingRunnable);
        AndroidUtilities.runOnUIThread(pingRunnable, PING_INTERVAL);
    }
}
