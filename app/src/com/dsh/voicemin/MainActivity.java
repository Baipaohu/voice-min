package com.dsh.voicemin;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.text.InputType;
import android.util.Base64;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class MainActivity extends Activity implements Engine.Listener {
    private static final int SAMPLE_RATE = 16000;
    private static final int REQ_REC = 1;
    private static final int REQ_PICK = 2;
    private static final int REQ_NOTIF = 3;
    private static final int REQ_SPLASH = 4;

    private static final String SYSTEM_PROMPT =
            "你是一名电气工程与自动化领域的专业助手。用户会给你一段录音转写、图片识别或课件内容，"
                    + "请在理解内容的基础上回答问题。电气术语按电力/自动化行业习惯理解，例如：断路器、继电保护、"
                    + "PLC、SCADA、矢量控制、变频器、互感器、整定值、Modbus、IEC 61850。回答用简体中文，简洁准确。";

    private Button btnRecord, btnStop, btnExport, btnExportMd, btnAsk, btnLibrary, btnSettings;
    private TextView tvStatus;
    private LinearLayout linesContainer;
    private ScrollView scrollView;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService netExec = Executors.newSingleThreadExecutor();
    private SharedPreferences prefs;
    private volatile Consumer<String> pendingAppend;

    private volatile int playGen = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        prefs = getSharedPreferences("voicemin", MODE_PRIVATE);
        btnRecord = findViewById(R.id.btnRecord);
        btnStop = findViewById(R.id.btnStop);
        btnExport = findViewById(R.id.btnExport);
        btnExportMd = findViewById(R.id.btnExportMd);
        btnAsk = findViewById(R.id.btnAsk);
        btnLibrary = findViewById(R.id.btnLibrary);
        btnSettings = findViewById(R.id.btnSettings);
        tvStatus = findViewById(R.id.tvStatus);
        linesContainer = findViewById(R.id.linesContainer);
        scrollView = findViewById(R.id.scrollView);

        btnRecord.setOnClickListener(v -> startRecording());
        btnStop.setOnClickListener(v -> stopRecording());
        btnExport.setOnClickListener(v -> doExport(false));
        btnExportMd.setOnClickListener(v -> doExport(true));
        btnAsk.setOnClickListener(v -> askAi());
        btnAsk.setOnLongClickListener(v -> {
            showKeyDialog();
            return true;
        });
        btnLibrary.setOnClickListener(v -> startActivity(new Intent(this, LibraryActivity.class)));
        btnSettings.setOnClickListener(v -> showSettings());

        Engine.get().attach(this);
        Engine.get().init(this);
        applyState(Engine.get().getState());
        renderLinesFromEngine();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Engine.get().attach(this);
        applyState(Engine.get().getState());
        renderLinesFromEngine();
    }

    @Override
    protected void onDestroy() {
        stopPlayback();
        Engine.get().detach(this);
        super.onDestroy();
    }

    private void renderLinesFromEngine() {
        java.util.List<Engine.Line> lines = Engine.get().getLines();
        if (lines.isEmpty()) {
            return;
        }
        linesContainer.removeAllViews();
        for (Engine.Line l : lines) {
            addLineView(l.text, l.startMs);
        }
        scrollToBottom();
    }

    private void applyState(String state) {
        if (state == null) {
            return;
        }
        switch (state) {
            case "loading":
                tvStatus.setText("正在准备离线模型…");
                break;
            case "ready":
                tvStatus.setText("就绪，点击「开始录音」");
                break;
            case "recording":
                tvStatus.setText("录音中…（熄屏 / 后台会继续）");
                break;
            case "finalizing":
                tvStatus.setText("收尾转写中…");
                break;
            case "done":
                break;
            default:
                if (state.startsWith("error")) {
                    tvStatus.setText("出错了：" + state.substring(6));
                }
                break;
        }
    }

    // ---------- Engine.Listener ----------

    @Override
    public void onState(String state) {
        ui.post(() -> {
            applyState(state);
            boolean rec = "recording".equals(state);
            boolean busy = rec || "finalizing".equals(state);
            btnRecord.setEnabled(!busy);
            btnStop.setEnabled(rec);
        });
    }

    @Override
    public void onLine(String text, long startMs) {
        ui.post(() -> addLineView(text, startMs));
    }

    @Override
    public void onReset() {
        ui.post(() -> {
            linesContainer.removeAllViews();
            btnExport.setEnabled(false);
            btnExportMd.setEnabled(false);
        });
    }

    @Override
    public void onDone(String transcript, int deleted) {
        ui.post(() -> {
            String msg = transcript.isEmpty() ? "未识别到内容" : "转写完成 · 点任意句可回放";
            if (deleted > 0) {
                msg += "（已清理旧录音 " + deleted + " 条）";
            }
            tvStatus.setText(msg);
            btnRecord.setEnabled(true);
            btnStop.setEnabled(false);
            btnExport.setEnabled(!transcript.isEmpty());
            btnExportMd.setEnabled(!transcript.isEmpty());
            scrollToBottom();
        });
    }

    private void addLineView(String text, long startMs) {
        TextView line = new TextView(this);
        line.setText(text);
        line.setTextSize(16);
        int p = (int) (4 * getResources().getDisplayMetrics().density);
        line.setPadding(p, p, p, p);
        line.setOnClickListener(v -> playFrom(startMs));
        line.setOnLongClickListener(v -> {
            askWithSegment(text);
            return true;
        });
        linesContainer.addView(line);
        scrollToBottom();
    }

    private void startRecording() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_REC);
            return;
        }
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
        }
        if (!"ready".equals(Engine.get().getState())) {
            toast("模型还未就绪，请稍候");
            return;
        }
        KeepAliveService.start(this);
        if (!Engine.get().startRecording()) {
            KeepAliveService.stop(this);
            toast("开始录音失败");
            return;
        }
        btnRecord.setEnabled(false);
        btnStop.setEnabled(true);
    }

    private void stopRecording() {
        Engine.get().stopRecording();
        btnStop.setEnabled(false);
    }

    private void playFrom(long ms) {
        File wav = Engine.get().getWavFile();
        if (!Engine.get().isAudioReady() || wav == null || !wav.exists() || wav.length() <= 44) {
            toast("录音结束后才能回放");
            return;
        }
        final int gen = ++playGen;
        final long startByte = 44 + ms * 32;
        new Thread(() -> playLoop(gen, wav, startByte)).start();
    }

    private void stopPlayback() {
        playGen++;
    }

    private void playLoop(int gen, File wav, long startByte) {
        AudioTrack track = null;
        try {
            track = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build())
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .setBufferSizeInBytes(SAMPLE_RATE * 2)
                    .build();
            track.play();
            byte[] buf = new byte[4096];
            try (RandomAccessFile raf = new RandomAccessFile(wav, "r")) {
                raf.seek(Math.min(startByte, raf.length()));
                while (gen == playGen) {
                    int n = raf.read(buf);
                    if (n <= 0) break;
                    int off = 0;
                    while (off < n && gen == playGen) {
                        int w = track.write(buf, off, n - off);
                        if (w <= 0) break;
                        off += w;
                    }
                }
            }
        } catch (Exception e) {
            toast("回放失败: " + e.getMessage());
        } finally {
            if (track != null) {
                try {
                    track.pause();
                } catch (Exception ignored) {
                }
                try {
                    track.flush();
                } catch (Exception ignored) {
                }
                try {
                    track.release();
                } catch (Exception ignored) {
                }
            }
        }
    }

    // ---------- 问 AI ----------

    private void askAi() {
        String key = prefs.getString("api_key", "");
        if (key.isEmpty()) {
            showKeyDialog();
            return;
        }
        showAskDialog(Engine.get().getTranscript(), key);
    }

    private void askWithSegment(String segment) {
        String key = prefs.getString("api_key", "");
        if (key.isEmpty()) {
            showKeyDialog();
            return;
        }
        showAskDialog(segment, key);
    }

    private void showKeyDialog() {
        EditText et = new EditText(this);
        et.setHint("sk-...");
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        et.setText(prefs.getString("api_key", ""));
        new AlertDialog.Builder(this)
                .setTitle("DeepSeek API Key")
                .setMessage("Key 只保存在本机。以后长按「问 AI」可随时修改或清除。")
                .setView(et)
                .setPositiveButton("保存", (d, w) -> {
                    String k = et.getText().toString().trim();
                    prefs.edit().putString("api_key", k).apply();
                    toast(k.isEmpty() ? "已清除 Key" : "已保存");
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showAskDialog(String context, String key) {
        float den = getResources().getDisplayMetrics().density;
        int pad = (int) (12 * den);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);

        TextView ctxLabel = new TextView(this);
        ctxLabel.setText("参考内容（会随问题一起发送）：");
        root.addView(ctxLabel);

        final TextView ctx = new TextView(this);
        ctx.setText(context);
        ctx.setTextIsSelectable(true);
        ctx.setTextSize(14);
        ScrollView cs = new ScrollView(this);
        cs.addView(ctx);
        root.addView(cs, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) (150 * den)));

        Button upload = new Button(this);
        upload.setText("＋ 上传图片 / PDF（识别成文字）");
        root.addView(upload);

        final EditText q = new EditText(this);
        q.setHint("输入你的问题…");
        root.addView(q);

        final TextView ans = new TextView(this);
        ans.setTextIsSelectable(true);
        ans.setTextSize(15);
        root.addView(ans);

        final AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle("向 DeepSeek 提问")
                .setView(root)
                .setPositiveButton("发送", null)
                .setNegativeButton("关闭", null)
                .setNeutralButton("历史", null)
                .create();

        upload.setOnClickListener(v -> {
            pendingAppend = text -> ctx.append("\n【上传材料识别】\n" + text + "\n");
            Intent it = new Intent(Intent.ACTION_GET_CONTENT);
            it.setType("*/*");
            it.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/*", "application/pdf"});
            startActivityForResult(it, REQ_PICK);
        });

        dlg.setOnShowListener(d -> {
            dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String question = q.getText().toString().trim();
                if (question.isEmpty()) {
                    ans.setText("请先输入问题");
                    return;
                }
                final String ctxText = ctx.getText().toString();
                ans.setText("思考中…");
                netExec.execute(() -> {
                    try {
                        AiClient.Result r = AiClient.askText(key, SYSTEM_PROMPT,
                                "参考内容：\n" + ctxText + "\n\n问题：" + question);
                        String show = r.text + "\n\n— 用量：输入 " + r.promptTokens
                                + " / 输出 " + r.completionTokens + " tokens";
                        saveQa(question, r.text);
                        ui.post(() -> ans.setText(show));
                    } catch (Exception e) {
                        ui.post(() -> ans.setText("请求失败：" + e.getMessage()));
                    }
                });
            });
            dlg.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> showHistory());
        });
        dlg.show();
    }

    private void showHistory() {
        File f = new File(getFilesDir(), "qa_history.txt");
        String text = "";
        if (f.exists()) {
            try (FileInputStream in = new FileInputStream(f)) {
                byte[] b = new byte[(int) f.length()];
                int n = in.read(b);
                text = new String(b, 0, Math.max(n, 0), "UTF-8");
            } catch (Exception ignored) {
            }
        }
        if (text.trim().isEmpty()) {
            toast("还没有问答记录");
            return;
        }
        final String shareText = text;
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextIsSelectable(true);
        tv.setTextSize(14);
        int pad = (int) (12 * getResources().getDisplayMetrics().density);
        tv.setPadding(pad, pad, pad, pad);
        ScrollView sv = new ScrollView(this);
        sv.addView(tv);
        new AlertDialog.Builder(this).setTitle("问答历史")
                .setView(sv)
                .setPositiveButton("分享到…", (d, w) -> {
                    Intent it = new Intent(Intent.ACTION_SEND);
                    it.setType("text/plain");
                    it.putExtra(Intent.EXTRA_TEXT, shareText);
                    startActivity(Intent.createChooser(it, "分享到…"));
                })
                .setNegativeButton("关闭", null).show();
    }

    private void showSettings() {
        String[] items = {"设置启动页图片（从相册选）", "恢复默认启动页", "关闭启动页"};
        new AlertDialog.Builder(this).setTitle("设置").setItems(items, (d, w) -> {
            if (w == 0) {
                Intent it = new Intent(Intent.ACTION_GET_CONTENT);
                it.setType("image/*");
                startActivityForResult(it, REQ_SPLASH);
            } else if (w == 1) {
                new File(getFilesDir(), "splash.png").delete();
                prefs.edit().putBoolean("splash_disabled", false).putBoolean("splash_user_set", false).apply();
                toast("已恢复默认（文字）启动页（下次启动生效）");
            } else {
                new File(getFilesDir(), "splash.png").delete();
                prefs.edit().putBoolean("splash_disabled", true).putBoolean("splash_user_set", false).apply();
                toast("已关闭启动页（下次启动生效）");
            }
        }).show();
    }

    private void saveSplash(Uri uri) {
        try {
            Bitmap bmp = BitmapFactory.decodeStream(getContentResolver().openInputStream(uri));
            if (bmp == null) {
                toast("无法读取图片");
                return;
            }
            Bitmap small = downscale(bmp, 1080);
            try (FileOutputStream out = new FileOutputStream(new File(getFilesDir(), "splash.png"))) {
                small.compress(Bitmap.CompressFormat.PNG, 100, out);
            }
            prefs.edit().putBoolean("splash_disabled", false).putBoolean("splash_user_set", true).apply();
            toast("已设置启动页（下次启动生效）");
        } catch (Exception e) {
            toast("设置失败：" + e.getMessage());
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_SPLASH) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                saveSplash(data.getData());
            }
            return;
        }
        if (requestCode != REQ_PICK || resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        final Uri uri = data.getData();
        final Consumer<String> append = pendingAppend;
        if (append == null) {
            return;
        }
        toast("识别中，请稍候…");
        netExec.execute(() -> {
            try {
                String mime = getContentResolver().getType(uri);
                String text;
                if (mime != null && mime.contains("pdf")) {
                    text = ocrPdf(uri);
                } else {
                    text = ocrImage(uri);
                }
                final String result = text;
                ui.post(() -> {
                    append.accept(result);
                    toast("已加入参考内容");
                });
            } catch (Exception e) {
                ui.post(() -> toast("识别失败：" + e.getMessage()));
            }
        });
    }

    private String ocrImage(Uri uri) throws Exception {
        Bitmap bmp = BitmapFactory.decodeStream(getContentResolver().openInputStream(uri));
        if (bmp == null) {
            throw new IOException("无法读取图片");
        }
        String key = prefs.getString("api_key", "");
        if (key.isEmpty()) {
            throw new IOException("请先设置 API Key（长按「问 AI」）");
        }
        AiClient.Result r = AiClient.askVision(key,
                "请逐字识别图片中的全部文字，保持原有顺序与换行；不要翻译、不要解释、只输出识别到的文字。",
                toBase64(downscale(bmp, 1600)));
        return r.text;
    }

    private String ocrPdf(Uri uri) throws Exception {
        String key = prefs.getString("api_key", "");
        if (key.isEmpty()) {
            throw new IOException("请先设置 API Key（长按「问 AI」）");
        }
        ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(uri, "r");
        if (pfd == null) {
            throw new IOException("无法打开 PDF");
        }
        PdfRenderer pr = new PdfRenderer(pfd);
        int pages = Math.min(pr.getPageCount(), 8);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < pages; i++) {
            PdfRenderer.Page page = pr.openPage(i);
            int w = Math.max(1, page.getWidth() * 2);
            int h = Math.max(1, page.getHeight() * 2);
            Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            bmp.eraseColor(Color.WHITE);
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            page.close();
            AiClient.Result r = AiClient.askVision(key,
                    "请逐字识别这一页的全部文字，保持顺序与换行；不要翻译、不要解释。",
                    toBase64(downscale(bmp, 1600)));
            sb.append("—— 第 ").append(i + 1).append(" 页 ——\n").append(r.text).append('\n');
        }
        pr.close();
        pfd.close();
        return sb.toString();
    }

    private static Bitmap downscale(Bitmap src, int max) {
        int w = src.getWidth();
        int h = src.getHeight();
        if (w <= max && h <= max) {
            return src;
        }
        float s = Math.min((float) max / w, (float) max / h);
        return Bitmap.createScaledBitmap(src, Math.max(1, Math.round(w * s)),
                Math.max(1, Math.round(h * s)), true);
    }

    private static String toBase64(Bitmap bmp) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.JPEG, 85, bos);
        return Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP);
    }

    private void saveQa(String q, String a) {
        try (FileWriter fw = new FileWriter(new File(getFilesDir(), "qa_history.txt"), true)) {
            String ts = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
            fw.write("[" + ts + "] 问：" + q + "\n答：" + a + "\n\n");
        } catch (IOException ignored) {
        }
    }

    // ---------- 导出 ----------

    private void scrollToBottom() {
        if (scrollView != null) {
            scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
        }
    }

    private void doExport(boolean md) {
        String text = Engine.get().getTranscript();
        if (text.isEmpty()) {
            toast("没有可导出的内容");
            return;
        }
        String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        File dir = new File(getExternalFilesDir(null), "transcripts");
        dir.mkdirs();
        File f = new File(dir, "transcript_" + ts + (md ? ".md" : ".txt"));
        try (FileWriter fw = new FileWriter(f)) {
            if (md) {
                String now = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date());
                fw.write("# 录音转写\n\n> 导出时间：" + now + "\n\n");
            }
            fw.write(text);
        } catch (IOException e) {
            toast("导出失败: " + e.getMessage());
            return;
        }
        toast("已导出: " + f.getAbsolutePath());
    }

    private void toast(String s) {
        ui.post(() -> Toast.makeText(this, s, Toast.LENGTH_LONG).show());
    }
}
