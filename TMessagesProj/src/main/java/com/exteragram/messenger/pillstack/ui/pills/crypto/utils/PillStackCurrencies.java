package com.exteragram.messenger.pillstack.ui.pills.crypto.utils;

import org.telegram.messenger.BillingController;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.Currency;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;

public abstract class PillStackCurrencies {

    private static final HashSet<String> AMBIGUOUS_SYMBOLS = new HashSet<>(Arrays.asList("$", "kr", "Fr", "₩"));
    private static final Map<String, CurrencyInfo> CURRENCIES = new HashMap<>();

    public static final String[] TARGET_CURRENCIES = {
        "AUTO", "AED", "BYN", "CNY", "CZK", "EUR", "GBP", "ILS", "INR", "JPY", "KZT", "PLN", "RUB", "TRY", "UAH", "USD"
    };

    public record CurrencyInfo(String code, int nameResId, String symbolOverride, boolean suffixSymbol) {
    }

    static {
        addCurrency("USD", R.string.CryptoCurrencyUsd, "$", false);
        addCurrency("EUR", R.string.CryptoCurrencyEur, null, false);
        addCurrency("RUB", R.string.CryptoCurrencyRub, "₽", true);
        addCurrency("GBP", R.string.CryptoCurrencyGbp, null, false);
        addCurrency("KZT", R.string.CryptoCurrencyKzt, "₸", true);
        addCurrency("TRY", R.string.CryptoCurrencyTry, "₺", true);
        addCurrency("UAH", R.string.CryptoCurrencyUah, "₴", true);
        addCurrency("PLN", R.string.CryptoCurrencyPln, "zł", true);
        addCurrency("AED", R.string.CryptoCurrencyAed, null, false);
        addCurrency("CNY", R.string.CryptoCurrencyCny, "CN¥", false);
        addCurrency("JPY", R.string.CryptoCurrencyJpy, null, false);
        addCurrency("BYN", R.string.CryptoCurrencyByn, "Br", true);
        addCurrency("ILS", R.string.CryptoCurrencyIls, "₪", false);
        addCurrency("CZK", R.string.CryptoCurrencyCzk, "Kč", true);
        addCurrency("INR", R.string.CryptoCurrencyInr, "₹", false);
    }

    private static void addCurrency(String code, int nameResId, String symbolOverride, boolean suffixSymbol) {
        String normalized = normalize(code);
        if (normalized.isEmpty()) {
            return;
        }
        CURRENCIES.put(normalized, new CurrencyInfo(normalized, nameResId, symbolOverride, suffixSymbol));
    }

    public static CharSequence getTargetCurrencyLabel(String currency) {
        if (currency == null || "AUTO".equalsIgnoreCase(currency)) {
            return LocaleController.getString(R.string.QualityAuto);
        }
        return getCurrencyLabelWithCode(currency);
    }

    public static CharSequence getTargetCurrencySubtext(String currency) {
        if (currency == null || "AUTO".equalsIgnoreCase(currency)) {
            return LocaleController.getString(R.string.QualityAuto);
        }
        return getCurrencyName(currency);
    }

    public static String getCurrencyName(String currency) {
        String normalized = normalize(currency);
        CurrencyInfo info = CURRENCIES.get(normalized);
        return info == null ? normalized : LocaleController.getString(info.nameResId());
    }

    public static String getCurrencyLabelWithCode(String currency) {
        String normalized = normalize(currency);
        CurrencyInfo info = CURRENCIES.get(normalized);
        if (info == null) {
            return normalized;
        }
        return LocaleController.getString(info.nameResId()) + " — " + normalized;
    }

    public static String[] getTargetCurrencies(String excludedCurrency) {
        if (excludedCurrency == null || excludedCurrency.isEmpty()) {
            return TARGET_CURRENCIES;
        }
        int count = 0;
        for (String currency : TARGET_CURRENCIES) {
            if (!excludedCurrency.equalsIgnoreCase(currency)) {
                count++;
            }
        }
        String[] result = new String[count];
        int index = 0;
        for (String currency : TARGET_CURRENCIES) {
            if (!excludedCurrency.equalsIgnoreCase(currency)) {
                result[index++] = currency;
            }
        }
        return result;
    }

    public static String formatFiatPrice(BigDecimal price, String currency) {
        if (price == null || currency == null || currency.isEmpty()) {
            return null;
        }
        try {
            int fractionDigits = Math.max(0, BillingController.getInstance().getCurrencyExp(currency));
            BigDecimal scaled = price.setScale(fractionDigits, RoundingMode.HALF_UP);
            NumberFormat numberFormat = NumberFormat.getNumberInstance(Locale.US);
            numberFormat.setGroupingUsed(true);
            numberFormat.setMinimumFractionDigits(fractionDigits);
            numberFormat.setMaximumFractionDigits(fractionDigits);
            String amount = numberFormat.format(scaled);

            String normalized = normalize(currency);
            CurrencyInfo info = CURRENCIES.get(normalized);
            String symbol = info != null ? info.symbolOverride() : null;
            boolean hasOverride = symbol != null;
            if (!hasOverride) {
                try {
                    symbol = Currency.getInstance(normalized).getSymbol(Locale.US);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
            if (symbol == null || symbol.isEmpty() || symbol.equalsIgnoreCase(normalized)) {
                return amount + " " + currency;
            }
            if (!hasOverride && AMBIGUOUS_SYMBOLS.contains(symbol)) {
                return amount + " " + currency;
            }
            if (info != null && info.suffixSymbol()) {
                return amount + " " + symbol;
            }
            return symbol + amount;
        } catch (Exception ignore) {
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
