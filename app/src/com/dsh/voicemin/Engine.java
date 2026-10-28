package com.dsh.voicemin;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetFileDescriptor;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

import com.k2fsa.sherpa.onnx.FeatureConfig;
import com.k2fsa.sherpa.onnx.OfflineModelConfig;
import com.k2fsa.sherpa.onnx.OfflineRecognizer;
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig;
import com.k2fsa.sherpa.onnx.OfflineRecognizerResult;
import com.k2fsa.sherpa.onnx.OfflineSenseVoiceModelConfig;
import com.k2fsa.sherpa.onnx.OfflineStream;
import com.k2fsa.sherpa.onnx.SileroVadModelConfig;
import com.k2fsa.sherpa.onnx.SpeechSegment;
import com.k2fsa.sherpa.onnx.Vad;
import com.k2fsa.sherpa.onnx.VadModelConfig;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 录音/VAD/转写引擎。放在单例里，界面销毁后仍可继续工作，
 * 由 KeepAliveService 保活（前台服务 + 唤醒锁）。
 */
public final class Engine {
    public interface Listener {
        void onState(String state);

        void onLine(String text, long startMs);

        void onReset();

        void onDone(String transcript, int deleted);
    }

    public static final class Line {
        public final String text;
        public final long startMs;

        Line(String text, long startMs) {
            this.text = text;
            this.startMs = startMs;
        }
    }

    private static final Engine INSTANCE = new Engine();

    public static Engine get() {
        return INSTANCE;
    }

    private static final int SAMPLE_RATE = 16000;
    private static final int WINDOW = 512;
    private static final int MAX_RECORDINGS = 20;

    private Context ctx;
    private volatile Listener listener;
    private volatile String state = "loading";
    private volatile boolean initialized = false;
    private volatile boolean recording = false;
    private volatile boolean audioReady = false;

    private OfflineRecognizer recognizer;
    private Vad vad;
    private final float[] vadBuf = new float[WINDOW];
    private int vadFill = 0;

    private AudioRecord recorder;
    private File recDir;
    private File wavFile;
    private RandomAccessFile wavRaf;
    private long wavDataLen = 0;

    private final StringBuilder transcript = new StringBuilder();
    private final List<Line> lines = new ArrayList<>();
    private final Object lock = new Object();

    private final ExecutorService audioExec = Executors.newSingleThreadExecutor();
    private volatile ExecutorService asrExec;

    private Engine() {
    }

    public void attach(Listener l) {
        listener = l;
    }

    public void detach(Listener l) {
        if (listener == l) {
            listener = null;
        }
    }

    public String getState() {
        return state;
    }

    public boolean isRecording() {
        return recording;
    }

    public boolean isAudioReady() {
        return audioReady;
    }

    public File getWavFile() {
        return wavFile;
    }

    public String getTranscript() {
        synchronized (lock) {
            return transcript.toString().trim();
        }
    }

    public List<Line> getLines() {
        synchronized (lock) {
            return new ArrayList<>(lines);
        }
    }

    private void setState(String s) {
        state = s;
        Listener l = listener;
        if (l != null) {
            l.onState(s);
        }
    }

    public void init(Context c) {
        if (initialized) {
            return;
        }
        initialized = true;
        ctx = c.getApplicationContext();
        audioExec.execute(() -> {
            try {
                loadModel();
                setState("ready");
            } catch (Throwable t) {
                setState("error: " + t);
            }
        });
    }

