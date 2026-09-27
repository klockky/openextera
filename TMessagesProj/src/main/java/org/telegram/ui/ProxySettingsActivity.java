/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import android.animation.ValueAnimator;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.net.Uri;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.PasswordTransformationMethod;
import android.transition.ChangeBounds;
import android.transition.Fade;
import android.transition.Transition;
import android.transition.TransitionManager;
import android.transition.TransitionSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import androidx.core.graphics.ColorUtils;

import com.exteragram.messenger.proxy.ProxyController;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.SvgHelper;
import org.telegram.messenger.Utilities;
import org.telegram.utils.proxy.WebProxyTransport;
import org.telegram.utils.proxy.ProxySettings;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.RadioCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.OutlineTextContainerView;
import org.telegram.ui.Components.QRCodeBottomSheet;
import org.telegram.ui.Components.SectionsScrollView;

import java.util.ArrayList;

public class ProxySettingsActivity extends BaseFragment {

    private final static int FIELD_NAME = 0;
    private final static int FIELD_IP = 1;
    private final static int FIELD_PORT = 2;
    private final static int FIELD_USER = 3;
    private final static int FIELD_PASSWORD = 4;
    private final static int FIELD_SECRET = 5;
    private final static int FIELDS_COUNT = 6;

    private final static int TYPE_SOCKS5 = 0;
    private final static int TYPE_MTPROTO = 1;
    private final static int TYPE_WEB = 2;

    private final static int WEB_PROXY_PORT = 443;
    private final static int FIELDS_CONTAINER_TAG = -33024;

    private EditTextBoldCursor[] inputFields;
    private OutlineTextContainerView[] inputFieldContainers;
    private ScrollView scrollView;
    private LinearLayout linearLayout2;
    private FrameLayout inputFieldsSection;
    private LinearLayout inputFieldsContainer;
    private HeaderCell headerCell;
    private ShadowSectionCell[] sectionCell = new ShadowSectionCell[4];
    private TextInfoPrivacyCell sponsorInfoCell;
    private TextSettingsCell pasteCell;
    private TextSettingsCell shareCell;
    private ActionBarMenuItem doneItem;
    private RadioCell[] typeCell = new RadioCell[3];
    private int currentType = -1;

    private int pasteType = -1;
    private String pasteString;
    private String[] pasteFields;

    private boolean addingNewProxy;

    private SharedConfig.ProxyInfo currentProxyInfo;

    private boolean ignoreOnTextChange;

    private ClipboardManager clipboardManager;

    private float shareDoneProgress = 1f;
    private float[] shareDoneProgressAnimValues = new float[2];
    private boolean shareDoneEnabled = true;
    private ValueAnimator shareDoneAnimator;

    private static final int done_button = 1;

    private final ClipboardManager.OnPrimaryClipChangedListener clipChangedListener = this::updatePasteCell;

    public ProxySettingsActivity() {
        super();
        currentProxyInfo = new SharedConfig.ProxyInfo(ProxySettings.EMPTY);
        addingNewProxy = true;
    }

    public ProxySettingsActivity(SharedConfig.ProxyInfo proxyInfo) {
        super();
        currentProxyInfo = proxyInfo;
    }

    @Override
    public void onResume() {
        super.onResume();
        AndroidUtilities.requestAdjustResize(getParentActivity(), classGuid);
        clipboardManager.addPrimaryClipChangedListener(clipChangedListener);
        updatePasteCell();
    }

    @Override
    public void onPause() {
        super.onPause();
        clipboardManager.removePrimaryClipChangedListener(clipChangedListener);
    }

