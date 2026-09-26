package com.exteragram.messenger.components;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.net.Uri;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiNetworkSuggestion;
import android.os.Build;
import android.provider.Settings;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.Base64;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;

import com.exteragram.messenger.utils.system.SystemUtils;
import com.exteragram.messenger.utils.text.LocaleUtils;
import com.google.zxing.EncodeHintType;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LinkifyPort;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.TelegramQRCodeWriter;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.browser.Browser;
import org.telegram.messenger.utils.ViewOutlineProviderImpl;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.DialogCell;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ColoredImageSpan;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Components.StickerImageView;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.HashMap;

public class QRCodeSheet extends BottomSheet {

    private static final int TEXT_TYPE_LINK = 0;
    private static final int TEXT_TYPE_TEXT = 1;
    private static final int TEXT_TYPE_AUTH_TOKEN = 2;
    private static final int TEXT_TYPE_PHONE = 3;
    private static final int TEXT_TYPE_WIFI = 4;

    private final BaseFragment fragment;

    private String ssid;
    private String password;
    private String wifiAuthType = "WPA";

    public QRCodeSheet(BaseFragment fragment, String text) {
        super(fragment.getParentActivity(), false, fragment.getResourceProvider());
        this.fragment = fragment;

        final int type;
        CharSequence buttonText;
        CharSequence secondButtonText;
        if (text.startsWith("tg://login?token=")) {
            type = TEXT_TYPE_AUTH_TOKEN;
            buttonText = LocaleController.getString(R.string.Cancel);
            secondButtonText = LocaleController.getString(R.string.Allow);
        } else if (LinkifyPort.WEB_URL.matcher(text).matches() || text.startsWith("tel:")) {
            type = text.startsWith("tel:") ? TEXT_TYPE_PHONE : TEXT_TYPE_LINK;
            SpannableStringBuilder openText = new SpannableStringBuilder(LocaleController.getString(R.string.Open)).append(".");
            int length = openText.length();
            openText.setSpan(new ColoredImageSpan(ContextCompat.getDrawable(fragment.getParentActivity(), R.drawable.msg_mini_topicarrow)), length - 1, length, 0);
            buttonText = openText;
            secondButtonText = getTextWithIcon("share");
        } else if (text.startsWith("WIFI:")) {
            type = TEXT_TYPE_WIFI;
            parseWifiInfo(text);
            buttonText = LocaleController.getString(R.string.WifiConnect);
            secondButtonText = getTextWithIcon("share");
        } else {
            type = TEXT_TYPE_TEXT;
            buttonText = getTextWithIcon("copy");
            secondButtonText = getTextWithIcon("share");
        }

        Activity context = fragment.getParentActivity();
        fixNavigationBar();

        FrameLayout frameLayout = new FrameLayout(context);
        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        frameLayout.addView(linearLayout);

        linearLayout.addView(new View(fragment.getParentActivity()) {
            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                int width = getMeasuredWidth();
                RectF rect = new RectF();
                rect.set((getMeasuredWidth() - width) / 2f, 0, (getMeasuredWidth() + width) / 2f, AndroidUtilities.dp(4));
                Theme.dialogs_onlineCirclePaint.setColor(getThemedColor(Theme.key_sheet_scrollUp));
                canvas.drawRoundRect(rect, AndroidUtilities.dp(2), AndroidUtilities.dp(2), Theme.dialogs_onlineCirclePaint);
            }
        }, LayoutHelper.createLinear(36, 4, Gravity.CENTER_HORIZONTAL, 18, 2, 18, 0));

        if (type == TEXT_TYPE_AUTH_TOKEN) {
            StickerImageView stickerImageView = new StickerImageView(context, currentAccount);
            stickerImageView.setStickerPackName(AndroidUtilities.STICKERS_PLACEHOLDER_PACK_NAME);
            stickerImageView.setStickerNum(6);
            stickerImageView.getImageReceiver().setAutoRepeat(1);
            stickerImageView.getImageReceiver().setAutoRepeatCount(1);
            linearLayout.addView(stickerImageView, LayoutHelper.createLinear(144, 144, Gravity.CENTER_HORIZONTAL, 0, 20, 0, 10));
        } else {
            ImageView imageView = new ImageView(context);
            ScaleStateListAnimator.apply(imageView, 0.03f, 1.2f);
            imageView.setScaleType(ImageView.ScaleType.FIT_XY);
            imageView.setOutlineProvider(ViewOutlineProviderImpl.boundsWithRoundRect(AndroidUtilities.dp(12)));
            imageView.setClipToOutline(true);
            Bitmap qr = createQR(text);
            imageView.setImageBitmap(qr);
            imageView.setOnClickListener(v -> {
                if (qr != null) {
                    copyQR(qr, context);
                }
            });
            linearLayout.addView(imageView, LayoutHelper.createLinear(200, 200, Gravity.CENTER_HORIZONTAL, 18, 20, 18, 10));
        }

