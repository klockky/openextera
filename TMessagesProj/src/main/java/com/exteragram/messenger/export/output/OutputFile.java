package com.exteragram.messenger.export.output;

import android.util.Log;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.NativeByteBuffer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class OutputFile {

    public final File _file;
    private boolean _inStats = false;
    private long _offset = 0;
    private final Stats _stats;

    public OutputFile(String path, Stats stats) {
        _file = new File(path);
        try {
            if (_file.getPath().contains("/")) {
                _file.getParentFile().mkdirs();
            }
            _file.createNewFile();
            _stats = stats;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String PrepareRelativePath(String folder, String suggested) {
        if (!new File(folder + "/" + suggested).exists()) {
            return suggested;
        }

        final int position = suggested.indexOf('.');
        final String prefix = suggested.substring(0, position);
        final String postfix = position >= 0 ? suggested.substring(position) : "";
        final Utilities.CallbackReturn<Integer, String> relativePart = index -> prefix + " (" + index + ")" + postfix;

        int index = 0;
        String result;
        do {
            result = relativePart.run(++index);
        } while (new File(folder + result).exists());
        return result;
    }

    public long size() {
        return _offset;
    }

    public boolean empty() {
        return _offset == 0;
    }

    public AbstractWriter.Result writeBlock(String block) {
        AbstractWriter.Result result = writeBlockAttempt(block);
        if (!result.isSuccess()) {
            throw new IllegalStateException("result is not success for block: " + block);
        }
        return result;
    }

    public AbstractWriter.Result writeBlock(NativeByteBuffer block) {
        AbstractWriter.Result result = writeBlockAttempt(block);
        if (!result.isSuccess()) {
            throw new IllegalStateException("result is not success for block: " + block);
        }
        return result;
    }

    public AbstractWriter.Result writeBlock(byte[] block) {
        AbstractWriter.Result result = writeBlockAttempt(block);
        if (!result.isSuccess()) {
            throw new IllegalStateException("result is not success for block: " + block);
        }
        return result;
    }

    private void addToStats() {
        if (_stats != null && !_inStats) {
            _inStats = true;
            _stats.incrementFiles();
        }
    }

    public AbstractWriter.Result writeBlockAttempt(String block) {
        addToStats();
        final int size = block.length();
        if (size == 0) {
            Log.e("exteraGram", "size of block to write was zero!");
            return AbstractWriter.Result.Success();
        }
        FileOutputStream stream = null;
        try {
            stream = new FileOutputStream(_file, true);
            stream.write(block.getBytes());
            _offset += size;
            if (_stats != null) {
                _stats.incrementBytes(size);
            }
        } catch (Exception e) {
            FileLog.e(e);
            return AbstractWriter.Result.Error();
        } finally {
            closeStream(stream);
        }
        return AbstractWriter.Result.Success();
    }

    public AbstractWriter.Result writeBlockAttempt(byte[] block) {
        addToStats();
        final int size = block.length;
        if (size == 0) {
            Log.e("exteraGram", "size of block to write was zero!");
            return AbstractWriter.Result.Success();
        }
        FileOutputStream stream = null;
        try {
            stream = new FileOutputStream(_file, true);
            stream.write(block);
            _offset += size;
            if (_stats != null) {
                _stats.incrementBytes(size);
            }
        } catch (Exception e) {
            FileLog.e(e);
            return AbstractWriter.Result.Error();
        } finally {
            closeStream(stream);
        }
        return AbstractWriter.Result.Success();
    }

    public AbstractWriter.Result writeBlockAttempt(NativeByteBuffer block) {
        addToStats();
        final int size = block.buffer.limit();
        if (size == 0) {
            Log.e("exteraGram", "size of block to write was zero!");
            return AbstractWriter.Result.Success();
        }
        RandomAccessFile file = null;
        try {
            file = new RandomAccessFile(_file, "rws");
            file.seek(file.length());
        } catch (IOException e) {
            FileLog.e(e);
        }
        try {
            try {
                FileChannel channel = file.getChannel();
                channel.write(block.buffer);
                _offset += size;
                if (_stats != null) {
                    _stats.incrementBytes(size);
                }
                channel.close();
                return AbstractWriter.Result.Success();
            } catch (Exception e) {
                FileLog.e(e);
                return AbstractWriter.Result.Error();
            } finally {
                file.close();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void closeStream(FileOutputStream stream) {
        if (stream == null) {
            return;
        }
        try {
            stream.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static class Stats {
        private final AtomicInteger _files = new AtomicInteger(0);
        private final AtomicLong _bytes = new AtomicLong(0);

        public void incrementFiles() {
            _files.getAndIncrement();
        }

        public void incrementBytes(int count) {
            _bytes.addAndGet(count);
        }

        public int filesCount() {
            return _files.get();
        }

        public long bytesCount() {
            return _bytes.get();
        }
    }
}