    @Override
    public View createView(Context context) {
        actionBar.setTitle(LocaleController.getString(R.string.ProxyDetails));
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(false);
        if (parentLayout != null && parentLayout.isLayersLayout()) {
            actionBar.setOccupyStatusBar(false);
        }

        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == done_button) {
                    if (getParentActivity() == null) {
                        return;
                    }
                    final String name = inputFields[FIELD_NAME].getText().toString().trim();
                    final String oldLink = addingNewProxy ? null : currentProxyInfo.settings.getLink();
                    currentProxyInfo.settings = ProxySettings.builder()
                            .setType(ProxySettings.intToType(currentType))
                            .setAddress(inputFields[FIELD_IP].getText().toString())
                            .setPort(currentType == TYPE_WEB ? 0 : Utilities.parseInt(inputFields[FIELD_PORT].getText().toString()))
                            .setUser(currentType == TYPE_SOCKS5 ? inputFields[FIELD_USER].getText().toString() : "")
                            .setPassword(currentType == TYPE_SOCKS5 ? inputFields[FIELD_PASSWORD].getText().toString() : "")
                            .setSecret(currentType != TYPE_SOCKS5 ? inputFields[FIELD_SECRET].getText().toString() : "")
                            .build();

                    final ProxyController proxyController = ProxyController.getInstance();
                    final SharedPreferences preferences = MessagesController.getGlobalMainSettings();
                    final boolean enabled = addingNewProxy || preferences.getBoolean("proxy_enabled", false);
                    currentProxyInfo = proxyController.saveProxy(currentProxyInfo, oldLink, name);
                    if (addingNewProxy) {
                        proxyController.setCurrentProxy(currentProxyInfo);
                    }
                    if (addingNewProxy || proxyController.getCurrentProxy() == currentProxyInfo) {
                        SharedPreferences.Editor editor = preferences.edit();
                        editor.putBoolean("proxy_enabled", enabled);
                        currentProxyInfo.settings.toSharedPreferences(editor);
                        ConnectionsManager.setProxySettings(enabled, currentProxyInfo.settings);
                        editor.apply();
                    }

                    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxySettingsChanged);