        TextView textView = new TextView(context);
        ScaleStateListAnimator.apply(textView, 0.02f, 1.5f);
        textView.setGravity(Gravity.CENTER_HORIZONTAL);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        textView.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(4), AndroidUtilities.dp(8), AndroidUtilities.dp(4));
        textView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        if (type == TEXT_TYPE_WIFI && !TextUtils.isEmpty(ssid)) {
            textView.setText(MessageFormat.format("SSID: {0}{1}", ssid, TextUtils.isEmpty(password) ? "" : ", Password: " + password));
        } else {
            textView.setText(type == TEXT_TYPE_AUTH_TOKEN ? LocaleController.getString(R.string.AreYouSureToLogin) : text);
        }
        String link = type == TEXT_TYPE_LINK ? LocaleUtils.ensureUrlHasHttps(text) : text;
        if (type != TEXT_TYPE_AUTH_TOKEN) {
            textView.setBackground(Theme.createSelectorDrawable(Theme.multAlpha(getThemedColor(Theme.key_windowBackgroundWhiteGrayText), Theme.isCurrentThemeDark() ? 0.2f : 0.15f), Theme.RIPPLE_MASK_ROUNDRECT_6DP, AndroidUtilities.dp(8)));
            textView.setOnClickListener(v -> {
                if (AndroidUtilities.addToClipboard(link)) {
                    showCopyBulletin(true);
                }
            });
        }
        linearLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 21, 2, 21, 8));

        LinearLayout buttonsLayout = new LinearLayout(context);
        buttonsLayout.setOrientation(LinearLayout.HORIZONTAL);
        linearLayout.addView(buttonsLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 16, 15, 16, 4));

        ButtonWithCounterView button = new ButtonWithCounterView(context, fragment.getResourceProvider());
        button.setRound();
        button.setText(buttonText, false);
        button.setOnClickListener(v -> {
            switch (type) {
                case TEXT_TYPE_AUTH_TOKEN:
                    break;
                case TEXT_TYPE_LINK:
                case TEXT_TYPE_PHONE:
                    Browser.openUrl(fragment.getParentActivity(), Uri.parse(link));
                    break;
                case TEXT_TYPE_TEXT:
                    if (AndroidUtilities.addToClipboard(link)) {
                        showCopyBulletin(false);
                    }
                    break;
                case TEXT_TYPE_WIFI:
                    AndroidUtilities.runOnUIThread(this::connectToWifi, 750);
                    break;
            }
            dismiss();
        });
        if (type == TEXT_TYPE_AUTH_TOKEN) {
            button.setNeutral();
        }
        buttonsLayout.addView(button, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1f));
        buttonsLayout.addView(new View(context), LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 0.06f));

        ButtonWithCounterView secondButton = new ButtonWithCounterView(context, fragment.getResourceProvider());
        secondButton.setRound();
        secondButton.setText(secondButtonText, false);
        secondButton.setFilled(true);
        secondButton.setOnClickListener(v -> {
            if (type == TEXT_TYPE_AUTH_TOKEN) {
                AndroidUtilities.runOnUIThread(() -> acceptLoginToken(link, fragment), 750);
            } else {
                try {
                    Intent intent = new Intent(Intent.ACTION_SEND);
                    intent.setType("text/plain");
                    intent.putExtra(Intent.EXTRA_TEXT, link);
                    fragment.startActivityForResult(Intent.createChooser(intent, LocaleController.getString(R.string.QrCode)), 500);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
            dismiss();
        });
        buttonsLayout.addView(secondButton, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1f));

        ScrollView scrollView = new ScrollView(context);
        scrollView.addView(frameLayout);
        setCustomView(scrollView);
    }

    private void acceptLoginToken(String link, BaseFragment fragment) {
        try {
            byte[] token = Base64.decode(link.substring("tg://login?token=".length()).replaceAll("/", "_").replaceAll("\\+", "-"), Base64.URL_SAFE);
            TLRPC.TL_auth_acceptLoginToken req = new TLRPC.TL_auth_acceptLoginToken();
            req.token = token;
            ConnectionsManager.getInstance(UserConfig.selectedAccount).sendRequest(req, (response, error) -> dismiss());
        } catch (Exception e) {
            FileLog.e("Failed to pass qr code auth", e);
            AndroidUtilities.runOnUIThread(() -> AlertsCreator.showSimpleAlert(fragment, LocaleController.getString(R.string.AuthAnotherClient), LocaleController.getString(R.string.ErrorOccurred)));
        }
    }

    private Spanned getTextWithIcon(String action) {
        boolean copy = action.equals("copy");
        SpannableStringBuilder builder = new SpannableStringBuilder();
        builder.append("..").setSpan(new ColoredImageSpan(ContextCompat.getDrawable(fragment.getParentActivity(), copy ? R.drawable.msg_copy_filled : R.drawable.msg_share_filled)), 0, 1, 0);
        builder.setSpan(new DialogCell.FixedWidthSpan(AndroidUtilities.dp(4)), 1, 2, 0);
        builder.append(LocaleController.getString(copy ? R.string.LinkActionCopy : R.string.LinkActionShare));
        return builder;
    }

    private Bitmap createQR(String text) {
        try {
            HashMap<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.MARGIN, 0);
            return new TelegramQRCodeWriter().encode(text, 768, 768, hints, null, 1.0f, 0xFFFFFFFF, 0xFF000000, false);
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    @SuppressLint("SetWorldReadable")
    private void copyQR(Bitmap bitmap, Activity activity) {
        try {
            File file = new File(activity.getExternalFilesDir(null), "qr_code.jpg");
            if (file.exists()) {
                file.delete();
            }
            file.createNewFile();
            FileOutputStream stream = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
            stream.flush();
            stream.close();
            file.setReadable(true, false);
            SystemUtils.addFileToClipboard(file, () -> BulletinFactory.of(getContainer(), null).createCopyBulletin(LocaleController.getString(R.string.PhotoCopied), resourcesProvider).show());
        } catch (IOException e) {
            FileLog.e(e);
        }
    }

    private void parseWifiInfo(String text) {
        for (String part : text.substring(text.indexOf(":") + 1).split("(?<!\\\\);")) {
            if (part.startsWith("S:")) {
                ssid = unescapeWifiString(part.substring(2));
            } else if (part.startsWith("P:")) {
                password = unescapeWifiString(part.substring(2));
            } else if (part.startsWith("T:")) {
                wifiAuthType = part.substring(2);
            }
        }
    }

    private String unescapeWifiString(String value) {
        return value.replace("\\\\", "\\")
                .replace("\\;", ";")
                .replace("\\:", ":")
                .replace("\\,", ",")
                .replace("\\\"", "\"");
    }

    private void connectToWifi() {
        if (TextUtils.isEmpty(ssid)) {
            showErrorBulletin(LocaleController.getString(R.string.WifiFailed));
            return;
        }
        WifiManager wifiManager = (WifiManager) ApplicationLoader.applicationContext.getSystemService(Context.WIFI_SERVICE);
        if (wifiManager == null) {
            showErrorBulletin(LocaleController.getString(R.string.WifiFailed));
            return;
        }
        if (!wifiManager.isWifiEnabled()) {
            showErrorBulletin(LocaleController.getString(R.string.WifiDisabled));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                fragment.startActivityForResult(new Intent(Settings.Panel.ACTION_WIFI), 501);
            }
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            connectWifiModern(wifiManager);
        } else {
            connectWifiLegacy(wifiManager);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private void connectWifiModern(WifiManager wifiManager) {
        WifiNetworkSuggestion.Builder builder = new WifiNetworkSuggestion.Builder()
                .setSsid(ssid)
                .setIsAppInteractionRequired(true);
        if (!TextUtils.isEmpty(password)) {
            if ("WPA".equalsIgnoreCase(wifiAuthType)) {
                builder.setWpa2Passphrase(password);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && "SAE".equalsIgnoreCase(wifiAuthType)) {
                builder.setWpa3Passphrase(password);
            }
        }
        if (wifiManager.addNetworkSuggestions(Collections.singletonList(builder.build())) == WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS) {
            fragment.getParentActivity().startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
        } else {
            showErrorBulletin(LocaleController.getString(R.string.WifiFailed));
        }
    }

    @SuppressWarnings("deprecation")
    private void connectWifiLegacy(WifiManager wifiManager) {
        WifiConfiguration config = new WifiConfiguration();
        config.SSID = String.format("\"%s\"", ssid);
        boolean noPassword = TextUtils.isEmpty(wifiAuthType) || "nopass".equalsIgnoreCase(wifiAuthType);
        if (!TextUtils.isEmpty(password) && !noPassword) {
            if ("WPA".equalsIgnoreCase(wifiAuthType)) {
                config.preSharedKey = String.format("\"%s\"", password);
            } else if ("WEP".equalsIgnoreCase(wifiAuthType)) {
                config.wepKeys[0] = String.format("\"%s\"", password);
                config.wepTxKeyIndex = 0;
                config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
                config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40);
            }
        } else {
            config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
        }
        int networkId = wifiManager.addNetwork(config);
        if (networkId != -1 && wifiManager.enableNetwork(networkId, true)) {
            wifiManager.reconnect();
            BulletinFactory.of(fragment).createSimpleBulletin(R.raw.contact_check, LocaleController.getString(R.string.WifiSuccess)).show();
        } else {
            showErrorBulletin(LocaleController.getString(R.string.WifiFailed));
        }
    }

    private void showErrorBulletin(String message) {
        AndroidUtilities.runOnUIThread(() -> BulletinFactory.of(fragment).createErrorBulletin(message).show());
    }

    private void showCopyBulletin(boolean inSheet) {
        AndroidUtilities.runOnUIThread(() -> (inSheet ? BulletinFactory.of(getContainer(), null) : BulletinFactory.of(fragment))
                .createCopyBulletin(LocaleController.formatString("TextCopied", R.string.TextCopied))
                .show());
    }
}
