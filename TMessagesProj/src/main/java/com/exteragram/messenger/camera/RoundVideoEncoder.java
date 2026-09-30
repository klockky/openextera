package com.exteragram.messenger.camera;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTimestamp;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLExt;
import android.opengl.EGLSurface;
import android.opengl.GLUtils;
import android.os.SystemClock;
import android.view.Surface;

import com.exteragram.messenger.utils.system.SystemUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.video.MP4Builder;
import org.telegram.messenger.video.MediaCodecVideoConvertor;
import org.telegram.messenger.video.Mp4Movie;
import org.telegram.ui.Components.PermissionRequest;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class RoundVideoEncoder {

    private static final int STATE_IDLE = 0;
    private static final int STATE_STARTING = 1;
    private static final int STATE_RECORDING = 2;
    private static final int STATE_PAUSING = 3;
    private static final int STATE_PAUSED = 4;
    private static final int STATE_FINISHING = 5;
    private static final int STATE_FINISHED = 6;
    private static final int STATE_FAILED = 7;

    private static final int DEFERRED_NONE = 0;
    private static final int DEFERRED_STOP = 1;
    private static final int DEFERRED_CANCEL = 2;

    private static final int CLEANUP_NONE = 0;
    private static final int CLEANUP_CANCEL = 1;
    private static final int CLEANUP_FAILURE = 2;

    private static final int NO_TRACK = -5;

    private static final int SAMPLE_RATE = 48000;
    private static final int AUDIO_BUFFER_SIZE = 2048;
    private static final int AUDIO_CHUNKS_PER_BATCH = 10;
    private static final int AUDIO_BATCH_POOL_SIZE = 25;
    private static final long AAC_FRAME_DURATION_US = 21333;

    private static final long NANOS_PER_SECOND = 1_000_000_000L;
    private static final long MICROS_PER_SECOND = 1_000_000L;
    private static final long SEGMENT_WARMUP_NS = 200_000_000L;
    private static final long DRAIN_TIMEOUT_US = 10_000L;

    private static final int EGL_RECORDABLE_ANDROID = 0x3142;

    private final Renderer renderer;
    private final Callback callback;
    private final boolean isSecretChat;
    private final DispatchQueue encoderQueue;

    private final AtomicBoolean finishRequested = new AtomicBoolean(false);
    private volatile boolean started;
    private volatile int state = STATE_IDLE;

    private File videoFile;
    private File fileToWrite;
    private File pausePreviewFile;
    private boolean writingToDifferentFile;
    private boolean allowSendingWhileRecording;

    private int videoWidth;
    private int videoHeight;
    private int videoBitrate;
    private int frameRate = 30;
    private long maxDurationUs;

    private MediaCodec videoEncoder;
    private MediaCodec audioEncoder;
    private MediaCodec.BufferInfo videoBufferInfo;
    private MediaCodec.BufferInfo audioBufferInfo;
    private Surface inputSurface;
    private MP4Builder mediaMuxer;
    private int videoTrackIndex = NO_TRACK;
    private int audioTrackIndex = NO_TRACK;
    private int prependHeaderSize;
    private boolean firstEncode;
    private boolean videoEosSignalled;
    private boolean videoEosSeen;
    private boolean audioEosQueued;
    private boolean audioEosSeen;

    private EGLDisplay eglDisplay = EGL14.EGL_NO_DISPLAY;
    private EGLContext eglContext = EGL14.EGL_NO_CONTEXT;
    private EGLSurface eglSurface = EGL14.EGL_NO_SURFACE;
    private EGLConfig eglConfig;
    private EGLContext pendingResumeContext;

    private final Object pendingFrameLock = new Object();
    private final FrameSnapshot pendingFrame = new FrameSnapshot();
    private final FrameSnapshot currentFrame = new FrameSnapshot();
    private boolean pendingFrameSet;

    private int lastCameraId = Integer.MIN_VALUE;
    private boolean sourceAnchorSet;
    private long sourceAnchorSourceNs;
    private long sourceAnchorMonotonicNs;
    private long lastSourceTimestampNs;
    private long segmentFirstArrivalNs = -1;
    private long segmentVideoOriginNs = -1;
    private long segmentActiveBaseNs;
    private long lastVideoFrameIndex = -1;
    private long lastVideoActiveTimeNs;
    private long minVideoFrameDeltaNs;
    private long lastSubmittedVideoPtsUs = -1;
    private long lastMuxedVideoPtsUs = -1;

    private final Object audioCaptureLock = new Object();
    private AudioCaptureSession activeAudioCapture;
    private boolean audioCaptureRunning;
    private int audioCaptureGeneration;
    private final ArrayBlockingQueue<AudioChunkBatch> audioBatchPool = new ArrayBlockingQueue<>(AUDIO_BATCH_POOL_SIZE);
    private boolean audioBatchesPrepared;
    private final ArrayList<AudioChunkBatch> pendingAudio = new ArrayList<>();
    private long audioSegmentBaseUs = -1;
    private long audioSegmentFramesSubmitted;
    private long audioTotalEndUs;
    private long audioCapUs;
    private long lastSubmittedAudioEndUs = -1;
    private long lastMuxedAudioPtsUs = -1;
    private boolean waitingAudioTail;

    private int deferredFinish = DEFERRED_NONE;
    private int deferredAudioCleanup = CLEANUP_NONE;

    public interface Callback {
        void onAudioAmplitude(double amplitude);

        void onFinished(FinishReason reason);

        void onPaused(File previewFile);

        void onRecordingStarted(boolean resumed);

        void onWriteData(long availableSize);
    }

    public enum FinishReason {
        COMPLETED,
        CANCELLED,
        FAILED
    }

    public interface Renderer {
        boolean onDrawEncoderFrame(long frameDeltaNs, FrameSnapshot frame);

        void onEncoderSurfaceCreated(int width, int height);

        void onEncoderSurfaceDestroyed();
    }

    public static final class FrameSnapshot {
        public long sourceTimestampNs;
        public long arrivalTimeNs;
        public int cameraId;
        public int surfaceIndex;
        public int textureId;
        public int previewWidth;
        public int previewHeight;
        public final float[] stMatrix = new float[16];
        public final float[] mvpMatrix = new float[16];
        public final float[] textureCoords = new float[8];

        public void copyFrom(FrameSnapshot other) {
            sourceTimestampNs = other.sourceTimestampNs;
            arrivalTimeNs = other.arrivalTimeNs;
            cameraId = other.cameraId;
            surfaceIndex = other.surfaceIndex;
            textureId = other.textureId;
            System.arraycopy(other.stMatrix, 0, stMatrix, 0, 16);
            System.arraycopy(other.mvpMatrix, 0, mvpMatrix, 0, 16);
            System.arraycopy(other.textureCoords, 0, textureCoords, 0, 8);
            previewWidth = other.previewWidth;
            previewHeight = other.previewHeight;
        }
    }

    public class AudioChunkBatch {
        public int drained;
        public int results;
        public final byte[][] data = new byte[AUDIO_CHUNKS_PER_BATCH][];
        public final ByteBuffer[] buffer = new ByteBuffer[AUDIO_CHUNKS_PER_BATCH];
        public final long[] startTimeNs = new long[AUDIO_CHUNKS_PER_BATCH];
        public final Runnable deliveryRunnable = () -> handleAudioBatch(this);

        public AudioChunkBatch() {
            for (int i = 0; i < AUDIO_CHUNKS_PER_BATCH; i++) {
                data[i] = new byte[AUDIO_BUFFER_SIZE];
                buffer[i] = ByteBuffer.wrap(data[i]).order(ByteOrder.nativeOrder());
            }
        }
    }

    public class AudioCaptureSession {
        public final AudioRecord audioRecorder;
        public final int generation;
        public boolean recorderReleased;
        public Thread thread;
        public final AtomicBoolean stopRequested = new AtomicBoolean(false);
        public final Object recorderLock = new Object();
        public final Runnable stopRunnable = this::stopRecorder;
        public final Runnable completionRunnable = () -> handleAudioCaptureFinished(this);

        public AudioCaptureSession(AudioRecord audioRecorder, int generation) {
            this.audioRecorder = audioRecorder;
            this.generation = generation;
        }

        public void requestStop() {
            boolean firstRequest = stopRequested.compareAndSet(false, true);
            Thread captureThread = thread;
            if (captureThread != null && captureThread != Thread.currentThread()) {
                captureThread.interrupt();
            }
            if (firstRequest && !Utilities.globalQueue.postRunnable(stopRunnable)) {
                FileLog.e("RoundVideoEncoder unable to schedule AudioRecord stop");
            }
        }

        private void stopRecorder() {
            synchronized (recorderLock) {
                if (!recorderReleased) {
                    stopAudioRecorder(audioRecorder);
                }
            }
        }

        public void releaseRecorder() {
            synchronized (recorderLock) {
                if (recorderReleased) {
                    return;
                }
                recorderReleased = true;
                releaseAudioRecorder(audioRecorder);
            }
        }
    }

    public RoundVideoEncoder(Renderer renderer, Callback callback, boolean isSecretChat) {
        this.renderer = renderer;
        this.callback = callback;
        this.isSecretChat = isSecretChat;
        encoderQueue = new DispatchQueue("RoundVideoEncoder", false);
        encoderQueue.setPriority(Thread.MAX_PRIORITY);
        encoderQueue.start();
    }

    public boolean isStarted() {
        return started;
    }

    public void startRecording(File file, EGLContext sharedContext, int requestedFrameRate) {
        started = true;
        encoderQueue.postRunnable(() -> handleStart(file, sharedContext, requestedFrameRate));
    }

    public void frameAvailable(FrameSnapshot frame) {
        if (!started) {
            return;
        }
        synchronized (pendingFrameLock) {
            pendingFrame.copyFrom(frame);
            if (pendingFrameSet) {
                return;
            }
            pendingFrameSet = true;
            encoderQueue.postRunnable(this::handleFrame);
        }
    }

    public void pause(File previewFile) {
        AudioCaptureSession capture = getActiveAudioCapture();
        encoderQueue.postRunnable(() -> handlePause(previewFile));
        requestAudioCaptureStop(capture);
    }

    public void stop() {
        if (finishRequested.compareAndSet(false, true)) {
            AudioCaptureSession capture = getActiveAudioCapture();
            encoderQueue.postRunnable(() -> handleFinish(false));
            requestAudioCaptureStop(capture);
        }
    }

    public void cancel() {
        if (finishRequested.compareAndSet(false, true)) {
            AudioCaptureSession capture = getActiveAudioCapture();
            encoderQueue.postRunnable(() -> handleFinish(true));
            requestAudioCaptureStop(capture);
        }
    }

    private void handleStart(File file, EGLContext sharedContext, int requestedFrameRate) {
        if (state == STATE_PAUSED) {
            handleResume(sharedContext);
            return;
        }
        if (state == STATE_PAUSING) {
            pendingResumeContext = sharedContext;
            return;
        }
        if (state != STATE_IDLE) {
            return;
        }
        state = STATE_STARTING;
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("RoundVideoEncoder start " + file);
        }
        try {
            videoFile = file;
            int resolution = SystemUtils.getRoundVideoResolution();
            videoWidth = resolution;
            videoHeight = resolution;
            videoBitrate = SystemUtils.getRoundVideoBitrate() * 1024;
            frameRate = requestedFrameRate == 60 ? 60 : 30;
            maxDurationUs = SystemUtils.getRoundVideoMaxDurationMs() * 1000;
            audioCapUs = maxDurationUs;
            allowSendingWhileRecording = SharedConfig.deviceIsHigh();
            prepareAudioBatches();

            videoBufferInfo = new MediaCodec.BufferInfo();
            audioBufferInfo = new MediaCodec.BufferInfo();

            MediaFormat audioFormat = new MediaFormat();
            audioFormat.setString(MediaFormat.KEY_MIME, MediaController.AUDIO_MIME_TYPE);
            audioFormat.setInteger(MediaFormat.KEY_SAMPLE_RATE, SAMPLE_RATE);
            audioFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1);
            audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, SystemUtils.getRoundAudioBitrate() * 1024);
            audioFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 20480);
            audioEncoder = MediaCodec.createEncoderByType(MediaController.AUDIO_MIME_TYPE);
            audioEncoder.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            audioEncoder.start();

            MediaFormat videoFormat = MediaFormat.createVideoFormat(MediaController.VIDEO_MIME_TYPE, videoWidth, videoHeight);
            videoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            videoFormat.setInteger(MediaFormat.KEY_BIT_RATE, videoBitrate);
            videoFormat.setInteger("max-bitrate", videoBitrate);
            videoEncoder = MediaCodec.createEncoderByType(MediaController.VIDEO_MIME_TYPE);
            try {
                if (videoEncoder.getCodecInfo().getCapabilitiesForType(MediaController.VIDEO_MIME_TYPE).getEncoderCapabilities().isBitrateModeSupported(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR)) {
                    videoFormat.setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR);
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
            videoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate);
            videoFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
            videoEncoder.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            inputSurface = videoEncoder.createInputSurface();
            videoEncoder.start();
            firstEncode = true;

            fileToWrite = videoFile;
            writingToDifferentFile = false;
            if (ImageLoader.isSdCardPath(videoFile)) {
                try {
                    fileToWrite = new File(ApplicationLoader.getFilesDirFixed(), "camera_tmp.mp4");
                    if (fileToWrite.exists()) {
                        fileToWrite.delete();
                    }
                    writingToDifferentFile = true;
                } catch (Throwable e) {
                    FileLog.e(e);
                    fileToWrite = videoFile;
                    writingToDifferentFile = false;
                }
            }

            Mp4Movie movie = new Mp4Movie();
            movie.setCacheFile(fileToWrite);
            movie.setRotation(0);
            movie.setSize(videoWidth, videoHeight);
            mediaMuxer = new MP4Builder().createMovie(movie, isSecretChat, false);
            mediaMuxer.setAllowSyncFiles(false);

            createEncoderEgl(sharedContext);
            startAudioCapture();
            state = STATE_RECORDING;
            AndroidUtilities.runOnUIThread(() -> callback.onRecordingStarted(false));
        } catch (Throwable e) {
            FileLog.e(e);
            fail();
        }
    }

    private void handleResume(EGLContext sharedContext) {
        if (state != STATE_PAUSED) {
            return;
        }
        state = STATE_STARTING;
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("RoundVideoEncoder resume");
        }
        try {
            sourceAnchorSet = false;
            segmentFirstArrivalNs = -1;
            segmentVideoOriginNs = -1;
            audioSegmentBaseUs = -1;
            audioCapUs = maxDurationUs;
            createEncoderEgl(sharedContext);
            startAudioCapture();
            state = STATE_RECORDING;
            AndroidUtilities.runOnUIThread(() -> callback.onRecordingStarted(true));
        } catch (Throwable e) {
            FileLog.e(e);
            fail();
        }
    }

    private void handleFrame() {
        // Keep the lock for the whole frame, as exteraGram does: the snapshot references the camera's live
        // texture, and holding the lock while the encoder samples it makes the camera thread wait in
        // frameAvailable() instead of updating the texture mid-draw (that race produced white tiles in videos).
        synchronized (pendingFrameLock) {
            if (!pendingFrameSet) {
                return;
            }
            pendingFrameSet = false;
            currentFrame.copyFrom(pendingFrame);
            long sourceTimestampNs = currentFrame.sourceTimestampNs;
            long arrivalTimeNs = currentFrame.arrivalTimeNs;
            int cameraId = currentFrame.cameraId;
            if (state != STATE_RECORDING) {
                return;
            }
            try {
                drainEncoders();
                feedPendingAudio();

                boolean cameraChanged = cameraId != lastCameraId;
                lastCameraId = cameraId;
                boolean discontinuity = true;
                long frameTimeNs;
                if (sourceTimestampNs <= 0) {
                    sourceAnchorSet = false;
                    frameTimeNs = arrivalTimeNs;
                } else {
                    if (!sourceAnchorSet || cameraChanged || sourceTimestampNs <= lastSourceTimestampNs || sourceTimestampNs - lastSourceTimestampNs > NANOS_PER_SECOND) {
                        sourceAnchorSourceNs = sourceTimestampNs;
                        sourceAnchorMonotonicNs = arrivalTimeNs;
                        sourceAnchorSet = true;
                        frameTimeNs = arrivalTimeNs;
                    } else {
                        frameTimeNs = sourceAnchorMonotonicNs + (sourceTimestampNs - sourceAnchorSourceNs);
                        discontinuity = false;
                    }
                    lastSourceTimestampNs = sourceTimestampNs;
                }

                if (segmentFirstArrivalNs == -1) {
                    segmentFirstArrivalNs = arrivalTimeNs;
                }
                if (arrivalTimeNs - segmentFirstArrivalNs < SEGMENT_WARMUP_NS) {
                    return;
                }

                if (segmentVideoOriginNs != -1) {
                    long activeTimeNs = segmentActiveBaseNs + (frameTimeNs - segmentVideoOriginNs);
                    long frameIndex = frameRate * activeTimeNs / NANOS_PER_SECOND;
                    if (frameIndex > lastVideoFrameIndex && activeTimeNs / 1000 < maxDurationUs) {
                        acceptFrame(frameIndex, activeTimeNs, discontinuity ? 0 : activeTimeNs - lastVideoActiveTimeNs);
                    }
                    return;
                }

                long activeTimeNs = lastVideoFrameIndex < 0 ? 0 : lastVideoActiveTimeNs + videoFallbackFrameDurationNs();
                if (activeTimeNs / 1000 >= maxDurationUs) {
                    return;
                }
                long frameIndex = frameRate * activeTimeNs / NANOS_PER_SECOND;
                segmentVideoOriginNs = frameTimeNs;
                segmentActiveBaseNs = activeTimeNs;
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d("RoundVideoEncoder segment origin at " + activeTimeNs + "ns slot " + frameIndex);
                }
                if (acceptFrame(frameIndex, activeTimeNs, 0)) {
                    feedPendingAudio();
                }
            } catch (Exception e) {
                FileLog.e(e);
                fail();
            }
        }
    }

    private boolean acceptFrame(long frameIndex, long activeTimeNs, long frameDeltaNs) {
        if (!makeEglCurrent()) {
            fail();
            return false;
        }
        try {
            if (!renderer.onDrawEncoderFrame(frameDeltaNs, currentFrame)) {
                return false;
            }
            EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, activeTimeNs);
            if (!EGL14.eglSwapBuffers(eglDisplay, eglSurface)) {
                FileLog.e("RoundVideoEncoder eglSwapBuffers failed at frame " + frameIndex + ": " + GLUtils.getEGLErrorString(EGL14.eglGetError()));
                fail();
                return false;
            }
            if (lastVideoFrameIndex >= 0 && frameDeltaNs > 0) {
                if (minVideoFrameDeltaNs == 0 || frameDeltaNs < minVideoFrameDeltaNs) {
                    minVideoFrameDeltaNs = frameDeltaNs;
                }
            }
            lastVideoFrameIndex = frameIndex;
            lastVideoActiveTimeNs = activeTimeNs;
            lastSubmittedVideoPtsUs = activeTimeNs / 1000;
            return true;
        } catch (Throwable e) {
            FileLog.e(e);
            fail();
            return false;
        }
    }

    private void handleAudioBatch(AudioChunkBatch batch) {
        if (state == STATE_RECORDING || state == STATE_PAUSING || (state == STATE_FINISHING && waitingAudioTail)) {
            pendingAudio.add(batch);
            if (segmentVideoOriginNs == -1 && pendingAudio.size() > AUDIO_BATCH_POOL_SIZE - 1) {
                recycleAudioBatch(pendingAudio.remove(0));
            }
            try {
                drainEncoders();
                feedPendingAudio();
            } catch (Exception e) {
                FileLog.e(e);
                fail();
            }
            return;
        }
        recycleAudioBatch(batch);
    }

    private void handleAudioCaptureFinished(AudioCaptureSession session) {
        if (session.generation != audioCaptureGeneration) {
            return;
        }
        audioCaptureRunning = false;
        if (deferredAudioCleanup != CLEANUP_NONE) {
            int cleanup = deferredAudioCleanup;
            deferredAudioCleanup = CLEANUP_NONE;
            if (cleanup == CLEANUP_CANCEL) {
                finalizeCancel();
            } else {
                finalizeFailure();
            }
            return;
        }
        if (state == STATE_PAUSING) {
            finishPause();
        } else if (state == STATE_FINISHING && waitingAudioTail) {
            waitingAudioTail = false;
            finalizeStop();
        } else if (state == STATE_RECORDING) {
            FileLog.e("RoundVideoEncoder audio capture ended unexpectedly");
            fail();
        }
    }

    private void alignAudioSegment() {
        while (!pendingAudio.isEmpty()) {
            AudioChunkBatch batch = pendingAudio.get(0);
            while (batch.drained < batch.results) {
                ByteBuffer chunk = batch.buffer[batch.drained];
                int samples = chunk.remaining() / 2;
                if (samples > 0) {
                    long startNs = batch.startTimeNs[batch.drained];
                    long endNs = startNs + samples * NANOS_PER_SECOND / SAMPLE_RATE;
                    if (endNs > segmentVideoOriginNs) {
                        if (startNs < segmentVideoOriginNs) {
                            int skipSamples = (int) ((segmentVideoOriginNs - startNs) * SAMPLE_RATE / NANOS_PER_SECOND);
                            if (skipSamples < samples) {
                                chunk.position(chunk.position() + skipSamples * 2);
                                startNs += skipSamples * NANOS_PER_SECOND / SAMPLE_RATE;
                            }
                        }
                        audioSegmentBaseUs = Math.max((segmentActiveBaseNs + (startNs - segmentVideoOriginNs)) / 1000, audioTotalEndUs);
                        audioSegmentFramesSubmitted = 0;
                        if (BuildVars.LOGS_ENABLED) {
                            FileLog.d("RoundVideoEncoder audio segment base " + audioSegmentBaseUs + "us");
                        }
                        return;
                    }
                }
                batch.drained++;
            }
            pendingAudio.remove(0);
            recycleAudioBatch(batch);
        }
    }

    private void feedPendingAudio() {
        if (audioEncoder == null || audioEosQueued || segmentVideoOriginNs == -1) {
            return;
        }
        if (audioSegmentBaseUs == -1) {
            alignAudioSegment();
            if (audioSegmentBaseUs == -1) {
                return;
            }
        }
        while (hasFeedablePendingAudio()) {
            long framesLeft = (audioCapUs - audioSegmentBaseUs) * SAMPLE_RATE / MICROS_PER_SECOND - audioSegmentFramesSubmitted;
            if (framesLeft <= 0) {
                recyclePendingAudio();
                return;
            }
            try {
                int inputIndex = audioEncoder.dequeueInputBuffer(0);
                if (inputIndex < 0) {
                    return;
                }
                ByteBuffer inputBuffer = audioEncoder.getInputBuffer(inputIndex);
                if (inputBuffer == null) {
                    FileLog.e("RoundVideoEncoder audio input buffer was null");
                    failIfActive();
                    return;
                }
                inputBuffer.clear();
                long presentationTimeUs = audioSegmentBaseUs + audioSegmentFramesSubmitted * MICROS_PER_SECOND / SAMPLE_RATE;
                boolean capReached = false;
                int bytesQueued = 0;
                while (!pendingAudio.isEmpty() && !capReached) {
                    AudioChunkBatch batch = pendingAudio.get(0);
                    boolean inputFull = false;
                    while (batch.drained < batch.results) {
                        ByteBuffer chunk = batch.buffer[batch.drained];
                        int remaining = chunk.remaining();
                        if (remaining > 0) {
                            if (remaining / 2 > framesLeft) {
                                chunk.limit(chunk.position() + (int) framesLeft * 2);
                                remaining = chunk.remaining();
                                capReached = true;
                                if (remaining <= 0) {
                                    break;
                                }
                            }
                            if (inputBuffer.remaining() < remaining) {
                                inputFull = true;
                                break;
                            }
                            inputBuffer.put(chunk);
                            long frames = remaining / 2;
                            audioSegmentFramesSubmitted += frames;
                            framesLeft -= frames;
                            bytesQueued += remaining;
                        }
                        batch.drained++;
                        if (capReached) {
                            break;
                        }
                    }
                    if (inputFull) {
                        break;
                    }
                    if (batch.drained >= batch.results) {
                        pendingAudio.remove(0);
                        recycleAudioBatch(batch);
                    }
                    if (capReached) {
                        recyclePendingAudio();
                    }
                }
                audioTotalEndUs = audioSegmentBaseUs + audioSegmentFramesSubmitted * MICROS_PER_SECOND / SAMPLE_RATE;
                if (bytesQueued > 0) {
                    audioEncoder.queueInputBuffer(inputIndex, 0, bytesQueued, presentationTimeUs, 0);
                    lastSubmittedAudioEndUs = audioTotalEndUs;
                } else {
                    audioEncoder.queueInputBuffer(inputIndex, 0, 0, presentationTimeUs, 0);
                    return;
                }
            } catch (Exception e) {
                FileLog.e(e);
                failIfActive();
                return;
            }
        }
    }

    private void failIfActive() {
        if (state == STATE_STARTING || state == STATE_RECORDING || state == STATE_PAUSING || state == STATE_PAUSED) {
            fail();
        }
    }

    private boolean hasFeedablePendingAudio() {
        while (!pendingAudio.isEmpty()) {
            AudioChunkBatch batch = pendingAudio.get(0);
            for (int i = batch.drained; i < batch.results; i++) {
                if (batch.buffer[i].remaining() > 0) {
                    return true;
                }
            }
            pendingAudio.remove(0);
            recycleAudioBatch(batch);
        }
        return false;
    }

    private void handlePause(File previewFile) {
        if (state != STATE_RECORDING) {
            return;
        }
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("RoundVideoEncoder pause");
        }
        state = STATE_PAUSING;
        pausePreviewFile = previewFile;
        audioCapUs = Math.min(audioCapUs, videoEndTimeUs());
        if (audioCaptureRunning) {
            stopAudioCapture(false);
        } else {
            finishPause();
        }
    }

    private void finishPause() {
        feedPendingAudio();
        try {
            long deadline = SystemClock.elapsedRealtime() + 500;
            while (SystemClock.elapsedRealtime() < deadline) {
                drainVideoOnce(DRAIN_TIMEOUT_US);
                drainAudioOnce(DRAIN_TIMEOUT_US);
                feedPendingAudio();
                if (!hasFeedablePendingAudio() && hasReachedPauseTargets()) {
                    break;
                }
            }
            recyclePendingAudio();
            if (mediaMuxer != null && pausePreviewFile != null) {
                try {
                    mediaMuxer.setAllowSyncFiles(allowSendingWhileRecording);
                    mediaMuxer.finishMovie(pausePreviewFile);
                    mediaMuxer.setAllowSyncFiles(false);
                } catch (Exception e) {
                    FileLog.e(e);
                    fail();
                    return;
                }
            }
            releaseEgl();
            File previewFile = pausePreviewFile;
            pausePreviewFile = null;
            state = STATE_PAUSED;
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("RoundVideoEncoder paused, video end " + videoEndTimeUs() + "us audio end " + audioTotalEndUs + "us");
            }
            if (deferredFinish != DEFERRED_NONE) {
                boolean cancel = deferredFinish == DEFERRED_CANCEL;
                deferredFinish = DEFERRED_NONE;
                pendingResumeContext = null;
                handleFinish(cancel);
                return;
            }
            if (pendingResumeContext != null) {
                EGLContext resumeContext = pendingResumeContext;
                pendingResumeContext = null;
                handleResume(resumeContext);
            } else {
                AndroidUtilities.runOnUIThread(() -> callback.onPaused(previewFile));
            }
        } catch (Exception e) {
            FileLog.e(e);
            fail();
        }
    }

    private void handleFinish(boolean cancel) {
        if (state == STATE_FINISHED || state == STATE_FAILED || state == STATE_FINISHING) {
            return;
        }
        if (state == STATE_PAUSING) {
            deferredFinish = cancel ? DEFERRED_CANCEL : DEFERRED_STOP;
            return;
        }
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("RoundVideoEncoder finish cancel=" + cancel + " state=" + state);
        }
        if (state == STATE_IDLE) {
            state = STATE_FINISHED;
            scheduleQueueRecycle();
            FinishReason reason = cancel ? FinishReason.CANCELLED : FinishReason.COMPLETED;
            AndroidUtilities.runOnUIThread(() -> callback.onFinished(reason));
            return;
        }
        state = STATE_FINISHING;
        if (cancel) {
            if (stopAudioCapture(true)) {
                finalizeCancel();
            } else {
                deferredAudioCleanup = CLEANUP_CANCEL;
            }
            return;
        }
        audioCapUs = Math.min(audioCapUs, videoEndTimeUs());
        if (videoEncoder != null && !videoEosSignalled) {
            try {
                videoEncoder.signalEndOfInputStream();
                videoEosSignalled = true;
            } catch (Exception e) {
                FileLog.e(e);
                videoEosSeen = true;
            }
        }
        if (audioCaptureRunning) {
            stopAudioCapture(false);
            waitingAudioTail = true;
        } else {
            finalizeStop();
        }
    }

    private void finalizeStop() {
        if (segmentVideoOriginNs == -1 && audioSegmentBaseUs == -1) {
            recyclePendingAudio();
        }
        boolean success = drainToEndOfStream();
        releaseEgl();
        releaseCodecs();
        if (mediaMuxer != null) {
            try {
                mediaMuxer.setAllowSyncFiles(allowSendingWhileRecording);
                mediaMuxer.finishMovie();
            } catch (Exception e) {
                FileLog.e(e);
                success = false;
            }
            FileLog.d("RoundVideoEncoder finished muxer, video end " + videoEndTimeUs() + "us audio end " + audioTotalEndUs + "us");
            if (writingToDifferentFile) {
                if (videoFile.exists()) {
                    try {
                        videoFile.delete();
                    } catch (Exception e) {
                        FileLog.e("RoundVideoEncoder copying fileToWrite to videoFile, deleting videoFile error " + videoFile);
                        FileLog.e(e);
                    }
                }
                if (!fileToWrite.renameTo(videoFile)) {
                    FileLog.e("RoundVideoEncoder unable to rename file, try move file");
                    try {
                        if (AndroidUtilities.copyFile(fileToWrite, videoFile)) {
                            fileToWrite.delete();
                        } else {
                            FileLog.e("RoundVideoEncoder unable to copy file");
                            success = false;
                        }
                    } catch (IOException e) {
                        FileLog.e(e);
                        FileLog.e("RoundVideoEncoder unable to move file");
                    }
                }
            }
        }
        if (!success) {
            deleteQuietly(fileToWrite);
            if (videoFile != null && !videoFile.equals(fileToWrite)) {
                deleteQuietly(videoFile);
            }
        }
        releaseInputSurface();
        recyclePendingAudio();
        state = success ? STATE_FINISHED : STATE_FAILED;
        scheduleQueueRecycle();
        FinishReason reason = success ? FinishReason.COMPLETED : FinishReason.FAILED;
        AndroidUtilities.runOnUIThread(() -> callback.onFinished(reason));
    }

    private void finalizeCancel() {
        releaseAndDiscardOutput();
        state = STATE_FINISHED;
        scheduleQueueRecycle();
        AndroidUtilities.runOnUIThread(() -> callback.onFinished(FinishReason.CANCELLED));
    }

    private void fail() {
        if (state == STATE_FINISHED || state == STATE_FAILED || deferredAudioCleanup != CLEANUP_NONE) {
            return;
        }
        FileLog.e("RoundVideoEncoder failed in state " + state);
        state = STATE_FINISHING;
        if (stopAudioCapture(true)) {
            finalizeFailure();
        } else {
            deferredAudioCleanup = CLEANUP_FAILURE;
        }
    }

    private void finalizeFailure() {
        releaseAndDiscardOutput();
        state = STATE_FAILED;
        scheduleQueueRecycle();
        AndroidUtilities.runOnUIThread(() -> callback.onFinished(FinishReason.FAILED));
    }

    private void releaseAndDiscardOutput() {
        releaseEgl();
        releaseCodecs();
        releaseInputSurface();
        recyclePendingAudio();
        if (mediaMuxer != null) {
            try {
                mediaMuxer.finishMovie();
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        deleteQuietly(fileToWrite);
        deleteQuietly(videoFile);
    }

    private static void deleteQuietly(File file) {
        if (file == null) {
            return;
        }
        try {
            file.delete();
        } catch (Throwable ignore) {
        }
    }

    private boolean hasReachedPauseTargets() {
        boolean videoMuxed = lastSubmittedVideoPtsUs < 0 || lastMuxedVideoPtsUs >= lastSubmittedVideoPtsUs;
        long audioTargetUs = lastSubmittedAudioEndUs < 0 ? -1 : Math.max(0, lastSubmittedAudioEndUs - AAC_FRAME_DURATION_US);
        return videoMuxed && (audioTargetUs < 0 || lastMuxedAudioPtsUs >= audioTargetUs);
    }

    private void scheduleQueueRecycle() {
        DispatchQueue queue = encoderQueue;
        queue.postRunnable(queue::recycle);
    }

    private long videoFallbackFrameDurationNs() {
        if (minVideoFrameDeltaNs > 0) {
            return Math.max(minVideoFrameDeltaNs, 1_000_000L);
        }
        return NANOS_PER_SECOND / frameRate;
    }

    private long videoEndTimeUs() {
        if (lastVideoFrameIndex < 0) {
            return 0;
        }
        return (lastVideoActiveTimeNs + videoFallbackFrameDurationNs()) / 1000;
    }

    private boolean drainToEndOfStream() {
        long deadline = SystemClock.elapsedRealtime() + 5000;
        if (audioEncoder == null) {
            audioEosSeen = true;
        }
        if (videoEncoder == null || !videoEosSignalled) {
            videoEosSeen = true;
        }
        while (!(videoEosSeen && audioEosSeen) && SystemClock.elapsedRealtime() < deadline) {
            if (!audioEosQueued && !audioEosSeen) {
                feedPendingAudio();
                if (!hasFeedablePendingAudio()) {
                    queueAudioEndOfStream();
                }
            }
            try {
                if (!videoEosSeen) {
                    drainVideoOnce(DRAIN_TIMEOUT_US);
                }
                if (!audioEosSeen) {
                    drainAudioOnce(DRAIN_TIMEOUT_US);
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        if (!videoEosSeen || !audioEosSeen) {
            FileLog.e("RoundVideoEncoder end of stream drain timed out, video=" + videoEosSeen + " audio=" + audioEosSeen);
        }
        return videoEosSeen && audioEosSeen;
    }

    private void queueAudioEndOfStream() {
        try {
            int inputIndex = audioEncoder.dequeueInputBuffer(DRAIN_TIMEOUT_US);
            if (inputIndex >= 0) {
                audioEncoder.queueInputBuffer(inputIndex, 0, 0, audioTotalEndUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                audioEosQueued = true;
            }
        } catch (Exception e) {
            FileLog.e(e);
            audioEosSeen = true;
        }
    }

    private void drainEncoders() throws Exception {
        if (videoEncoder != null) {
            while (drainVideoOnce(0)) {
            }
        }
        if (audioEncoder != null) {
            while (drainAudioOnce(0)) {
            }
        }
    }

    private boolean drainVideoOnce(long timeoutUs) throws Exception {
        if (videoEncoder == null) {
            return false;
        }
        int outputIndex = videoEncoder.dequeueOutputBuffer(videoBufferInfo, timeoutUs);
        if (outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
            return false;
        }
        if (outputIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
            return true;
        }
        if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            MediaFormat outputFormat = videoEncoder.getOutputFormat();
            if (videoTrackIndex == NO_TRACK && mediaMuxer != null) {
                videoTrackIndex = mediaMuxer.addTrack(outputFormat, false);
                if (outputFormat.containsKey("prepend-sps-pps-to-idr-frames") && outputFormat.getInteger("prepend-sps-pps-to-idr-frames") == 1) {
                    ByteBuffer sps = outputFormat.getByteBuffer("csd-0");
                    ByteBuffer pps = outputFormat.getByteBuffer("csd-1");
                    prependHeaderSize = (sps == null ? 0 : sps.limit()) + (pps == null ? 0 : pps.limit());
                }
            }
            return true;
        }
        if (outputIndex < 0) {
            return false;
        }
        boolean endOfStream;
        try {
            ByteBuffer encodedData = videoEncoder.getOutputBuffer(outputIndex);
            if (encodedData == null) {
                throw new RuntimeException("videoEncoderOutputBuffer " + outputIndex + " was null");
            }
            MediaCodec.BufferInfo info = videoBufferInfo;
            if (info.size > 1) {
                if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                    if (prependHeaderSize != 0 && (info.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0) {
                        info.offset += prependHeaderSize;
                        info.size -= prependHeaderSize;
                    }
                    if (firstEncode && (info.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0) {
                        MediaCodecVideoConvertor.cutOfNalData(MediaController.VIDEO_MIME_TYPE, encodedData, info);
                        firstEncode = false;
                    }
                    if (mediaMuxer != null && videoTrackIndex >= 0) {
                        long availableSize = mediaMuxer.writeSampleData(videoTrackIndex, encodedData, info, true);
                        lastMuxedVideoPtsUs = Math.max(lastMuxedVideoPtsUs, info.presentationTimeUs);
                        if (availableSize != 0 && !writingToDifferentFile && allowSendingWhileRecording) {
                            callback.onWriteData(availableSize);
                        }
                    }
                } else if (videoTrackIndex == NO_TRACK && mediaMuxer != null) {
                    byte[] csd = new byte[info.size];
                    encodedData.limit(info.offset + info.size);
                    encodedData.position(info.offset);
                    encodedData.get(csd);
                    ByteBuffer sps = null;
                    ByteBuffer pps = null;
                    for (int i = info.size - 1; i > 3; i--) {
                        if (csd[i] == 1 && csd[i - 1] == 0 && csd[i - 2] == 0 && csd[i - 3] == 0) {
                            sps = ByteBuffer.allocate(i - 3);
                            pps = ByteBuffer.allocate(info.size - (i - 3));
                            sps.put(csd, 0, i - 3).position(0);
                            pps.put(csd, i - 3, info.size - (i - 3)).position(0);
                            break;
                        }
                    }
                    MediaFormat newFormat = MediaFormat.createVideoFormat(MediaController.VIDEO_MIME_TYPE, videoWidth, videoHeight);
                    if (sps != null) {
                        newFormat.setByteBuffer("csd-0", sps);
                        newFormat.setByteBuffer("csd-1", pps);
                    }
                    videoTrackIndex = mediaMuxer.addTrack(newFormat, false);
                }
            }
            endOfStream = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
        } finally {
            videoEncoder.releaseOutputBuffer(outputIndex, false);
        }
        if (endOfStream) {
            videoEosSeen = true;
            return false;
        }
        return true;
    }

    private boolean drainAudioOnce(long timeoutUs) throws Exception {
        if (audioEncoder == null) {
            return false;
        }
        int outputIndex = audioEncoder.dequeueOutputBuffer(audioBufferInfo, timeoutUs);
        if (outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
            return false;
        }
        if (outputIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
            return true;
        }
        if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            MediaFormat outputFormat = audioEncoder.getOutputFormat();
            if (audioTrackIndex == NO_TRACK && mediaMuxer != null) {
                audioTrackIndex = mediaMuxer.addTrack(outputFormat, true);
            }
            return true;
        }
        if (outputIndex < 0) {
            return false;
        }
        boolean endOfStream;
        try {
            ByteBuffer encodedData = audioEncoder.getOutputBuffer(outputIndex);
            if (encodedData == null) {
                throw new RuntimeException("audioEncoderOutputBuffer " + outputIndex + " was null");
            }
            MediaCodec.BufferInfo info = audioBufferInfo;
            if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                info.size = 0;
            }
            if (info.size != 0 && mediaMuxer != null && audioTrackIndex >= 0) {
                long availableSize = mediaMuxer.writeSampleData(audioTrackIndex, encodedData, info, false);
                lastMuxedAudioPtsUs = Math.max(lastMuxedAudioPtsUs, info.presentationTimeUs);
                if (availableSize != 0 && !writingToDifferentFile && allowSendingWhileRecording) {
                    callback.onWriteData(availableSize);
                }
            }
            endOfStream = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
        } finally {
            audioEncoder.releaseOutputBuffer(outputIndex, false);
        }
        if (endOfStream) {
            audioEosSeen = true;
            return false;
        }
        return true;
    }

    private void createEncoderEgl(EGLContext sharedContext) {
        if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
            throw new RuntimeException("EGL already set up");
        }
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        if (eglDisplay == EGL14.EGL_NO_DISPLAY) {
            throw new RuntimeException("unable to get EGL14 display");
        }
        int[] version = new int[2];
        if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
            eglDisplay = EGL14.EGL_NO_DISPLAY;
            throw new RuntimeException("unable to initialize EGL14");
        }

        int[] configAttributes = {
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL_RECORDABLE_ANDROID, 1,
            EGL14.EGL_NONE
        };
        EGLConfig[] configs = new EGLConfig[1];
        int[] numConfigs = new int[1];
        if (!EGL14.eglChooseConfig(eglDisplay, configAttributes, 0, configs, 0, configs.length, numConfigs, 0)) {
            throw new RuntimeException("Unable to find a suitable EGLConfig");
        }
        eglConfig = configs[0];

        int[] contextAttributes = {
            EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
            EGL14.EGL_NONE
        };
        eglContext = EGL14.eglCreateContext(eglDisplay, eglConfig, sharedContext, contextAttributes, 0);
        if (eglContext == null || eglContext == EGL14.EGL_NO_CONTEXT) {
            eglContext = EGL14.EGL_NO_CONTEXT;
            throw new RuntimeException("eglCreateContext failed " + GLUtils.getEGLErrorString(EGL14.eglGetError()));
        }

        int[] surfaceAttributes = {
            EGL14.EGL_NONE
        };
        eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, eglConfig, inputSurface, surfaceAttributes, 0);
        if (eglSurface == null || eglSurface == EGL14.EGL_NO_SURFACE) {
            eglSurface = EGL14.EGL_NO_SURFACE;
            throw new RuntimeException("eglCreateWindowSurface failed " + GLUtils.getEGLErrorString(EGL14.eglGetError()));
        }
        if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
            throw new RuntimeException("eglMakeCurrent failed " + GLUtils.getEGLErrorString(EGL14.eglGetError()));
        }
        try {
            renderer.onEncoderSurfaceCreated(videoWidth, videoHeight);
        } catch (Throwable e) {
            throw new RuntimeException("encoder renderer initialization failed", e);
        }
    }

    private boolean makeEglCurrent() {
        if (eglDisplay == EGL14.EGL_NO_DISPLAY || eglSurface == EGL14.EGL_NO_SURFACE) {
            return false;
        }
        if (eglContext.equals(EGL14.eglGetCurrentContext()) && eglSurface.equals(EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW))) {
            return true;
        }
        if (EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
            return true;
        }
        FileLog.e("RoundVideoEncoder eglMakeCurrent failed " + GLUtils.getEGLErrorString(EGL14.eglGetError()));
        return false;
    }

    private void releaseEgl() {
        if (eglDisplay == EGL14.EGL_NO_DISPLAY) {
            return;
        }
        if (makeEglCurrent()) {
            try {
                renderer.onEncoderSurfaceDestroyed();
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }
        if (eglSurface != EGL14.EGL_NO_SURFACE) {
            EGL14.eglDestroySurface(eglDisplay, eglSurface);
            eglSurface = EGL14.EGL_NO_SURFACE;
        }
        EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
        if (eglContext != EGL14.EGL_NO_CONTEXT) {
            EGL14.eglDestroyContext(eglDisplay, eglContext);
            eglContext = EGL14.EGL_NO_CONTEXT;
        }
        EGL14.eglReleaseThread();
        EGL14.eglTerminate(eglDisplay);
        eglDisplay = EGL14.EGL_NO_DISPLAY;
        eglConfig = null;
    }

    private void releaseInputSurface() {
        if (inputSurface != null) {
            try {
                inputSurface.release();
            } catch (Throwable e) {
                FileLog.e(e);
            }
            inputSurface = null;
        }
    }

    private void releaseCodecs() {
        if (videoEncoder != null) {
            try {
                videoEncoder.stop();
            } catch (Throwable e) {
                FileLog.e(e);
            }
            try {
                videoEncoder.release();
            } catch (Throwable e) {
                FileLog.e(e);
            }
            videoEncoder = null;
        }
        if (audioEncoder != null) {
            try {
                audioEncoder.stop();
            } catch (Throwable e) {
                FileLog.e(e);
            }
            try {
                audioEncoder.release();
            } catch (Throwable e) {
                FileLog.e(e);
            }
            audioEncoder = null;
            setBluetoothScoOn(false);
        }
    }

    private void recyclePendingAudio() {
        for (int i = 0; i < pendingAudio.size(); i++) {
            recycleAudioBatch(pendingAudio.get(i));
        }
        pendingAudio.clear();
    }

    private void prepareAudioBatches() {
        if (audioBatchesPrepared) {
            return;
        }
        for (int i = 0; i < AUDIO_BATCH_POOL_SIZE; i++) {
            audioBatchPool.add(new AudioChunkBatch());
        }
        audioBatchesPrepared = true;
    }

    private AudioChunkBatch obtainAudioBatch() throws InterruptedException {
        AudioChunkBatch batch = audioBatchPool.poll(250, TimeUnit.MILLISECONDS);
        if (batch == null) {
            return null;
        }
        batch.results = 0;
        batch.drained = 0;
        return batch;
    }

    private void recycleAudioBatch(AudioChunkBatch batch) {
        if (!audioBatchPool.offer(batch)) {
            FileLog.e("RoundVideoEncoder audio batch pool overflow");
        }
    }

    private void startAudioCapture() {
        setBluetoothScoOn(true);
        int minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (minBufferSize <= 0) {
            minBufferSize = 3584;
        }
        int bufferSize = 49152;
        if (bufferSize < minBufferSize) {
            bufferSize = ((minBufferSize / AUDIO_BUFFER_SIZE) + 1) * AUDIO_BUFFER_SIZE * 2;
        }
        AudioRecord audioRecord = new AudioRecord(MediaRecorder.AudioSource.DEFAULT, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);
        AudioCaptureSession session = null;
        try {
            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                throw new RuntimeException("AudioRecord init failed");
            }
            audioRecord.startRecording();
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("RoundVideoEncoder initied audio record with channels " + audioRecord.getChannelCount() + " sample rate = " + audioRecord.getSampleRate() + " bufferSize = " + bufferSize);
            }
            audioCaptureRunning = true;
            session = new AudioCaptureSession(audioRecord, ++audioCaptureGeneration);
            Thread thread = new Thread(new AudioCaptureRunnable(session), "RoundVideoAudioCapture");
            session.thread = thread;
            thread.setPriority(Thread.MAX_PRIORITY);
            synchronized (audioCaptureLock) {
                activeAudioCapture = session;
            }
            thread.start();
        } catch (Throwable e) {
            synchronized (audioCaptureLock) {
                if (activeAudioCapture == session) {
                    activeAudioCapture = null;
                }
            }
            audioCaptureRunning = false;
            if (session != null) {
                session.releaseRecorder();
            } else {
                releaseAudioRecorder(audioRecord);
            }
            throw e;
        }
    }

    private AudioCaptureSession getActiveAudioCapture() {
        synchronized (audioCaptureLock) {
            return activeAudioCapture;
        }
    }

    private void requestAudioCaptureStop(AudioCaptureSession session) {
        if (session != null) {
            session.requestStop();
        }
    }

    private boolean stopAudioCapture(boolean wait) {
        AudioCaptureSession session = getActiveAudioCapture();
        requestAudioCaptureStop(session);
        if (session == null) {
            return true;
        }
        Thread thread = session.thread;
        if (wait && thread != null && thread != Thread.currentThread()) {
            try {
                thread.join(1500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (thread.isAlive()) {
                FileLog.e("RoundVideoEncoder audio thread did not stop within timeout");
            }
        }
        return thread == null || !thread.isAlive();
    }

    private void stopAudioRecorder(AudioRecord audioRecord) {
        try {
            if (audioRecord.getRecordingState() != AudioRecord.RECORDSTATE_STOPPED) {
                audioRecord.stop();
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    private void releaseAudioRecorder(AudioRecord audioRecord) {
        stopAudioRecorder(audioRecord);
        try {
            audioRecord.release();
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    public class AudioCaptureRunnable implements Runnable {

        private final AudioTimestamp audioTimestamp = new AudioTimestamp();
        private final AudioCaptureSession session;

        public AudioCaptureRunnable(AudioCaptureSession session) {
            this.session = session;
        }

        @Override
        public void run() {
            long totalFrames = 0;
            boolean done = false;
            try {
                while (!done && !session.stopRequested.get()) {
                    AudioChunkBatch batch;
                    try {
                        batch = obtainAudioBatch();
                    } catch (InterruptedException e) {
                        if (session.stopRequested.get()) {
                            break;
                        }
                        continue;
                    }
                    if (batch == null) {
                        if (!session.stopRequested.get()) {
                            FileLog.e("RoundVideoEncoder audio batch pool stalled");
                        }
                        break;
                    }
                    try {
                        if (session.stopRequested.get()) {
                            recycleAudioBatch(batch);
                            done = true;
                            continue;
                        }
                        boolean hasTimestamp;
                        try {
                            hasTimestamp = session.audioRecorder.getTimestamp(audioTimestamp, AudioTimestamp.TIMEBASE_MONOTONIC) == AudioRecord.SUCCESS;
                        } catch (Exception e) {
                            hasTimestamp = false;
                        }
                        for (int i = 0; i < AUDIO_CHUNKS_PER_BATCH; ) {
                            ByteBuffer chunk = batch.buffer[i];
                            chunk.clear();
                            int read = session.audioRecorder.read(batch.data[i], 0, AUDIO_BUFFER_SIZE);
                            if (read <= 0) {
                                done = true;
                                break;
                            }
                            chunk.position(0);
                            chunk.limit(read);
                            if (i % 2 == 0) {
                                double sum = 0;
                                for (int j = 0; j < read / 2; j++) {
                                    short sample = chunk.getShort();
                                    sum += sample * sample;
                                }
                                chunk.position(0);
                                try {
                                    callback.onAudioAmplitude(Math.sqrt(sum / read / 2));
                                } catch (Throwable e) {
                                    FileLog.e(e);
                                }
                            }
                            long frames = read / 2;
                            long startTimeNs;
                            if (hasTimestamp) {
                                startTimeNs = audioTimestamp.nanoTime + (totalFrames - audioTimestamp.framePosition) * NANOS_PER_SECOND / SAMPLE_RATE;
                            } else {
                                startTimeNs = System.nanoTime() - frames * NANOS_PER_SECOND / SAMPLE_RATE;
                            }
                            batch.startTimeNs[i] = startTimeNs;
                            i++;
                            batch.results = i;
                            totalFrames += frames;
                            if (session.stopRequested.get()) {
                                done = true;
                                break;
                            }
                        }
                        if (batch.results <= 0 || !postBatch(batch)) {
                            recycleAudioBatch(batch);
                        }
                    } catch (Throwable e) {
                        recycleAudioBatch(batch);
                        throw e;
                    }
                }
            } catch (Throwable e) {
                FileLog.e(e);
            } finally {
                session.releaseRecorder();
                synchronized (audioCaptureLock) {
                    if (activeAudioCapture == session) {
                        activeAudioCapture = null;
                    }
                }
                if (!encoderQueue.getHandler().post(session.completionRunnable) && BuildVars.LOGS_ENABLED) {
                    FileLog.e("RoundVideoEncoder unable to post audio capture completion");
                }
            }
        }

        private boolean postBatch(AudioChunkBatch batch) {
            return encoderQueue.getHandler().post(batch.deliveryRunnable);
        }
    }

    private void setBluetoothScoOn(boolean scoOn) {
        AudioManager am = (AudioManager) ApplicationLoader.applicationContext.getSystemService(Context.AUDIO_SERVICE);
        if (SharedConfig.recordViaSco && !PermissionRequest.hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            SharedConfig.recordViaSco = false;
            SharedConfig.saveConfig();
        }
        if (am.isBluetoothScoAvailableOffCall() && SharedConfig.recordViaSco || !scoOn) {
            BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
            try {
                if (btAdapter != null && btAdapter.getProfileConnectionState(BluetoothProfile.HEADSET) == BluetoothProfile.STATE_CONNECTED || !scoOn) {
                    if (scoOn && !am.isBluetoothScoOn()) {
                        am.startBluetoothSco();
                    } else if (!scoOn && am.isBluetoothScoOn()) {
                        am.stopBluetoothSco();
                    }
                }
            } catch (SecurityException ignored) {
            } catch (Throwable e) {
                FileLog.e(e);
                try {
                    if (!scoOn && am.isBluetoothScoOn()) {
                        am.stopBluetoothSco();
                    }
                } catch (Exception e2) {
                    FileLog.e(e2);
                }
            }
        }
    }
}