                    finishFragment();
                }
            }
        });

        doneItem = actionBar.createMenu().addItemWithWidth(done_button, R.drawable.ic_ab_done, AndroidUtilities.dp(56));
        doneItem.setContentDescription(LocaleController.getString(R.string.Done));

        fragmentView = new FrameLayout(context);
        FrameLayout frameLayout = (FrameLayout) fragmentView;
        fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        linearLayout2 = new SectionsScrollView.SectionsLinearLayout(context);
        final SectionsScrollView sectionsScrollView = new SectionsScrollView(context, linearLayout2, resourceProvider);
        scrollView = sectionsScrollView;
        actionBar.setAdaptiveBackground(sectionsScrollView);
        scrollView.setFillViewport(true);
        AndroidUtilities.setScrollViewEdgeEffectColor(scrollView, Theme.getColor(Theme.key_actionBarDefault));
        frameLayout.addView(scrollView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        linearLayout2.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(linearLayout2, new ScrollView.LayoutParams(ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

        inputFields = new EditTextBoldCursor[FIELDS_COUNT];

        final View.OnClickListener typeCellClickListener = view -> setProxyType((Integer) view.getTag(), true);
        for (int a = 0; a < typeCell.length; a++) {
            typeCell[a] = new RadioCell(context);
            typeCell[a].setBackground(Theme.getSelectorDrawable(true));
            typeCell[a].setTag(a);
            final int textRes;
            if (a == TYPE_SOCKS5) {
                textRes = R.string.UseProxySocks5;
            } else if (a == TYPE_MTPROTO) {
                textRes = R.string.UseProxyTelegram;
            } else {
                textRes = R.string.UseProxyWeb;
            }
            typeCell[a].setText(LocaleController.getString(textRes), a == currentType, a != typeCell.length - 1);
            linearLayout2.addView(typeCell[a], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 50));
            typeCell[a].setOnClickListener(typeCellClickListener);
        }

        sectionCell[0] = new ShadowSectionCell(context);
        linearLayout2.addView(sectionCell[0], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 14));

        inputFieldsSection = new FrameLayout(context);
        inputFieldsSection.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(8), AndroidUtilities.dp(12), 0);
        linearLayout2.addView(inputFieldsSection, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        inputFieldsContainer = new LinearLayout(context);
        inputFieldsContainer.setOrientation(LinearLayout.VERTICAL);
        inputFieldsContainer.setTag(FIELDS_CONTAINER_TAG);
        inputFieldsSection.addView(inputFieldsContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        sectionCell[3] = new ShadowSectionCell(context);
        linearLayout2.addView(sectionCell[3], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 14));

        inputFieldContainers = new OutlineTextContainerView[FIELDS_COUNT];
        for (int a = 0; a < FIELDS_COUNT; a++) {
            OutlineTextContainerView container = new OutlineTextContainerView(context);
            inputFieldContainers[a] = container;

            inputFields[a] = new EditTextBoldCursor(context);
            inputFields[a].setTag(a);
            inputFields[a].setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            inputFields[a].setHintColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
            inputFields[a].setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            inputFields[a].setBackground(null);
            inputFields[a].setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            inputFields[a].setCursorSize(AndroidUtilities.dp(20));
            inputFields[a].setCursorWidth(1.5f);
            inputFields[a].setSingleLine(true);
            inputFields[a].setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            if (a == FIELD_NAME) {
                inputFields[a].setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
                inputFields[a].addTextChangedListener(new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {

                    }

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {

                    }

                    @Override
                    public void afterTextChanged(Editable s) {
                        updateActionBarTitle();
                        updateFieldContainerState(FIELD_NAME, inputFields[FIELD_NAME].hasFocus(), true);
                    }
                });
            } else if (a == FIELD_IP) {
                inputFields[a].setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
                inputFields[a].addTextChangedListener(new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {

                    }

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {

                    }

                    @Override
                    public void afterTextChanged(Editable s) {
                        updateFieldContainerState(FIELD_IP, inputFields[FIELD_IP].hasFocus(), true);
                        checkShareDone(true);
                    }
                });
            } else if (a == FIELD_PORT) {
                inputFields[a].setInputType(InputType.TYPE_CLASS_NUMBER);
                inputFields[a].addTextChangedListener(new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {

                    }

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {

                    }

                    @Override
                    public void afterTextChanged(Editable s) {
                        if (ignoreOnTextChange) {
                            return;
                        }
                        EditText phoneField = inputFields[FIELD_PORT];
                        int start = phoneField.getSelectionStart();
                        String chars = "0123456789";
                        String str = phoneField.getText().toString();
                        StringBuilder builder = new StringBuilder(str.length());
                        for (int a = 0; a < str.length(); a++) {
                            String ch = str.substring(a, a + 1);
                            if (chars.contains(ch)) {
                                builder.append(ch);
                            }
                        }
                        ignoreOnTextChange = true;
                        int port = Utilities.parseInt(builder.toString());
                        if (port < 0 || port > 65535 || !str.equals(builder.toString())) {
                            if (port < 0) {
                                phoneField.setText("0");
                            } else if (port > 65535) {
                                phoneField.setText("65535");
                            } else {
                                phoneField.setText(builder.toString());
                            }
                        } else {
                            if (start >= 0) {
                                phoneField.setSelection(Math.min(start, phoneField.length()));
                            }
                        }
                        ignoreOnTextChange = false;
                        updateFieldContainerState(FIELD_PORT, inputFields[FIELD_PORT].hasFocus(), true);
                        checkShareDone(true);
                    }
                });
            } else if (a == FIELD_PASSWORD) {
                inputFields[a].setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                inputFields[a].setTypeface(Typeface.DEFAULT);
                inputFields[a].setTransformationMethod(PasswordTransformationMethod.getInstance());
                inputFields[a].addTextChangedListener(new SimpleFieldTextWatcher(FIELD_PASSWORD));
            } else if (a == FIELD_SECRET) {
                inputFields[a].setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
                inputFields[a].setTypeface(Typeface.DEFAULT);
                inputFields[a].setTransformationMethod(PasswordTransformationMethod.getInstance());
                inputFields[a].addTextChangedListener(new SimpleFieldTextWatcher(FIELD_SECRET));
            } else {
                inputFields[a].setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
                inputFields[a].addTextChangedListener(new SimpleFieldTextWatcher(a));
            }
            inputFields[a].setImeOptions(EditorInfo.IME_ACTION_NEXT | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
            switch (a) {
                case FIELD_NAME:
                    container.setText(LocaleController.getString(R.string.ProxyRename));
                    inputFields[a].setText(ProxyController.getInstance().getName(currentProxyInfo));
                    break;
                case FIELD_IP:
                    container.setText(LocaleController.getString(R.string.UseProxyAddress));
                    inputFields[a].setText(currentProxyInfo.settings.getAddress());
                    break;
                case FIELD_PORT:
                    container.setText(LocaleController.getString(R.string.UseProxyPort));
                    inputFields[a].setText("" + currentProxyInfo.settings.getPort());
                    break;
                case FIELD_USER:
                    container.setText(LocaleController.getString(R.string.UseProxyUsername));
                    inputFields[a].setText(currentProxyInfo.settings.getUser());
                    break;
                case FIELD_PASSWORD:
                    container.setText(LocaleController.getString(R.string.UseProxyPassword));
                    inputFields[a].setText(currentProxyInfo.settings.getPassword());
                    break;
                case FIELD_SECRET:
                    container.setText(LocaleController.getString(R.string.UseProxySecret));
                    inputFields[a].setText(currentProxyInfo.settings.getSecret());
                    break;
            }
            inputFields[a].setSelection(inputFields[a].length());

            inputFields[a].setPadding(0, AndroidUtilities.dp(16), 0, AndroidUtilities.dp(16));
            container.addView(inputFields[a], LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 16, 0, 16, 0));
            container.attachEditText(inputFields[a]);
            inputFields[a].setOnFocusChangeListener((v, hasFocus) -> {
                final int field = (Integer) v.getTag();
                updateFieldContainerState(field, hasFocus, true);
                if (field == FIELD_SECRET) {
                    updateSecretVisibility(hasFocus);
                }
            });
            updateFieldContainerState(a, false, false);

            inputFields[a].setOnEditorActionListener((textView, i, keyEvent) -> {
                if (i == EditorInfo.IME_ACTION_NEXT) {
                    int num = (Integer) textView.getTag();
                    if (num + 1 < inputFields.length) {
                        num++;
                        inputFields[num].requestFocus();
                    }
                    return true;
                } else if (i == EditorInfo.IME_ACTION_DONE) {
                    finishFragment();
                    return true;
                }
                return false;
            });
        }

        inputFieldsContainer.addView(inputFieldContainers[FIELD_NAME], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 12));
        LinearLayout addressRow = new LinearLayout(context);
        addressRow.setOrientation(LinearLayout.HORIZONTAL);
        inputFieldsContainer.addView(addressRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 12));
        addressRow.addView(inputFieldContainers[FIELD_IP], LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, 0, 0, 8, 0));
        addressRow.addView(inputFieldContainers[FIELD_PORT], LayoutHelper.createLinear(112, LayoutHelper.WRAP_CONTENT, 8, 0, 0, 0));
        inputFieldsContainer.addView(inputFieldContainers[FIELD_USER], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 12));
        inputFieldsContainer.addView(inputFieldContainers[FIELD_PASSWORD], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 12));
        inputFieldsContainer.addView(inputFieldContainers[FIELD_SECRET], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 12));
        for (int a = 0; a < inputFieldsContainer.getChildCount(); a++) {
            inputFieldsContainer.getChildAt(a).setTag(FIELDS_CONTAINER_TAG);
        }
        updateActionBarTitle();

        pasteCell = new TextSettingsCell(fragmentView.getContext());
        pasteCell.setBackground(Theme.getSelectorDrawable(true));
        pasteCell.setText(LocaleController.getString(R.string.PasteFromClipboard), false);
        pasteCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
        pasteCell.setOnClickListener(v -> {
            if (pasteType == -1) {
                return;
            }
            for (int i = 0; i < pasteFields.length; i++) {
                if (pasteType == TYPE_SOCKS5 && i == FIELD_SECRET) {
                    continue;
                }
                if (pasteType == TYPE_MTPROTO && (i == FIELD_USER || i == FIELD_PASSWORD)) {
                    continue;
                }
                if (pasteFields[i] != null) {
                    inputFields[i].setText(pasteFields[i]);
                } else {
                    inputFields[i].setText(null);
                }
                updateFieldContainerState(i, inputFields[i].hasFocus(), false);
            }
            inputFields[FIELD_IP].setSelection(inputFields[FIELD_IP].length());

            // clear fields that were hidden after the type change
            setProxyType(pasteType, true, () -> {
                AndroidUtilities.hideKeyboard(inputFieldsContainer.findFocus());
                for (int i = 1; i < pasteFields.length; i++) {
                    if ((pasteType != TYPE_SOCKS5 || i == FIELD_SECRET) && (pasteType != TYPE_MTPROTO || i == FIELD_USER || i == FIELD_PASSWORD)) {
                        inputFields[i].setText(null);
                        updateFieldContainerState(i, inputFields[i].hasFocus(), false);
                    }
                }
            });
        });
        linearLayout2.addView(pasteCell, 0, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        pasteCell.setVisibility(View.GONE);
        sectionCell[2] = new ShadowSectionCell(context);
        sectionCell[2].setVisibility(View.GONE);
        linearLayout2.addView(sectionCell[2], 1, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        shareCell = new TextSettingsCell(context);
        shareCell.setBackground(Theme.getSelectorDrawable(true));
        shareCell.setText(LocaleController.getString(R.string.ShareFile), false);
        shareCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
        linearLayout2.addView(shareCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        shareCell.setOnClickListener(v -> {
            final String link = ProxyController.getInstance().buildShareLink(currentProxyInfo, inputFields[FIELD_NAME].getText().toString().trim());
            if (TextUtils.isEmpty(link)) {
                return;
            }
            QRCodeBottomSheet alert = new QRCodeBottomSheet(context, LocaleController.getString(R.string.ShareQrCode), link, LocaleController.getString(R.string.QRCodeLinkHelpProxy), true);
            alert.setCenterImage(SvgHelper.getBitmap(AndroidUtilities.readRes(R.raw.qr_dog), AndroidUtilities.dp(60), AndroidUtilities.dp(60), false));
            showDialog(alert);
        });

        sponsorInfoCell = new TextInfoPrivacyCell(context);
        sponsorInfoCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        sponsorInfoCell.setText(LocaleController.getString(R.string.UseProxyTelegramInfo2));
        sponsorInfoCell.setVisibility(View.GONE);
        linearLayout2.addView(sponsorInfoCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        sectionCell[1] = new ShadowSectionCell(context);
        sectionCell[1].setBackgroundDrawable(Theme.getThemedDrawableByKey(context, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
        linearLayout2.addView(sectionCell[1], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        clipboardManager = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);

        shareDoneEnabled = true;
        shareDoneProgress = 1f;
        checkShareDone(false);

        currentType = -1;
        setProxyType(ProxySettings.typeToInt(currentProxyInfo.settings.getType()), false);

        pasteType = -1;
        pasteString = null;
        updatePasteCell();

        return fragmentView;
    }

    private void updateSecretVisibility(boolean visible) {
        if (inputFields == null || inputFields[FIELD_SECRET] == null) {
            return;
        }
        final int selection = inputFields[FIELD_SECRET].getSelectionStart();
        inputFields[FIELD_SECRET].setTransformationMethod(visible ? null : PasswordTransformationMethod.getInstance());
        inputFields[FIELD_SECRET].setSelection(Math.max(0, Math.min(selection, inputFields[FIELD_SECRET].length())));
    }

    private void updateFieldContainerState(int field, boolean focused, boolean animated) {
        if (inputFieldContainers == null || inputFields == null || field < 0 || field >= inputFieldContainers.length || inputFieldContainers[field] == null || inputFields[field] == null) {
            return;
        }
        final boolean hasText = inputFields[field].getText() != null && inputFields[field].length() > 0;
        inputFieldContainers[field].animateSelection(focused ? 1f : 0f, focused || hasText ? 1f : 0f, animated);
    }

    private class SimpleFieldTextWatcher implements TextWatcher {
        private final int field;

        private SimpleFieldTextWatcher(int field) {
            this.field = field;
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {

        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {

        }

        @Override
        public void afterTextChanged(Editable s) {
            updateFieldContainerState(field, inputFields[field].hasFocus(), true);
        }
    }

    private void updatePasteCell() {
        final ClipData clip = clipboardManager.getPrimaryClip();

        String clipText;
        if (clip != null && clip.getItemCount() > 0) {
            try {
                clipText = clip.getItemAt(0).coerceToText(fragmentView.getContext()).toString();
            } catch (Exception e) {
                clipText = null;
            }
        } else {
            clipText = null;
        }

        if (TextUtils.equals(clipText, pasteString)) {
            return;
        }

        pasteType = -1;
        pasteString = clipText;
        pasteFields = new String[inputFields.length];
        if (clipText != null) {
            final Uri uri = getProxyUriFromText(clipText);
            if (uri != null) {
                final String path = uri.getPath();
                final String host = uri.getHost();
                if (TextUtils.equals(host, "socks") || TextUtils.equals(path, "/socks")) {
                    pasteType = TYPE_SOCKS5;
                } else if (TextUtils.equals(host, "proxy") || TextUtils.equals(path, "/proxy")) {
                    pasteType = TYPE_MTPROTO;
                } else if (TextUtils.equals(host, "webproxy") || TextUtils.equals(path, "/webproxy")) {
                    pasteType = TYPE_WEB;
                }
                if (pasteType != -1) {
                    pasteFields[FIELD_NAME] = uri.getQueryParameter("title");
                    pasteFields[FIELD_IP] = uri.getQueryParameter("server");
                    pasteFields[FIELD_PORT] = uri.getQueryParameter("port");
                    if (pasteType == TYPE_SOCKS5) {
                        pasteFields[FIELD_USER] = uri.getQueryParameter("user");
                        pasteFields[FIELD_PASSWORD] = uri.getQueryParameter("pass");
                    } else {
                        pasteFields[FIELD_SECRET] = uri.getQueryParameter("secret");
                    }
                    if (pasteType == TYPE_WEB) {
                        pasteFields[FIELD_PORT] = String.valueOf(WEB_PROXY_PORT);
                    }
                }
            }
        }

        if (pasteType != -1) {
            if (pasteCell.getVisibility() != View.VISIBLE) {
                pasteCell.setVisibility(View.VISIBLE);
                sectionCell[2].setVisibility(View.VISIBLE);
            }
        } else {
            if (pasteCell.getVisibility() != View.GONE) {
                pasteCell.setVisibility(View.GONE);
                sectionCell[2].setVisibility(View.GONE);
            }
        }
    }

    private Uri getProxyUriFromText(String text) {
        final String[] prefixes = {
            "https://t.me/socks?", "http://t.me/socks?", "t.me/socks?", "tg://socks?",
            "https://t.me/proxy?", "http://t.me/proxy?", "t.me/proxy?", "tg://proxy?"
        };
        for (String prefix : prefixes) {
            final int index = text.indexOf(prefix);
            if (index >= 0) {
                String link = text.substring(index).trim();
                final int space = link.indexOf(' ');
                if (space >= 0) {
                    link = link.substring(0, space);
                }
                if (link.startsWith("t.me/")) {
                    link = "https://" + link;
                }
                return Uri.parse(link);
            }
        }
        return null;
    }

    private void setShareDoneEnabled(boolean enabled, boolean animated) {
        if (shareDoneEnabled != enabled) {
            if (shareDoneAnimator != null) {
                shareDoneAnimator.cancel();
            } else if (animated) {
                shareDoneAnimator = ValueAnimator.ofFloat(0f, 1f);
                shareDoneAnimator.setDuration(200);
                shareDoneAnimator.addUpdateListener(a -> {
                    shareDoneProgress = AndroidUtilities.lerp(shareDoneProgressAnimValues, a.getAnimatedFraction());
                    shareCell.setTextColor(ColorUtils.blendARGB(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2), Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4), shareDoneProgress));
                    doneItem.setAlpha(shareDoneProgress / 2f + 0.5f);
                });
            }
            if (animated) {
                shareDoneProgressAnimValues[0] = shareDoneProgress;
                shareDoneProgressAnimValues[1] = enabled ? 1f : 0f;
                shareDoneAnimator.start();
            } else {
                shareDoneProgress = enabled ? 1f : 0f;
                shareCell.setTextColor(enabled ? Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4) : Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
                doneItem.setAlpha(enabled ? 1f : .5f);
            }
            shareCell.setEnabled(enabled);
            doneItem.setEnabled(enabled);
            shareDoneEnabled = enabled;
        }
    }

    private void checkShareDone(boolean animated) {
        if (shareCell == null || doneItem == null || inputFields[FIELD_IP] == null || inputFields[FIELD_PORT] == null) {
            return;
        }
        if (currentType == TYPE_WEB) {
            setShareDoneEnabled(!TextUtils.isEmpty(WebProxyTransport.normalizeHost(inputFields[FIELD_IP].getText().toString())) && WebProxyTransport.isValidSecret(inputFields[FIELD_SECRET].getText().toString()), animated);
        } else {
            setShareDoneEnabled(inputFields[FIELD_IP].length() != 0 && Utilities.parseInt(inputFields[FIELD_PORT].getText().toString()) != 0, animated);
        }
    }

    private void updateActionBarTitle() {
        if (actionBar == null) {
            return;
        }
        String name;
        if (inputFields != null && inputFields.length > 0 && inputFields[FIELD_NAME] != null) {
            name = inputFields[FIELD_NAME].getText().toString().trim();
        } else {
            name = currentProxyInfo != null ? ProxyController.getInstance().getName(currentProxyInfo) : null;
        }
        actionBar.setTitle(TextUtils.isEmpty(name) ? LocaleController.getString(R.string.ProxyDetails) : name);
    }

    private void setProxyType(int type, boolean animated) {
        setProxyType(type, animated, null);
    }

    private void setProxyType(int type, boolean animated, Runnable onTransitionEnd) {
        if (currentType != type) {
            currentType = type;
            TransitionManager.endTransitions(linearLayout2);
            if (animated) {
                TransitionSet transitionSet = new TransitionSet()
                        .addTransition(new Fade(Fade.OUT))
                        .addTransition(new ChangeBounds())
                        .addTransition(new Fade(Fade.IN))
                        .setInterpolator(CubicBezierInterpolator.DEFAULT)
                        .setDuration(250);
                if (onTransitionEnd != null) {
                    transitionSet.addListener(new Transition.TransitionListener() {
                        @Override
                        public void onTransitionStart(Transition transition) {
                        }

                        @Override
                        public void onTransitionEnd(Transition transition) {
                            onTransitionEnd.run();
                        }

                        @Override
                        public void onTransitionCancel(Transition transition) {
                        }

                        @Override
                        public void onTransitionPause(Transition transition) {
                        }

                        @Override
                        public void onTransitionResume(Transition transition) {
                        }
                    });
                }
                TransitionManager.beginDelayedTransition(linearLayout2, transitionSet);
            }
            final boolean isWebProxy = currentType == TYPE_WEB;
            if (currentType == TYPE_SOCKS5) {
                sponsorInfoCell.setVisibility(View.GONE);
                inputFieldContainers[FIELD_SECRET].setVisibility(View.GONE);
                inputFieldContainers[FIELD_PASSWORD].setVisibility(View.VISIBLE);
                inputFieldContainers[FIELD_USER].setVisibility(View.VISIBLE);
            } else {
                sponsorInfoCell.setVisibility(isWebProxy ? View.GONE : View.VISIBLE);
                inputFieldContainers[FIELD_SECRET].setVisibility(View.VISIBLE);
                inputFieldContainers[FIELD_PASSWORD].setVisibility(View.GONE);
                inputFieldContainers[FIELD_USER].setVisibility(View.GONE);
            }
            inputFieldContainers[FIELD_PORT].setVisibility(isWebProxy ? View.GONE : View.VISIBLE);
            if (isWebProxy) {
                inputFields[FIELD_PORT].setText(String.valueOf(WEB_PROXY_PORT));
            }

            for (int a = 0; a < typeCell.length; a++) {
                typeCell[a].setChecked(currentType == a, animated);
            }

            if (inputFieldContainers[FIELD_IP] != null) {
                LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) inputFieldContainers[FIELD_IP].getLayoutParams();
                if (params != null) {
                    final int rightMargin = isWebProxy ? 0 : AndroidUtilities.dp(8);
                    if (params.rightMargin != rightMargin) {
                        params.rightMargin = rightMargin;
                        inputFieldContainers[FIELD_IP].setLayoutParams(params);
                    }
                }
            }
            checkShareDone(animated);
        }
    }

    @Override
    public void onTransitionAnimationEnd(boolean isOpen, boolean backward) {
        if (isOpen && !backward && addingNewProxy) {
            inputFields[FIELD_NAME].requestFocus();
            AndroidUtilities.showKeyboard(inputFields[FIELD_NAME]);
        }
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        final ThemeDescription.ThemeDescriptionDelegate delegate = () -> {
            if (shareCell != null && (shareDoneAnimator == null || !shareDoneAnimator.isRunning())) {
                shareCell.setTextColor(shareDoneEnabled ? Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4) : Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
            }
            if (inputFieldContainers != null) {
                for (OutlineTextContainerView container : inputFieldContainers) {
                    if (container != null) {
                        container.updateColor();
                    }
                }
            }
        };
        ArrayList<ThemeDescription> arrayList = new ArrayList<>();
        arrayList.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundGray));
        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));
        arrayList.add(new ThemeDescription(scrollView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault));
        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon));
        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));
        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SEARCH, null, null, null, null, Theme.key_actionBarDefaultSearch));
        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SEARCHPLACEHOLDER, null, null, null, null, Theme.key_actionBarDefaultSearchPlaceholder));
        arrayList.add(new ThemeDescription(linearLayout2, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider));

        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, delegate, Theme.key_windowBackgroundWhite));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, delegate, Theme.key_listSelector));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, delegate, Theme.key_windowBackgroundWhiteBlueText4));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, delegate, Theme.key_windowBackgroundWhiteGrayText2));

        arrayList.add(new ThemeDescription(pasteCell, 0, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueText4));

        for (int a = 0; a < typeCell.length; a++) {
            arrayList.add(new ThemeDescription(typeCell[a], 0, new Class[]{RadioCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
            arrayList.add(new ThemeDescription(typeCell[a], ThemeDescription.FLAG_CHECKBOX, new Class[]{RadioCell.class}, new String[]{"radioButton"}, null, null, null, Theme.key_radioBackground));
            arrayList.add(new ThemeDescription(typeCell[a], ThemeDescription.FLAG_CHECKBOXCHECK, new Class[]{RadioCell.class}, new String[]{"radioButton"}, null, null, null, Theme.key_radioBackgroundChecked));
        }

        if (inputFields != null) {
            for (int a = 0; a < inputFields.length; a++) {
                arrayList.add(new ThemeDescription(inputFields[a], ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
                arrayList.add(new ThemeDescription(inputFields[a], ThemeDescription.FLAG_HINTTEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteHintText));
                arrayList.add(new ThemeDescription(inputFields[a], ThemeDescription.FLAG_HINTTEXTCOLOR | ThemeDescription.FLAG_PROGRESSBAR, null, null, null, null, Theme.key_windowBackgroundWhiteBlueHeader));
                arrayList.add(new ThemeDescription(inputFields[a], ThemeDescription.FLAG_CURSORCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
            }
            arrayList.add(new ThemeDescription(null, 0, null, null, null, delegate, Theme.key_windowBackgroundWhiteHintText));
            arrayList.add(new ThemeDescription(null, 0, null, null, null, delegate, Theme.key_windowBackgroundWhiteInputField));
            arrayList.add(new ThemeDescription(null, 0, null, null, null, delegate, Theme.key_windowBackgroundWhiteInputFieldActivated));
            arrayList.add(new ThemeDescription(null, 0, null, null, null, delegate, Theme.key_text_RedBold));
        } else {
            arrayList.add(new ThemeDescription(null, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
            arrayList.add(new ThemeDescription(null, ThemeDescription.FLAG_HINTTEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteHintText));
        }

        arrayList.add(new ThemeDescription(headerCell, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite));
        arrayList.add(new ThemeDescription(headerCell, 0, new Class[]{HeaderCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueHeader));
        for (int a = 0; a < sectionCell.length; a++) {
            if (sectionCell[a] != null) {
                arrayList.add(new ThemeDescription(sectionCell[a], ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{ShadowSectionCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));
            }
        }
        arrayList.add(new ThemeDescription(sponsorInfoCell, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundGray));
        arrayList.add(new ThemeDescription(sponsorInfoCell, 0, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText4));
        arrayList.add(new ThemeDescription(sponsorInfoCell, ThemeDescription.FLAG_LINKCOLOR, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteLinkText));

        return arrayList;
    }
}
