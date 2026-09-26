package com.exteragram.messenger.preferences.components;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.exteragram.messenger.preferences.utils.IconShapeHelper;
import com.exteragram.messenger.utils.ui.MonetUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RLottieImageView;

public class HeaderSettingsCell extends LinearLayout implements CustomPreferenceCell {

    public final RLottieImageView imageView;
    public final TextView titleTextView;
    public final TextView subtitleTextView;
    private Path shapePath;

    public HeaderSettingsCell(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER);

        Drawable foreground = ContextCompat.getDrawable(context, R.drawable.ic_foreground).mutate();
        Theme.ThemeInfo activeTheme = Theme.getActiveTheme();
        int backgroundColor = ContextCompat.getColor(context, R.color.ic_background);
        if (activeTheme.isMonet() && Build.VERSION.SDK_INT >= 31) {
            backgroundColor = MonetUtils.getColor(activeTheme.isDark() ? "a2_800" : "a1_100");
            foreground = ContextCompat.getDrawable(context, R.drawable.ic_foreground_solid).mutate();
            foreground.setColorFilter(new PorterDuffColorFilter(MonetUtils.getColor(activeTheme.isDark() ? "a1_200" : "a1_700"), PorterDuff.Mode.MULTIPLY));
        }

        imageView = new RLottieImageView(context) {
            @Override
            public void draw(Canvas canvas) {
                canvas.save();
                canvas.clipPath(getPath());
                super.draw(canvas);
                canvas.restore();
            }
        };
        imageView.setBackgroundColor(backgroundColor);
        imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        imageView.setImageDrawable(foreground);
        addView(imageView, LayoutHelper.createLinear(72, 72, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 28, 0, 0));

        titleTextView = new TextView(context);
        titleTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        titleTextView.setTypeface(AndroidUtilities.bold());
        titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        titleTextView.setText(LocaleController.getString(R.string.exteraAppName));
        titleTextView.setLines(1);
        titleTextView.setMaxLines(1);
        titleTextView.setSingleLine(true);
        titleTextView.setPadding(0, 0, 0, 0);
        titleTextView.setGravity(Gravity.CENTER);
        addView(titleTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 50, 16, 50, 0));

        subtitleTextView = new TextView(context);
        subtitleTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        subtitleTextView.setTypeface(AndroidUtilities.bold());
        subtitleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        subtitleTextView.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
        try {
            PackageInfo packageInfo = ApplicationLoader.applicationContext.getPackageManager().getPackageInfo(ApplicationLoader.applicationContext.getPackageName(), 0);
            StringBuilder version = new StringBuilder(BuildVars.BUILD_VERSION_STRING);
            if (packageInfo != null) {
                version.append(" (").append(packageInfo.versionCode).append(")");
            }
            subtitleTextView.setText(version);
        } catch (PackageManager.NameNotFoundException e) {
            throw new RuntimeException(e);
        }
        subtitleTextView.setGravity(Gravity.CENTER);
        subtitleTextView.setLines(0);
        subtitleTextView.setMaxLines(0);
        subtitleTextView.setSingleLine(false);
        subtitleTextView.setPadding(0, 0, 0, 0);
        addView(subtitleTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 60, 2, 60, 28));
    }

    private Path getPath() {
        if (shapePath == null) {
            shapePath = IconShapeHelper.INSTANCE.getFinalIconShapePath(72, 72, 18);
        }
        return shapePath;
    }

    @Override
    public void invalidate() {
        shapePath = null;
        imageView.invalidate();
        super.invalidate();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof HeaderSettingsCell)) {
            return false;
        }
        HeaderSettingsCell other = (HeaderSettingsCell) o;
        return imageView.equals(other.imageView) && titleTextView.equals(other.titleTextView) && subtitleTextView.equals(other.subtitleTextView);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
    }
}
