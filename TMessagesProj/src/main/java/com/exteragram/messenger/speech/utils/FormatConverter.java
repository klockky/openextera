package com.exteragram.messenger.speech.utils;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;

import org.telegram.messenger.FileLog;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

public final class FormatConverter {

    private static final int DEFAULT_SAMPLE_RATE = 48000;
    private static final long TIMEOUT_US = 10000;

    private FormatConverter() {
    }

    public static int getSampleRate(String path) {
        int sampleRate = -1;
        MediaExtractor extractor = new MediaExtractor();
        try {
            extractor.setDataSource(path);
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat format = extractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/") && format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                    sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    break;
                }
            }
        } catch (IOException e) {
            FileLog.e("Error detecting sample rate", e);
        } finally {
            extractor.release();
        }
        return sampleRate != -1 ? sampleRate : DEFAULT_SAMPLE_RATE;
    }

    public static InputStream extractAndConvertToPcm(String path, boolean limitToOneMinute) throws IOException {
        return new LazyPcmInputStream(path, limitToOneMinute);
    }

    public static class LazyPcmInputStream extends InputStream {

        private static final long ONE_MINUTE_US = 60_000_000L;

        private final MediaExtractor extractor;
        private final MediaCodec codec;
        private final boolean limitToOneMinute;
        private final MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        private final ByteBuffer[] inputBuffers;
        private ByteBuffer[] outputBuffers;
        private ByteBuffer currentOutputBuffer;
        private long totalDecodedDurationUs;
        private boolean isEOS;

        @SuppressWarnings("deprecation")
        public LazyPcmInputStream(String path, boolean limitToOneMinute) throws IOException {
            this.limitToOneMinute = limitToOneMinute;
            extractor = new MediaExtractor();
            extractor.setDataSource(path);
            MediaFormat format = extractor.getTrackFormat(0);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime == null || !mime.startsWith("audio/")) {
                throw new IOException("Not an audio file");
            }
            codec = MediaCodec.createDecoderByType(mime);
            codec.configure(format, null, null, 0);
            codec.start();
            inputBuffers = codec.getInputBuffers();
            outputBuffers = codec.getOutputBuffers();
            extractor.selectTrack(0);
        }

        @Override
        public int read() {
            byte[] buffer = new byte[1];
            if (read(buffer, 0, 1) == -1) {
                return -1;
            }
            return buffer[0] & 0xFF;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) {
            if (isEOS) {
                return -1;
            }
            int read = 0;
            while (read < length && !isEOS) {
                if (currentOutputBuffer == null || !currentOutputBuffer.hasRemaining()) {
                    currentOutputBuffer = getNextOutputBuffer();
                    if (currentOutputBuffer == null) {
                        break;
                    }
                }
                int count = Math.min(length - read, currentOutputBuffer.remaining());
                currentOutputBuffer.get(buffer, offset + read, count);
                read += count;
            }
            return read > 0 ? read : -1;
        }

        @SuppressWarnings("deprecation")
        private ByteBuffer getNextOutputBuffer() {
            while (!isEOS) {
                int inputIndex = codec.dequeueInputBuffer(TIMEOUT_US);
                if (inputIndex >= 0) {
                    int sampleSize = extractor.readSampleData(inputBuffers[inputIndex], 0);
                    if (sampleSize < 0) {
                        codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        isEOS = true;
                    } else {
                        codec.queueInputBuffer(inputIndex, 0, sampleSize, extractor.getSampleTime(), 0);
                        extractor.advance();
                    }
                }
                int outputIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US);
                if (outputIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    outputBuffers = codec.getOutputBuffers();
                } else if (outputIndex != MediaCodec.INFO_OUTPUT_FORMAT_CHANGED && outputIndex != MediaCodec.INFO_TRY_AGAIN_LATER) {
                    ByteBuffer output = outputBuffers[outputIndex];
                    ByteBuffer copy = ByteBuffer.allocate(bufferInfo.size);
                    copy.put(output);
                    copy.flip();
                    codec.releaseOutputBuffer(outputIndex, false);
                    totalDecodedDurationUs = bufferInfo.presentationTimeUs;
                    if (limitToOneMinute && totalDecodedDurationUs >= ONE_MINUTE_US) {
                        isEOS = true;
                    }
                    return copy;
                }
            }
            return null;
        }

        @Override
        public void close() throws IOException {
            codec.stop();
            codec.release();
            extractor.release();
            super.close();
        }
    }
}