    private void loadModel() throws IOException {
        File modelDir = new File(ctx.getFilesDir(), "model");
        if (!modelDir.exists() && !modelDir.mkdirs()) {
            throw new IOException("无法创建模型目录");
        }
        File modelFile = ensureAsset("model.int8.onnx", modelDir);
        File tokensFile = ensureAsset("tokens.txt", modelDir);
        File vadFile = ensureAsset("silero_vad.onnx", modelDir);

        FeatureConfig feat = new FeatureConfig();
        feat.setSampleRate(SAMPLE_RATE);
        feat.setFeatureDim(80);

        OfflineSenseVoiceModelConfig sv = new OfflineSenseVoiceModelConfig();
        sv.setModel(modelFile.getAbsolutePath());
        sv.setLanguage("auto");
        sv.setUseInverseTextNormalization(true);

        OfflineModelConfig mc = new OfflineModelConfig();
        mc.setSenseVoice(sv);
        mc.setTokens(tokensFile.getAbsolutePath());
        mc.setNumThreads(2);
        mc.setDebug(false);
        mc.setProvider("cpu");

        OfflineRecognizerConfig cfg = new OfflineRecognizerConfig();
        cfg.setFeatConfig(feat);
        cfg.setModelConfig(mc);
        recognizer = new OfflineRecognizer(null, cfg);

        SileroVadModelConfig silero = new SileroVadModelConfig();
        silero.setModel(vadFile.getAbsolutePath());
        silero.setThreshold(0.5f);
        silero.setMinSilenceDuration(0.4f);
        silero.setMinSpeechDuration(0.25f);
        silero.setWindowSize(WINDOW);
        silero.setMaxSpeechDuration(20f);

        VadModelConfig vadCfg = new VadModelConfig();
        vadCfg.setSileroVadModelConfig(silero);
        vadCfg.setSampleRate(SAMPLE_RATE);
        vadCfg.setNumThreads(1);
        vadCfg.setProvider("cpu");
        vadCfg.setDebug(false);
        vad = new Vad(null, vadCfg);
    }

