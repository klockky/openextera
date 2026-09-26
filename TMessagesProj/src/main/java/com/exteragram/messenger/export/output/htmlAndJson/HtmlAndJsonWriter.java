package com.exteragram.messenger.export.output.htmlAndJson;

import com.exteragram.messenger.export.ExportSettings;
import com.exteragram.messenger.export.api.ApiWrap;
import com.exteragram.messenger.export.output.AbstractWriter;
import com.exteragram.messenger.export.output.OutputFile;
import com.exteragram.messenger.export.output.html.HtmlWriter;

import org.telegram.messenger.Utilities;

import java.util.ArrayList;

public class HtmlAndJsonWriter extends AbstractWriter {

    private final ArrayList<AbstractWriter> _writers;

    public HtmlAndJsonWriter() {
        _writers = new ArrayList<>();
        _writers.add(AbstractWriter.CreateWriter(Format.Html));
        _writers.add(AbstractWriter.CreateWriter(Format.Json));
    }

    private Result invoke(Utilities.CallbackReturn<AbstractWriter, Result> method) {
        Result result = new Result(Result.Type.Success, "");
        for (AbstractWriter writer : _writers) {
            Result current = method.run(writer);
            if (!current.isSuccess()) {
                result = current;
            }
        }
        return result;
    }

    @Override
    public String mainFilePath() {
        return "";
    }

    @Override
    public Result start(ExportSettings settings, OutputFile.Stats stats) {
        return invoke(writer -> writer.start(settings, stats));
    }

    @Override
    public Result writePersonal(ApiWrap.ExportPersonalInfo data) {
        return invoke(writer -> writer.writePersonal(data));
    }

    @Override
    public Result writeDialogsStart(ApiWrap.DialogsInfo data) {
        return invoke(writer -> writer.writeDialogsStart(data));
    }

    @Override
    public Result writeDialogStart(ApiWrap.DialogInfo data) {
        return invoke(writer -> writer.writeDialogStart(data));
    }

    @Override
    public Result writeDialogSlice(ApiWrap.MessagesSlice data) {
        return invoke(writer -> writer.writeDialogSlice(data));
    }

    @Override
    public Result writeDialogEnd() {
        return invoke(AbstractWriter::writeDialogEnd);
    }

    @Override
    public Result writeDialogsEnd() {
        return invoke(AbstractWriter::writeDialogsEnd);
    }

    @Override
    public Result writeSessionsList(ApiWrap.SessionsList data) {
        return invoke(writer -> writer.writeSessionsList(data));
    }

    @Override
    public Result writeUserpicsStart(ApiWrap.UserpicsInfo data) {
        return invoke(writer -> writer.writeUserpicsStart(data));
    }

    @Override
    public Result writeUserpicsSlice(ArrayList<HtmlWriter.Photo> data) {
        return invoke(writer -> writer.writeUserpicsSlice(data));
    }

    @Override
    public Result writeUserpicsEnd() {
        return invoke(AbstractWriter::writeUserpicsEnd);
    }

    @Override
    public Result writeStoriesStart(int count) {
        return invoke(writer -> writer.writeStoriesStart(count));
    }

    @Override
    public Result writeStoriesSlice(ApiWrap.StoriesSlice data) {
        return invoke(writer -> writer.writeStoriesSlice(data));
    }

    @Override
    public Result writeStoriesEnd() {
        return invoke(AbstractWriter::writeStoriesEnd);
    }

    @Override
    public Result writeContactsList(ApiWrap.ContactsList data) {
        return invoke(writer -> writer.writeContactsList(data));
    }

    @Override
    public Result writeOtherData(ApiWrap.File data) {
        return invoke(writer -> writer.writeOtherData(data));
    }

    @Override
    public Result finish() {
        return invoke(AbstractWriter::finish);
    }
}
