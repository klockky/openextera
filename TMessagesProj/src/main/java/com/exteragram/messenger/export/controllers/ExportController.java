package com.exteragram.messenger.export.controllers;

import android.os.Environment;
import android.util.Log;

import com.exteragram.messenger.export.ExportSettings;
import com.exteragram.messenger.export.api.ApiWrap;
import com.exteragram.messenger.export.output.AbstractWriter;
import com.exteragram.messenger.export.output.OutputFile;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BaseController;
import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.BulletinFactory;

import java.io.File;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;

public class ExportController extends BaseController {

    private static final int ANY_CHATS_MASK = 2016;

    public static final int INITIALIZATING_NOTIFICATION = 6666;
    public static final int DIALOGS_LIST_NOTIFICATION = 6666;
    private static int defaultId = INITIALIZATING_NOTIFICATION + 1;
    public static final int PERSONAL_INFO_NOTIFICATION = defaultId++;
    public static final int USERPICS_NOTIFICATION = defaultId++;
    public static final int STORIES_NOTIFICATION = defaultId++;
    public static final int CONTACTS_NOTIFICATION = defaultId++;
    public static final int SESSIONS_NOTIFICATION = defaultId++;
    public static final int OTHER_DATA_NOTIFICATION = defaultId++;
    public static final int DIALOGS_NOTIFICATION = defaultId++;
    public static final int FINISH_NOTIFICATION = defaultId++;

    public static volatile DispatchQueue exportQueue = new DispatchQueue("exportQueue");

    private static final ExportController[] Instance = new ExportController[16];

    private final int currAcc;
    private final ArrayList<ProcessingState.Step> _steps = new ArrayList<>();
    private final OutputFile.Stats _stats = new OutputFile.Stats();
    private ExportSettings _settings;
    private AbstractWriter _writer;
    private ProcessingState _state;
    private ApiWrap.DialogsInfo _dialogsInfo;

    private int _stepIndex = -1;
    private int[] _substepsInStep;
    private int _substepsTotal = 0;
    private int _substepsPassed = 0;
    private int _dialogIndex = -1;
    private ProcessingState.Step _lastProcessingStep = ProcessingState.Step.Initializing;
    private int _messagesWritten = 0;
    private int _messagesCount = 0;
    private int _userpicsWritten = 0;
    private int _userpicsCount = 0;
    private int _storiesWritten = 0;
    private int _storiesCount = 0;

    public static class ProcessingState {

        public enum Step {
            Initializing,
            DialogsList,
            PersonalInfo,
            Userpics,
            Stories,
            Contacts,
            Sessions,
            OtherData,
            Dialogs
        }

        public enum EntityType {
            Chat,
            SavedMessages,
            RepliesMessages,
            VerifyCodes,
            Other
        }

        public Step step = Step.Initializing;
        public int substepsPassed = 0;
        public int substepsNow = 0;
        public int substepsTotal = 0;
        public EntityType entityType = EntityType.Other;
        public String entityName;
        public int entityIndex = 0;
        public int entityCount = 0;
        public int itemIndex = 0;
        public int itemCount = 0;
        public long bytesRandomId = 0;
        public String bytesName;
        public long bytesLoaded = 0;
        public long bytesCount = 0;
    }

    public static class FinishedState extends ProcessingState {

        public String path;
        public int filesCount;
        public long bytesCount;

        public FinishedState(String path, int filesCount, long bytesCount) {
            this.path = path;
            this.filesCount = filesCount;
            this.bytesCount = bytesCount;
        }
    }

    public ExportController(int num) {
        super(num);
        currAcc = num;
    }

    public static ExportController getInstance(int num) {
        ExportController localInstance = Instance[num];
        if (localInstance == null) {
            synchronized (ExportController.class) {
                localInstance = Instance[num];
                if (localInstance == null) {
                    Instance[num] = localInstance = new ExportController(num);
                }
            }
        }
        return localInstance;
    }