    private File ensureAsset(String name, File dir) throws IOException {
        long assetSize;
        try (AssetFileDescriptor afd = ctx.getAssets().openFd(name)) {
            assetSize = afd.getLength();
        }
        File dst = new File(dir, name);
        if (dst.exists() && dst.length() == assetSize) {
            return dst;
        }
        File tmp = new File(dir, name + ".tmp");
        try (InputStream in = ctx.getAssets().open(name);
             FileOutputStream out = new FileOutputStream(tmp)) {
            byte[] buf = new byte[1024 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            out.flush();
        }
        if (dst.exists()) {
            dst.delete();
        }
        if (!tmp.renameTo(dst)) {
            throw new IOException("重命名失败: " + dst);
        }
        return dst;
    }

    public boolean startRecording() {
        if (recognizer == null || vad == null) {
            setState("error: 模型未就绪");
            return false;
        }
        try {
            synchronized (lock) {
                transcript.setLength(0);
                lines.clear();
            }
            Listener l = listener;
            if (l != null) {
                l.onReset();
            }

            SharedPreferences p = ctx.getSharedPreferences("voicemin", Context.MODE_PRIVATE);
            File saveDir = new File(LibraryStore.root(ctx), p.getString("save_folder", ""));
            saveDir.mkdirs();
            String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            recDir = new File(saveDir, "rec_" + ts);
            recDir.mkdirs();
            wavFile = new File(recDir, "audio.wav");
            wavRaf = new RandomAccessFile(wavFile, "rw");
            wavRaf.setLength(0);
            writeWavHeader(wavRaf, 0);
            wavRaf.seek(44);
            wavDataLen = 0;
            audioReady = false;

            JSONObject meta = new JSONObject();
            meta.put("name", "录音 " + new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date()));
            meta.put("favorite", false);
            meta.put("created", System.currentTimeMillis());
            LibraryStore.writeMeta(recDir, meta);

            int minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    Math.max(minBuf, SAMPLE_RATE * 2));
            recorder.startRecording();
        } catch (Exception e) {
            setState("error: " + e.getMessage());
            return false;
        }
        vad.reset();
        vadFill = 0;
        asrExec = Executors.newSingleThreadExecutor();
        recording = true;
        setState("recording");
        audioExec.execute(this::recordLoop);
        return true;
    }

    private void recordLoop() {
        byte[] byteBuf = new byte[2048];
        try {
            while (recording) {
                int n = recorder.read(byteBuf, 0, byteBuf.length);
                if (n <= 0) continue;
                wavRaf.write(byteBuf, 0, n);
                wavDataLen += n;
                float[] f = toFloat(byteBuf, n);
                int off = 0;
                while (off < f.length) {
                    int take = Math.min(WINDOW - vadFill, f.length - off);
                    System.arraycopy(f, off, vadBuf, vadFill, take);
                    vadFill += take;
                    off += take;
                    if (vadFill == WINDOW) {
                        vad.acceptWaveform(vadBuf);
                        vadFill = 0;
                        drainSegments();
                    }
                }
            }
        } catch (Exception ignored) {
        } finally {
            try {
                writeWavHeader(wavRaf, wavDataLen);
                wavRaf.close();
            } catch (IOException ignored) {
            }
            audioReady = true;
            try {
                vad.flush();
                drainSegments();
            } catch (Exception ignored) {
            }
            asrExec.execute(this::onDone);
            asrExec.shutdown();
        }
    }

    private void drainSegments() {
        while (!vad.empty()) {
            SpeechSegment seg = vad.front();
            vad.pop();
            final float[] samples = seg.getSamples();
            final long start = seg.getStart();
            asrExec.execute(() -> {
                String text = Hotwords.apply(transcribe(samples), ctx);
                if (text.isEmpty()) {
                    return;
                }
                final long startMs = start * 1000L / SAMPLE_RATE;
                final String line = "[" + fmtTime(start) + "] " + text;
                synchronized (lock) {
                    transcript.append(line).append('\n');
                    lines.add(new Line(line, startMs));
                }
                Listener l = listener;
                if (l != null) {
                    l.onLine(line, startMs);
                }
            });
        }
    }

    private String transcribe(float[] samples) {
        OfflineStream st = recognizer.createStream();
        st.acceptWaveform(samples, SAMPLE_RATE);
        recognizer.decode(st);
        OfflineRecognizerResult r = recognizer.getResult(st);
        st.release();
        String t = r.getText();
        return t == null ? "" : t.trim();
    }

    public void stopRecording() {
        if (!recording) {
            return;
        }
        recording = false;
        try {
            if (recorder != null) {
                recorder.stop();
                recorder.release();
            }
        } catch (Exception ignored) {
        }
        recorder = null;
        setState("finalizing");
    }

    private void onDone() {
        String text;
        synchronized (lock) {
            text = transcript.toString().trim();
        }
        if (recDir != null) {
            try (FileWriter fw = new FileWriter(new File(recDir, "transcript.txt"))) {
                fw.write(text);
            } catch (IOException ignored) {
            }
        }
        int deleted = LibraryStore.enforceLimit(ctx, MAX_RECORDINGS);
        KeepAliveService.stop(ctx);
        setState("done");
        Listener l = listener;
        if (l != null) {
            l.onDone(text, deleted);
        }
    }

    private static String fmtTime(long sampleIndex) {
        long sec = sampleIndex / SAMPLE_RATE;
        return String.format(Locale.US, "%02d:%02d", sec / 60, sec % 60);
    }

    private static float[] toFloat(byte[] pcm, int len) {
        int samples = len / 2;
        float[] out = new float[samples];
        for (int i = 0; i < samples; i++) {
            short s = (short) ((pcm[2 * i] & 0xFF) | (pcm[2 * i + 1] << 8));
            out[i] = s / 32768.0f;
        }
        return out;
    }

    private static void writeWavHeader(RandomAccessFile raf, long dataLen) throws IOException {
        raf.seek(0);
        raf.write(new byte[]{'R', 'I', 'F', 'F'});
        le32(raf, 36 + dataLen);
        raf.write(new byte[]{'W', 'A', 'V', 'E'});
        raf.write(new byte[]{'f', 'm', 't', ' '});
        le32(raf, 16);
        le16(raf, 1);
        le16(raf, 1);
        le32(raf, SAMPLE_RATE);
        le32(raf, SAMPLE_RATE * 2L);
        le16(raf, 2);
        le16(raf, 16);
        raf.write(new byte[]{'d', 'a', 't', 'a'});
        le32(raf, dataLen);
    }

    private static void le16(RandomAccessFile raf, int v) throws IOException {
        raf.write(v & 0xFF);
        raf.write((v >> 8) & 0xFF);
    }

    private static void le32(RandomAccessFile raf, long v) throws IOException {
        raf.write((int) (v & 0xFF));
        raf.write((int) ((v >> 8) & 0xFF));
        raf.write((int) ((v >> 16) & 0xFF));
        raf.write((int) ((v >> 24) & 0xFF));
    }
}
