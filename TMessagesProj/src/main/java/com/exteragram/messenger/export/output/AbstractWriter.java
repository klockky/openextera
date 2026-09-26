package com.exteragram.messenger.export.output;

import com.exteragram.messenger.export.ExportSettings;
import com.exteragram.messenger.export.api.ApiWrap;
import com.exteragram.messenger.export.output.html.HtmlWriter;
import com.exteragram.messenger.export.output.htmlAndJson.HtmlAndJsonWriter;
import com.exteragram.messenger.export.output.json.JsonWriter;

import java.util.ArrayList;

public abstract class AbstractWriter {

    public enum Format {
        Html,
        Json,
        HtmlAndJson
    }

    public abstract Result finish();

    public abstract String mainFilePath();

    public abstract Result start(ExportSettings settings, OutputFile.Stats stats);

    public abstract Result writeContactsList(ApiWrap.ContactsList data);

    public abstract Result writeDialogEnd();

    public abstract Result writeDialogSlice(ApiWrap.MessagesSlice data);

    public abstract Result writeDialogStart(ApiWrap.DialogInfo data);

    public abstract Result writeDialogsEnd();

    public abstract Result writeDialogsStart(ApiWrap.DialogsInfo data);

    public abstract Result writeOtherData(ApiWrap.File data);

    public abstract Result writePersonal(ApiWrap.ExportPersonalInfo data);

    public abstract Result writeSessionsList(ApiWrap.SessionsList data);

    public abstract Result writeStoriesEnd();

    public abstract Result writeStoriesSlice(ApiWrap.StoriesSlice data);

    public abstract Result writeStoriesStart(int count);

    public abstract Result writeUserpicsEnd();

    public abstract Result writeUserpicsSlice(ArrayList<HtmlWriter.Photo> data);

    public abstract Result writeUserpicsStart(ApiWrap.UserpicsInfo data);

    public static AbstractWriter CreateWriter(Format format) {
        switch (format) {
            case Html:
                return new HtmlWriter();
            case Json:
                return new JsonWriter();
            case HtmlAndJson:
                return new HtmlAndJsonWriter();
            default:
                throw new IncompatibleClassChangeError();
        }
    }

    public static class Result {
        String path;
        Type type;

        public enum Type {
            Success,
            Error,
            FatalError
        }

        public Result(Type type, String path) {
            this.type = type;
            this.path = path;
        }

        public static Result Success() {
            return new Result(Type.Success, "");
        }

        public static Result Error() {
            return new Result(Type.Error, "");
        }

        public boolean isSuccess() {
            return type == Type.Success;
        }
    }
}
