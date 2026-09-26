package com.exteragram.messenger.speech.ui;

import android.content.DialogInterface;
import android.widget.TextView;

import com.exteragram.messenger.speech.VoiceRecognitionController;
import com.exteragram.messenger.speech.VoiceRecognitionController.RecognitionModel;
import com.exteragram.messenger.translator.TranslatorUtils;
import com.exteragram.messenger.utils.ui.PopupUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;

import java.util.ArrayList;
import java.util.List;

public final class RecognitionModelDialogs {

    private static final String PROVIDER = "vosk";

    private RecognitionModelDialogs() {
    }

    public static CharSequence getRecognitionLanguageOption(String language) {
        String title = TranslatorUtils.getLanguageTitleSystem(language);
        String displayName = TranslatorUtils.getLanguageDisplayName(language);
        if (displayName == null) {
            return title;
        }
        return title + " - " + displayName;
    }

    public static void showDownloadDialog(BaseFragment fragment, String language, RecognitionModel model, Runnable onDownloaded) {
        if (fragment.getContext() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(fragment.getContext());
        builder.setTitle(LocaleController.getString(R.string.MissingLanguageModel));
        builder.setSubtitle(AndroidUtilities.replaceTags(LocaleController.formatString(R.string.ModelDownloadInfo, TranslatorUtils.getLanguageTitleSystem(language))));
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        builder.setPositiveButton(LocaleController.formatString(R.string.ModelDownload, AndroidUtilities.formatFileSize(model.getSize())), (dialog, which) -> startDownload(fragment, language, onDownloaded));
        fragment.showDialog(builder.create());
    }

    private static void startDownload(BaseFragment fragment, String language, Runnable onDownloaded) {
        fragment.dismissCurrentDialog();

        LoadingModelView loadingModelView = new LoadingModelView(fragment.getContext());
        AlertDialog.Builder builder = new AlertDialog.Builder(fragment.getContext());
        builder.setView(loadingModelView);
        AlertDialog alert = builder.create();
        alert.setCanceledOnTouchOutside(false);
        alert.setCancelable(false);

        boolean[] done = {false};
        float[] progressValue = {0f};
        long[] shownAt = {-1};
        Runnable updateProgress = () -> loadingModelView.setProgress(progressValue[0]);

        // show the progress dialog only if the download takes noticeable time
        AndroidUtilities.runOnUIThread(() -> {
            if (done[0]) {
                return;
            }
            shownAt[0] = System.currentTimeMillis();
            alert.show();
        }, 150);

        VoiceRecognitionController.getInstance().downloadModel(PROVIDER, language, new VoiceRecognitionController.DownloadModelCallback() {
            @Override
            public void onProgress(float progress) {
                progressValue[0] = progress;
                AndroidUtilities.cancelRunOnUIThread(updateProgress);
                AndroidUtilities.runOnUIThread(updateProgress);
            }

            @Override
            public void onCompleted() {
                AndroidUtilities.runOnUIThread(() -> {
                    done[0] = true;
                    loadingModelView.setProgress(1f);
                    if (shownAt[0] > 0) {
                        AndroidUtilities.runOnUIThread(alert::dismiss, Math.max(0, 1000 - (System.currentTimeMillis() - shownAt[0])));
                    } else {
                        alert.dismiss();
                    }
                    onDownloaded.run();
                    BulletinFactory.of(fragment).createSuccessBulletin(LocaleController.getString(R.string.ModelDownloaded)).show();
                });
            }

            @Override
            public void onError(Exception e) {
                AndroidUtilities.runOnUIThread(() -> {
                    alert.dismiss();
                    BulletinFactory.of(fragment).createErrorBulletin(LocaleController.getString(R.string.ModelError)).show();
                });
            }
        });
    }

    public static void showDeleteFlow(BaseFragment fragment, List<RecognitionModel> models, Utilities.Callback<RecognitionModel> onDeleted) {
        if (fragment.getContext() == null || models.isEmpty()) {
            return;
        }
        if (models.size() == 1) {
            showDeleteConfirmDialog(fragment, models.get(0), onDeleted);
            return;
        }
        ArrayList<CharSequence> options = new ArrayList<>(models.size());
        for (RecognitionModel model : models) {
            options.add(getRecognitionLanguageOption(model.getLanguage()));
        }
        PopupUtils.showDialogWithoutRadio(options, LocaleController.getString(R.string.DeleteRecognitionModel), fragment.getContext(), which -> {
            if (which < 0 || which >= models.size()) {
                return;
            }
            showDeleteConfirmDialog(fragment, models.get(which), onDeleted);
        });
    }

    private static void showDeleteConfirmDialog(BaseFragment fragment, RecognitionModel model, Utilities.Callback<RecognitionModel> onDeleted) {
        if (fragment.getContext() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(fragment.getContext());
        builder.setTitle(LocaleController.getString(R.string.DeleteRecognitionModel));
        builder.setSubtitle(AndroidUtilities.replaceTags(LocaleController.formatString(R.string.DeleteRecognitionModelInfo, getRecognitionLanguageOption(model.getLanguage()))));
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        builder.setPositiveButton(LocaleController.getString(R.string.Delete), (dialog, which) ->
            VoiceRecognitionController.getInstance().deleteModel(PROVIDER, model.getLanguage(), new VoiceRecognitionController.DeleteModelCallback() {
                @Override
                public void onCompleted() {
                    AndroidUtilities.runOnUIThread(() -> {
                        onDeleted.run(model);
                        BulletinFactory.of(fragment).createSuccessBulletin(LocaleController.getString(R.string.RecognitionModelDeleted)).show();
                    });
                }

                @Override
                public void onError(Exception e) {
                    AndroidUtilities.runOnUIThread(() -> BulletinFactory.of(fragment).createErrorBulletin(LocaleController.getString(R.string.RecognitionModelDeleteError)).show());
                }
            })
        );
        AlertDialog dialog = builder.create();
        fragment.showDialog(dialog);
        TextView button = (TextView) dialog.getButton(DialogInterface.BUTTON_POSITIVE);
        if (button != null) {
            button.setTextColor(Theme.getColor(Theme.key_text_RedBold));
        }
    }
}
