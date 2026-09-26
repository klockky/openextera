package com.exteragram.messenger.components;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.exteragram.messenger.pillstack.ui.pills.crypto.utils.ExchangeRates;
import com.exteragram.messenger.preferences.OtherPreferencesActivity;
import com.exteragram.messenger.utils.network.RemoteUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.ColoredImageSpan;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LinkSpanDrawable;
import org.telegram.ui.Components.RLottieImageView;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

public class SupporterBottomSheet extends BottomSheet {

    @SuppressLint("UseCompatLoadingForDrawables")
    public SupporterBottomSheet(BaseFragment fragment, Theme.ResourcesProvider resourcesProvider) {
        super(fragment.getParentActivity(), false, resourcesProvider);
        Activity context = fragment.getParentActivity();
        fixNavigationBar();

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);

        RLottieImageView imageView = new RLottieImageView(getContext());
        imageView.setScaleType(ImageView.ScaleType.CENTER);
        imageView.setImageResource(R.drawable.extera_heart_large);
        imageView.setColorFilter(new PorterDuffColorFilter(0xFFFFFFFF, PorterDuff.Mode.SRC_IN));
        imageView.setBackground(Theme.createCircleDrawable(AndroidUtilities.dp(80), ContextCompat.getColor(context, R.color.ic_background)));
        linearLayout.addView(imageView, LayoutHelper.createLinear(80, 80, Gravity.CENTER_HORIZONTAL, 0, 28, 0, 0));

        TextView titleView = new TextView(context);
        titleView.setText(LocaleController.getString(R.string.SupportDevelopment));
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        titleView.setGravity(Gravity.CENTER_HORIZONTAL);
        linearLayout.addView(titleView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 22, 14, 22, 0));

        TextView descriptionView = new TextView(context);
        descriptionView.setText(LocaleController.getString(R.string.SupportDevelopmentInfo));
        descriptionView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        descriptionView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        descriptionView.setGravity(Gravity.CENTER_HORIZONTAL);
        linearLayout.addView(descriptionView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 22, 8, 22, 0));

        float amountUsd = RemoteUtils.getFloatConfigValue("donates_amount_usd", 5.0f);
        String amountUsdText = "$" + amountUsd;
        FeatureCell donationCell = new FeatureCell(context, R.drawable.menu_feature_paid, LocaleController.getString(R.string.MakeDonation), "");
        linearLayout.addView(donationCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 20, 0, 0));

        Utilities.Callback<ExchangeRates.State> onRates = state -> {
            double tonAmount = state != null
                    ? state.formatDonate("TON", 1.0)
                    : amountUsd * (RemoteUtils.getIntConfigValue("donates_ton_markup_percent", 10) / 100.0 + 1.0);
            double rubAmount = state != null ? state.formatDonate("RUB", 100.0) : 100.0 * amountUsd;
            String tonText = "TON " + ExchangeRates.State.formatter.format(tonAmount);
            String rubText = Math.round(rubAmount) + "₽";
            SpannableStringBuilder subtitle = AndroidUtilities.replaceSingleTag(
                    LocaleController.formatString(R.string.MakeDonationInfo, amountUsdText, tonText + ", " + rubText),
                    Theme.key_chat_messageLinkIn, 0,
                    () -> {
                        dismiss();
                        fragment.presentFragment(new OtherPreferencesActivity());
                    });
            SpannableString tonIcon = new SpannableString("TON");
            ColoredImageSpan span = new ColoredImageSpan(R.drawable.mini_gram_16);
            span.setWidth(AndroidUtilities.dp(13));
            tonIcon.setSpan(span, 0, tonIcon.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            donationCell.setSubtitle(AndroidUtilities.replaceCharSequence("TON", subtitle, tonIcon));
        };
        onRates.run(ExchangeRates.getCached());
        ExchangeRates.fetch(onRates);

        linearLayout.addView(new FeatureCell(context, R.drawable.menu_feature_wallpaper, LocaleController.getString(R.string.SendProof),
                AndroidUtilities.replaceSingleTag(LocaleController.getString(R.string.SendProofInfo), Theme.key_chat_messageLinkIn, 0, () -> {
                    dismiss();
                    // TODO(openextera): exteraSquad infrastructure (donation proof goes to @exteraOwner), rework later
                    MessagesController.getInstance(currentAccount).openByUserName("exteraOwner", fragment, 1);
                })), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 16, 0, 0));
        linearLayout.addView(new FeatureCell(context, R.drawable.menu_feature_reactions, LocaleController.getString(R.string.ReceiveBadge), LocaleController.getString(R.string.ReceiveBadgeInfo)),
                LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 16, 0, 0));

        ButtonWithCounterView closeButton = new ButtonWithCounterView(context, true, null);
        closeButton.setRound();
        closeButton.setText(LocaleController.getString(R.string.Close), false);
        closeButton.setOnClickListener(v -> dismiss());
        linearLayout.addView(closeButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 0, 14, 22, 14, 14));

        ScrollView scrollView = new ScrollView(getContext());
        scrollView.addView(linearLayout);
        setCustomView(scrollView);
    }

    public static SupporterBottomSheet showAlert(BaseFragment fragment) {
        return showAlert(fragment, fragment.getResourceProvider());
    }

    public static SupporterBottomSheet showAlert(BaseFragment fragment, Theme.ResourcesProvider resourcesProvider) {
        SupporterBottomSheet sheet = new SupporterBottomSheet(fragment, resourcesProvider);
        if (fragment.getParentActivity() != null) {
            fragment.showDialog(sheet);
        }
        return sheet;
    }

    public class FeatureCell extends FrameLayout {

        private final LinkSpanDrawable.LinksTextView subtitleView;

        public FeatureCell(Context context, int icon, CharSequence title, CharSequence subtitle) {
            super(context);
            boolean isRtl = LocaleController.isRTL;

            ImageView imageView = new ImageView(getContext());
            Drawable drawable = ContextCompat.getDrawable(getContext(), icon).mutate();
            drawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider), PorterDuff.Mode.MULTIPLY));
            imageView.setImageDrawable(drawable);
            addView(imageView, LayoutHelper.createFrame(24, 24, isRtl ? Gravity.RIGHT : Gravity.LEFT, isRtl ? 0 : 27, 6, isRtl ? 27 : 0, 0));

            TextView titleView = new TextView(getContext());
            titleView.setText(title);
            titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            titleView.setTypeface(AndroidUtilities.bold());
            addView(titleView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, isRtl ? Gravity.RIGHT : Gravity.LEFT, isRtl ? 27 : 68, 0, isRtl ? 68 : 27, 0));

            subtitleView = new LinkSpanDrawable.LinksTextView(getContext());
            subtitleView.setText(subtitle);
            subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            subtitleView.setTextColor(Theme.getColor(Theme.key_dialogTextGray3, resourcesProvider));
            subtitleView.setLinkTextColor(Theme.getColor(Theme.key_chat_messageLinkIn, resourcesProvider));
            subtitleView.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
            addView(subtitleView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, isRtl ? Gravity.RIGHT : Gravity.LEFT, isRtl ? 27 : 68, 20, isRtl ? 68 : 27, 0));
        }

        public void setSubtitle(CharSequence subtitle) {
            subtitleView.setText(subtitle);
        }
    }
}