    private static String NormalizePath(ExportSettings settings) {
        File folder = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "exteraGram");
        String path = folder.getAbsolutePath();
        if (!path.endsWith("/")) {
            path = path.concat("/");
        }
        String[] files;
        if (!folder.exists() || (files = folder.list()) == null || files.length == 0) {
            return path;
        }
        String date = new SimpleDateFormat("yyyy-MM-dd_HH_mm").format(Calendar.getInstance().getTime());
        String name;
        if (settings.onlySinglePeer()) {
            name = "ChatExport_" + date;
        } else {
            name = "DataExport_" + date;
        }
        return path.concat(name);
    }

    public void startExport(ExportSettings settings) {
        exportQueue.postRunnable(() -> startExportInternal(settings));
    }

    private void startExportInternal(ExportSettings settings) {
        if (_settings != null && _settings.path != null && !_settings.path.isEmpty()) {
            showErrorBulletin("path is not empty! aborting");
            return;
        }
        _settings = settings;
        settings.path = NormalizePath(settings);
        _writer = AbstractWriter.CreateWriter(_settings.format);
        fillExportSteps();
        exportNext();
    }

    public void fillExportSteps() {
        _steps.add(ProcessingState.Step.Initializing);
        if (!_settings.onlySinglePeer()) {
            _steps.add(ProcessingState.Step.DialogsList);
        }
        if ((_settings.types & 1) != 0) {
            _steps.add(ProcessingState.Step.PersonalInfo);
        }
        if ((_settings.types & 2) != 0) {
            _steps.add(ProcessingState.Step.Userpics);
        }
        if ((_settings.types & 2048) != 0) {
            _steps.add(ProcessingState.Step.Stories);
        }
        if ((_settings.types & 4) != 0) {
            _steps.add(ProcessingState.Step.Contacts);
        }
        if ((_settings.types & 8) != 0) {
            _steps.add(ProcessingState.Step.Sessions);
        }
        if ((_settings.types & 16) != 0) {
            _steps.add(ProcessingState.Step.OtherData);
        }
        if (!_settings.onlySinglePeer()) {
            _steps.add(ProcessingState.Step.Dialogs);
        }
    }

    private void exportNext() {
        if (++_stepIndex >= _steps.size()) {
            if (_writer.finish().isSuccess()) {
                ExportRequestsController.getInstance(currAcc).invokeFinish(false, this::setFinishedState);
                Log.i("exteraGram", "finished Export!");
            }
            return;
        }
        switch (_steps.get(_stepIndex)) {
            case Initializing:
                initialize();
                break;
            case DialogsList:
                collectDialogsList();
                break;
            case PersonalInfo:
                exportPersonalInfo();
                break;
            case Userpics:
                exportUserpics();
                break;
            case Stories:
                exportStories();
                break;
            case Contacts:
                exportContacts();
                break;
            case Sessions:
                exportSessions();
                break;
            case OtherData:
                exportOtherData();
                break;
            case Dialogs:
                exportDialogs();
                break;
        }
    }

    private void setFinishedState() {
        setState(new FinishedState(_writer.mainFilePath(), _stats.filesCount(), _stats.bytesCount()));
    }

    private void initialize() {
        setState(new ProcessingState());
        ExportRequestsController.getInstance(currAcc).startExport(_settings, _stats, this::initialized);
    }

    private void initialized(ExportRequestsController.StartInfo info) {
        if (_writer.start(_settings, _stats).isSuccess()) {
            fillSubstepsInSteps(info);
            exportNext();
        }
    }

    private void collectDialogsList() {
        setState(stateDialogsList(0));
        ExportRequestsController.getInstance(currAcc).requestDialogsList(count -> {
            if (count > 0) {
                setState(stateDialogsList(count - 1));
            }
            return true;
        }, info -> {
            _dialogsInfo = info;
            exportNext();
        });
    }

    private void exportPersonalInfo() {
        setState(statePersonalInfo());
        ExportRequestsController.getInstance(currAcc).requestPersonalInfo(info -> {
            if (_writer.writePersonal(info).isSuccess()) {
                exportNext();
            }
        });
    }

    private ProcessingState statePersonalInfo() {
        return prepareState(ProcessingState.Step.PersonalInfo, state -> {});
    }

    private void exportUserpics() {
        ExportRequestsController.getInstance(currAcc).requestUserpics(info -> {
            if (!_writer.writeUserpicsStart(info).isSuccess()) {
                return false;
            }
            _userpicsWritten = 0;
            _userpicsCount = info.count();
            return true;
        }, progress -> {
            setState(stateUserpics(progress));
            return true;
        }, slice -> {
            if (!_writer.writeUserpicsSlice(slice).isSuccess()) {
                return false;
            }
            _userpicsWritten += slice.size();
            setState(stateUserpics(new ApiWrap.DownloadProgress()));
            return true;
        }, () -> {
            if (_writer.writeUserpicsEnd().isSuccess()) {
                exportNext();
            }
        });
    }

    private ProcessingState stateUserpics(ApiWrap.DownloadProgress progress) {
        return prepareState(ProcessingState.Step.Userpics, state -> {
            int index = _userpicsWritten + progress.itemIndex();
            state.entityIndex = index;
            state.entityCount = Math.max(_userpicsCount, index);
            state.bytesRandomId = progress.randomId();
            if (!progress.path().isEmpty()) {
                state.bytesName = progress.path().substring(progress.path().lastIndexOf('/') + 1);
            }
            state.bytesLoaded = progress.ready();
            state.bytesCount = progress.total();
        });
    }

    private void exportStories() {
        ExportRequestsController.getInstance(currAcc).requestStories(count -> {
            if (!_writer.writeStoriesStart(count).isSuccess()) {
                return false;
            }
            _storiesWritten = 0;
            _storiesCount = count;
            return true;
        }, progress -> {
            setState(stateStories(progress));
            return true;
        }, slice -> {
            if (!_writer.writeStoriesSlice(slice).isSuccess()) {
                return false;
            }
            _storiesWritten += slice.list.size();
            setState(stateStories(new ApiWrap.DownloadProgress()));
            return true;
        }, () -> {
            if (_writer.writeStoriesEnd().isSuccess()) {
                exportNext();
            }
        });
    }

    private ProcessingState stateStories(ApiWrap.DownloadProgress progress) {
        return prepareState(ProcessingState.Step.Stories, state -> {
            int index = _storiesWritten + progress.itemIndex();
            state.entityIndex = index;
            state.entityCount = Math.max(_storiesCount, index);
            state.bytesRandomId = progress.randomId();
            if (!progress.path().isEmpty()) {
                state.bytesName = progress.path().substring(progress.path().lastIndexOf('/') + 1);
            }
            state.bytesLoaded = progress.ready();
            state.bytesCount = progress.total();
        });
    }

    private void exportContacts() {
        setState(prepareState(ProcessingState.Step.Contacts, null));
        ExportRequestsController.getInstance(currAcc).requestContacts(contacts -> {
            if (_writer.writeContactsList(contacts).isSuccess()) {
                exportNext();
            }
        });
    }

    private ProcessingState stateDialogsList(int processed) {
        return prepareState(ProcessingState.Step.DialogsList, state -> {
            state.entityIndex = processed;
            state.entityCount = Math.max(processed, substepsInStep(ProcessingState.Step.Dialogs));
        });
    }

    private ProcessingState prepareState(ProcessingState.Step step, Utilities.Callback<ProcessingState> fill) {
        if (step != _lastProcessingStep) {
            _substepsPassed += substepsInStep(_lastProcessingStep);
            _lastProcessingStep = step;
        }
        ProcessingState state = new ProcessingState();
        if (fill != null) {
            fill.run(state);
        }
        state.step = step;
        state.substepsPassed = _substepsPassed;
        state.substepsNow = substepsInStep(_lastProcessingStep);
        state.substepsTotal = _substepsTotal;
        return state;
    }

    private int substepsInStep(ProcessingState.Step step) {
        return _substepsInStep[step.ordinal()];
    }

    private void fillSubstepsInSteps(ExportRequestsController.StartInfo info) {
        int[] result = new int[ProcessingState.Step.values().length];
        result[ProcessingState.Step.Initializing.ordinal()] = 1;
        if ((_settings.types & ANY_CHATS_MASK) != 0) {
            result[ProcessingState.Step.DialogsList.ordinal()] = 1;
        }
        if ((_settings.types & 32) != 0) {
            result[ProcessingState.Step.PersonalInfo.ordinal()] = 1;
        }
        if ((_settings.types & 2) != 0) {
            result[ProcessingState.Step.Userpics.ordinal()] = 1;
        }
        if ((_settings.types & 2048) != 0) {
            result[ProcessingState.Step.Stories.ordinal()] = 1;
        }
        if ((_settings.types & 4) != 0) {
            result[ProcessingState.Step.Contacts.ordinal()] = 1;
        }
        if ((_settings.types & 8) != 0) {
            result[ProcessingState.Step.Sessions.ordinal()] = 1;
        }
        if ((_settings.types & 16) != 0) {
            result[ProcessingState.Step.OtherData.ordinal()] = 1;
        }
        if ((_settings.types & ANY_CHATS_MASK) != 0) {
            result[ProcessingState.Step.Dialogs.ordinal()] = info.dialogsCount;
        }
        _substepsInStep = result;
        _substepsTotal = Arrays.stream(result).sum();
    }

    private void setState(ProcessingState state) {
        if (stopped()) {
            return;
        }
        final int notification;
        if (state instanceof FinishedState) {
            notification = FINISH_NOTIFICATION;
        } else {
            switch (state.step) {
                case Initializing:
                    notification = INITIALIZATING_NOTIFICATION;
                    break;
                case DialogsList:
                    notification = DIALOGS_LIST_NOTIFICATION;
                    break;
                case PersonalInfo:
                    notification = PERSONAL_INFO_NOTIFICATION;
                    break;
                case Userpics:
                    notification = USERPICS_NOTIFICATION;
                    break;
                case Stories:
                    notification = STORIES_NOTIFICATION;
                    break;
                case Contacts:
                    notification = CONTACTS_NOTIFICATION;
                    break;
                case Sessions:
                    notification = SESSIONS_NOTIFICATION;
                    break;
                case OtherData:
                    notification = OTHER_DATA_NOTIFICATION;
                    break;
                case Dialogs:
                    notification = DIALOGS_NOTIFICATION;
                    break;
                default:
                    notification = -1;
                    break;
            }
        }
        if (notification != -1) {
            AndroidUtilities.runOnUIThread(() -> NotificationCenter.getInstance(currAcc).postNotificationName(notification, state));
        }
        _state = state;
    }

    private boolean stopped() {
        return _state instanceof FinishedState;
    }

    private void exportDialogs() {
        if (_writer.writeDialogsStart(_dialogsInfo).isSuccess()) {
            exportNextDialog();
        }
    }

    private void exportSessions() {
        setState(prepareState(ProcessingState.Step.Sessions, null));
        ExportRequestsController.getInstance(currAcc).requestSessions(sessions -> {
            if (_writer.writeSessionsList(sessions).isSuccess()) {
                exportNext();
            }
        });
    }

    private void exportOtherData() {
        setState(prepareState(ProcessingState.Step.OtherData, null));
        ExportRequestsController.getInstance(currAcc).requestOtherData("lists/other_data.json", file -> {
            if (_writer.writeOtherData(file).isSuccess()) {
                exportNext();
            }
        });
    }

    private void exportNextDialog() {
        ApiWrap.DialogInfo info = _dialogsInfo.getItemAt(++_dialogIndex);
        if (info != null) {
            ExportRequestsController.getInstance(currAcc).requestMessages(info, fullInfo -> {
                if (!_writer.writeDialogStart(info).isSuccess()) {
                    return false;
                }
                _messagesWritten = 0;
                _messagesCount = fullInfo.messagesCountPerSplit.stream().mapToInt(Integer::intValue).sum();
                setState(stateDialogs(new ApiWrap.DownloadProgress()));
                return true;
            }, progress -> {
                setState(stateDialogs(progress));
                return true;
            }, slice -> {
                if (!_writer.writeDialogSlice(slice).isSuccess()) {
                    return false;
                }
                _messagesWritten += slice.list.size();
                setState(stateDialogs(new ApiWrap.DownloadProgress()));
                return true;
            }, () -> {
                if (_writer.writeDialogEnd().isSuccess()) {
                    exportNextDialog();
                }
            });
        } else if (_writer.writeDialogsEnd().isSuccess()) {
            exportNext();
        }
    }

    private void fillMessagesState(ProcessingState result, ApiWrap.DialogsInfo info, int index, ApiWrap.DownloadProgress progress) {
        ApiWrap.DialogInfo dialog = info.getItemAt(index);
        result.entityIndex = index;
        result.entityCount = info.chats.size() + info.left.size();
        result.entityName = dialog.name;
        switch (dialog.type) {
            case Self:
                result.entityType = ProcessingState.EntityType.SavedMessages;
                break;
            case Replies:
                result.entityType = ProcessingState.EntityType.RepliesMessages;
                break;
            case VerifyCodes:
                result.entityType = ProcessingState.EntityType.VerifyCodes;
                break;
            default:
                result.entityType = ProcessingState.EntityType.Chat;
                break;
        }
        int itemIndex = _messagesWritten + progress.itemIndex();
        result.itemIndex = itemIndex;
        result.itemCount = Math.max(_messagesCount, itemIndex);
        result.bytesRandomId = progress.randomId();
        if (!progress.path().isEmpty()) {
            result.bytesName = progress.path().substring(progress.path().lastIndexOf('/') + 1);
        }
        result.bytesLoaded = progress.ready();
        result.bytesCount = progress.total();
    }

    private ProcessingState stateDialogs(ApiWrap.DownloadProgress progress) {
        return prepareState(ProcessingState.Step.Dialogs, state -> fillMessagesState(state, _dialogsInfo, _dialogIndex, progress));
    }

    private static void showErrorBulletin(String text) {
        BulletinFactory.global().createErrorBulletin(text);
    }

    public static void showError(TLRPC.TL_error error) {
        if (error.text.contains("TAKEOUT_INVALID")) {
            showErrorBulletin(LocaleController.getString(R.string.ExportInvalid));
            return;
        }
        String text = error.text;
        if (text.startsWith("TAKEOUT_INIT_DELAY_")) {
            int seconds = Utilities.parseInt(text.substring(text.lastIndexOf("_")));
            Instant now = Instant.now();
            now.plusSeconds(seconds);
            String hours;
            if (seconds / 3600 <= 0) {
                hours = LocaleController.getString(R.string.ExportDelayLessThanHour);
            } else {
                hours = LocaleController.getString(R.string.Hours_other);
            }
            showErrorBulletin(LocaleController.formatString(R.string.ExportDelay, hours, now.toString()));
            return;
        }
        showErrorBulletin("API error happened! Error text: " + text);
    }
}
