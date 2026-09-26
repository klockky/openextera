package com.exteragram.messenger.pillstack.ui.pills.crypto.utils;

import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.pillstack.core.PillStackConfig;
import com.exteragram.messenger.pillstack.core.PillType;
import com.exteragram.messenger.utils.network.ExteraHttpClient;
import com.exteragram.messenger.utils.network.RemoteUtils;
import com.google.gson.annotations.SerializedName;

import org.telegram.messenger.BillingController;
import org.telegram.messenger.CacheFetcher;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.Utilities;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public abstract class ExchangeRates {

    private static final String RATES_URL = "https://api.coinbase.com/v2/exchange-rates?currency=USD";
    private static final String KEY_CACHE = "exchangeRatesCache";
    private static final String KEY_TIMESTAMP = "exchangeRatesTimestamp";

    public static final String[] MAIN_CURRENCIES = {"USD", "EUR", "RUB", "GBP", "KZT", "TRY", "UAH", "PLN", "AED", "CNY", "JPY", "BYN", "ILS", "CZK", "INR", "TON", "BTC", "ETH", "SOL"};
    public static final String[] CRYPTO_CURRENCIES = {"BTC", "ETH", "SOL", "TON", "USD", "EUR"};

    private static volatile State cacheValue;
    private static boolean remoteInFlight;
    private static final ArrayList<Utilities.Callback4<Boolean, State, Long, Boolean>> pendingRemote = new ArrayList<>();

    private static final CacheFetcher<Integer, State> fetcher;

    static {
        NotificationCenter.getGlobalInstance().addObserver((id, account, args) -> {
            if (id == NotificationCenter.pillStackSettingsChanged && PillStackConfig.shouldUpdatePill(args, PillType.GRAM.getId(), PillType.BTC.getId(), PillType.USD.getId())) {
                clearCache();
            }
        }, NotificationCenter.pillStackSettingsChanged);

        fetcher = new CacheFetcher<Integer, State>(5 * 60 * 1000) {
            @Override
            protected boolean saveLastTimeRequested() {
                return true;
            }

            @Override
            protected void getLocal(int currentAccount, Integer arguments, Utilities.Callback2<Long, State> onResult) {
                onResult.run(0L, getCached());
            }

            @Override
            protected void setLocal(int currentAccount, Integer arguments, State data, long requestedAt) {
                if (data == null) {
                    return;
                }
                cacheValue = data;
                try {
                    PillStackConfig.getEditor().putString(KEY_CACHE, ExteraConfig.getGSON().toJson(data)).apply();
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }

            @Override
            protected void getRemote(int currentAccount, Integer arguments, long hash, Utilities.Callback4<Boolean, State, Long, Boolean> onResult) {
                synchronized (pendingRemote) {
                    pendingRemote.add(onResult);
                    if (remoteInFlight) {
                        return;
                    }
                    remoteInFlight = true;
                }
                Request request = new Request.Builder().url(RATES_URL).build();
                ExteraHttpClient.INSTANCE.getClient().newCall(request).enqueue(new Callback() {
                    @Override
                    public void onFailure(@NonNull Call call, @NonNull IOException e) {
                        FileLog.e(e);
                        completeRemote(null);
                    }

                    @Override
                    public void onResponse(@NonNull Call call, @NonNull Response response) {
                        if (!response.isSuccessful()) {
                            response.close();
                            onFailure(call, new IOException("Unexpected code " + response));
                            return;
                        }
                        try (ResponseBody body = response.body()) {
                            completeRemote(parseState(ExteraConfig.getGSON().fromJson(body.charStream(), CoinbaseResponse.class)));
                        } catch (Exception e) {
                            FileLog.e(e);
                            completeRemote(null);
                        }
                    }
                });
            }

            @Override
            protected long getSavedLastTimeRequested(int hashCode) {
                return PillStackConfig.getPreferences().getLong(KEY_TIMESTAMP, 0);
            }

            @Override
            protected void setSavedLastTimeRequested(int hashCode, long time) {
                PillStackConfig.getEditor().putLong(KEY_TIMESTAMP, time).apply();
            }
        };
    }

    public static class CoinbaseResponse {
        @SerializedName("data")
        Data data;

        private CoinbaseResponse() {
        }
    }

    public static class Data {
        @SerializedName("currency")
        String currency;

        @SerializedName("rates")
        Map<String, String> rates;

        private Data() {
        }
    }

    public record State(Map<String, BigDecimal> usdRates) {

        public static final DecimalFormat formatter = new DecimalFormat("#.##");

        static {
            formatter.setDecimalFormatSymbols(DecimalFormatSymbols.getInstance(Locale.ENGLISH));
        }

        public BigDecimal getUsdRate(String currency) {
            if (currency == null) {
                return null;
            }
            return usdRates.get(normalize(currency));
        }

        public BigDecimal getRate(String from, String to) {
            BigDecimal fromRate = getUsdRate(from);
            BigDecimal toRate = getUsdRate(to);
            if (fromRate == null || toRate == null || toRate.signum() == 0) {
                return null;
            }
            return fromRate.divide(toRate, 12, RoundingMode.HALF_UP);
        }

        public double formatDonate(String currency, double fallbackRate) {
            BigDecimal rate = getRate("USD", currency);
            if (rate != null) {
                fallbackRate = rate.doubleValue();
            }
            double amount = fallbackRate * RemoteUtils.getFloatConfigValue("donates_amount_usd", 5.0f);
            if ("ton".equalsIgnoreCase(currency)) {
                amount += RemoteUtils.getIntConfigValue("donates_ton_markup_percent", 10) / 100.0d * amount;
            }
            return Double.parseDouble(formatter.format(amount));
        }
    }

    private static void completeRemote(State state) {
        ArrayList<Utilities.Callback4<Boolean, State, Long, Boolean>> callbacks;
        synchronized (pendingRemote) {
            remoteInFlight = false;
            callbacks = new ArrayList<>(pendingRemote);
            pendingRemote.clear();
        }
        for (Utilities.Callback4<Boolean, State, Long, Boolean> callback : callbacks) {
            if (state == null) {
                callback.run(true, null, 0L, false);
            } else {
                callback.run(false, state, 0L, true);
            }
        }
    }

    public static State getCached() {
        if (cacheValue == null) {
            try {
                String json = PillStackConfig.getPreferences().getString(KEY_CACHE, null);
                if (json != null) {
                    cacheValue = ExteraConfig.getGSON().fromJson(json, State.class);
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        return cacheValue;
    }

    public static void clearCache() {
        PillStackConfig.getEditor().putLong(KEY_TIMESTAMP, 0).apply();
        fetcher.forceRequest(0, 0);
    }

    public static boolean isSupportedCurrency(String currency) {
        if (currency == null) {
            return false;
        }
        String normalized = normalize(currency);
        for (String supported : MAIN_CURRENCIES) {
            if (supported.equals(normalized)) {
                return true;
            }
        }
        return false;
    }

    public static String resolveTargetCurrency(int account, String selection) {
        String normalized = normalize(selection);
        if (!"AUTO".equals(normalized)) {
            return TextUtils.isEmpty(normalized) || !isSupportedCurrency(normalized) ? "USD" : normalized;
        }
        String targetCurrency = BillingController.getInstance().getTargetCurrency(account, false);
        if (targetCurrency == null) {
            return "USD";
        }
        String normalizedTarget = normalize(targetCurrency);
        return isSupportedCurrency(normalizedTarget) ? normalizedTarget : "USD";
    }

    public static void fetch(Utilities.Callback<State> callback) {
        if (callback == null) {
            return;
        }
        fetcher.fetch(0, 0, callback);
    }

    private static State parseState(CoinbaseResponse response) {
        if (response == null || response.data == null || response.data.rates == null) {
            return null;
        }
        HashMap<String, BigDecimal> usdRates = new HashMap<>();
        for (String currency : MAIN_CURRENCIES) {
            BigDecimal rate = parseUsdRate(currency, response.data.rates);
            if (rate != null) {
                usdRates.put(currency, rate);
            }
        }
        if (usdRates.isEmpty()) {
            return null;
        }
        return new State(usdRates);
    }

    private static BigDecimal parseUsdRate(String currency, Map<String, String> rates) {
        if ("USD".equals(currency)) {
            return BigDecimal.ONE;
        }
        String value = rates.get(currency);
        if (value == null) {
            return null;
        }
        try {
            BigDecimal rate = new BigDecimal(value);
            if (rate.signum() == 0) {
                return null;
            }
            return BigDecimal.ONE.divide(rate, 16, RoundingMode.HALF_UP);
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }
}
