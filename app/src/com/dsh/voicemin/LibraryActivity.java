package com.dsh.voicemin;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

public class LibraryActivity extends Activity {
    private File root;
    private File current;
    private TextView tvPath;
    private ListView lv;
    private SharedPreferences prefs;
    private final List<File> items = new ArrayList<>();
    private final List<Boolean> isFolder = new ArrayList<>();
    private boolean multiMode = false;
    private final Set<String> selected = new HashSet<>();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_library);
        prefs = getSharedPreferences("voicemin", MODE_PRIVATE);
        root = LibraryStore.root(this);
        current = root;
        tvPath = findViewById(R.id.tvPath);
        lv = findViewById(R.id.lv);
        findViewById(R.id.btnUp).setOnClickListener(v -> goUp());
        findViewById(R.id.btnNewFolder).setOnClickListener(v -> newFolder());
        findViewById(R.id.btnHotwords).setOnClickListener(v -> editHotwords());
        findViewById(R.id.btnSetSave).setOnClickListener(v -> setSaveFolder());
        findViewById(R.id.btnClear).setOnClickListener(v -> clearAll());
        findViewById(R.id.btnClose).setOnClickListener(v -> finish());
        findViewById(R.id.btnMulti).setOnClickListener(v -> toggleMulti());
        findViewById(R.id.btnExportSel).setOnClickListener(v -> exportSelected());
        findViewById(R.id.btnSummary).setOnClickListener(v -> summarySelected());
        lv.setOnItemClickListener((p, v, pos, id) -> onItem(pos));
        lv.setOnItemLongClickListener((p, v, pos, id) -> {
            onLong(pos);
            return true;
        });
        refresh();
    }

    private String rel() {
        return relOf(current);
    }

    private String relOf(File f) {
        String r = root.getAbsolutePath();
        String c = f.getAbsolutePath();
        if (c.equals(r)) {
            return "";
        }
        return c.substring(r.length() + 1);
    }

    private void goUp() {
        if (current == null || current.equals(root)) {
            return;
        }
        File parent = current.getParentFile();
        if (parent != null && parent.getAbsolutePath().startsWith(root.getAbsolutePath())) {
            current = parent;
            refresh();
        }
    }

    private void refresh() {
        tvPath.setText("位置：" + (rel().isEmpty() ? "根目录" : rel()));
        items.clear();
        isFolder.clear();
        File[] kids = current.listFiles();
        if (kids != null) {
            List<File> folders = new ArrayList<>();
            List<File> recs = new ArrayList<>();
            for (File k : kids) {
                if (k.isDirectory()) {
                    if (LibraryStore.isRecording(k)) {
                        recs.add(k);
                    } else {
                        folders.add(k);
                    }
                }
            }
            Collections.sort(folders, Comparator.comparing((File f) -> f.getName().toLowerCase()));
            Collections.sort(recs, Comparator.comparing((File f) -> f.getName()).reversed());
            for (File f : folders) {
                items.add(f);
                isFolder.add(true);
            }
            for (File f : recs) {
                items.add(f);
                isFolder.add(false);
            }
        }
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            File f = items.get(i);
            if (isFolder.get(i)) {
                labels.add("📁 " + f.getName());
            } else {
                String name = LibraryStore.name(f);
                if (multiMode) {
                    labels.add((selected.contains(f.getAbsolutePath()) ? "☑ " : "☐ ") + name);
                } else {
                    labels.add((LibraryStore.favorite(f) ? "★ " : "🎙 ") + name);
                }
            }
        }
        lv.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, labels));
    }

    private void onItem(int pos) {
        File f = items.get(pos);
        if (isFolder.get(pos)) {
            current = f;
            refresh();
            return;
        }
        if (multiMode) {
            String p = f.getAbsolutePath();
            if (selected.contains(p)) {
                selected.remove(p);
            } else {
                selected.add(p);
            }
            refresh();
        } else {
            showTranscript(f);
        }
    }

    private void onLong(int pos) {
        File f = items.get(pos);
        if (isFolder.get(pos)) {
            String[] opts = {"重命名", "删除"};
            new AlertDialog.Builder(this).setTitle(f.getName()).setItems(opts, (d, w) -> {
                if (w == 0) {
                    renameFolder(f);
                } else {
                    deleteFolder(f);
                }
            }).show();
        } else {
            String[] opts = {"查看文字", "重命名", "收藏/取消收藏", "移动到…", "删除"};
            new AlertDialog.Builder(this).setTitle(LibraryStore.name(f)).setItems(opts, (d, w) -> {
                switch (w) {
                    case 0:
                        showTranscript(f);
                        break;
                    case 1:
                        renameRecording(f);
                        break;
                    case 2:
                        LibraryStore.setFavorite(f, !LibraryStore.favorite(f));
                        toast(LibraryStore.favorite(f) ? "已收藏（不计入 20 条上限）" : "已取消收藏");
                        refresh();
                        break;
                    case 3:
                        moveRecording(f);
                        break;
                    case 4:
                        deleteRecording(f);
                        break;
                }
            }).show();
        }
    }

    private void showTranscript(File rec) {
        String text = readFile(new File(rec, "transcript.txt"));
        TextView tv = new TextView(this);
        tv.setText(text.isEmpty() ? "（没有转写文字）" : text);
        tv.setTextIsSelectable(true);
        tv.setTextSize(16);
        int pad = (int) (12 * getResources().getDisplayMetrics().density);
        tv.setPadding(pad, pad, pad, pad);
        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        sv.addView(tv);
        new AlertDialog.Builder(this)
                .setTitle(LibraryStore.name(rec))
                .setView(sv)
                .setPositiveButton("关闭", null)
                .show();
    }

    private void toggleMulti() {
        multiMode = !multiMode;
        if (!multiMode) {
            selected.clear();
        }
        toast(multiMode ? "已进入多选：点录音勾选" : "已退出多选");
        refresh();
    }

    private List<File> selectedFiles() {
        List<File> out = new ArrayList<>();
        for (String p : selected) {
            File f = new File(p);
            if (LibraryStore.isRecording(f)) {
                out.add(f);
            }
        }
        return out;
    }

    private void exportSelected() {
        List<File> sel = selectedFiles();
        if (sel.isEmpty()) {
            toast("先点「多选」再勾选录音");
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (File rec : sel) {
            sb.append("### ").append(LibraryStore.name(rec)).append("\n\n");
            sb.append(readFile(new File(rec, "transcript.txt")).trim()).append("\n\n");
        }
        String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        File dir = new File(getExternalFilesDir(null), "transcripts");
        dir.mkdirs();
        File f = new File(dir, "selected_" + ts + ".md");
        try (FileWriter fw = new FileWriter(f)) {
            fw.write("# 多选转写（共 " + sel.size() + " 条）\n\n" + sb);
        } catch (Exception e) {
            toast("导出失败：" + e.getMessage());
            return;
        }
        toast("已导出：" + f.getAbsolutePath());
    }

    private void summarySelected() {
        final List<File> sel = selectedFiles();
        if (sel.isEmpty()) {
            toast("先点「多选」再勾选录音");
            return;
        }
        final String key = prefs.getString("api_key", "");
        if (key.isEmpty()) {
            toast("先长按「问 AI」填 API Key");
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (File rec : sel) {
            sb.append("### ").append(LibraryStore.name(rec)).append("\n");
            sb.append(readFile(new File(rec, "transcript.txt")).trim()).append("\n\n");
        }
        final String content = sb.toString();
        final String prompt = "下面是我多段课堂录音的转写文字。请：\n"
                + "1) 提炼老师明确强调的重点；\n"
                + "2) 列出作业、实验、考试等安排（有截止时间/地点的要标出）；\n"
                + "3) 最后给一份简短的待办清单。\n用中文、分条、简洁。\n\n" + content;
        toast("总结中，请稍候…");
        new Thread(() -> {
            try {
                AiClient.Result r = AiClient.askText(key, null, prompt);
                String out = r.text + "\n\n— 用量：输入 " + r.promptTokens
                        + " / 输出 " + r.completionTokens + " tokens";
                String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
                File dir = new File(getExternalFilesDir(null), "summaries");
                dir.mkdirs();
                final File f = new File(dir, "summary_" + ts + ".md");
                try (FileWriter fw = new FileWriter(f)) {
                    fw.write("# 课堂总结\n\n" + r.text);
                } catch (Exception ignored) {
                }
                runOnUiThread(() -> showSummary(out, f));
            } catch (Exception e) {
                runOnUiThread(() -> toast("总结失败：" + e.getMessage()));
            }
        }).start();
    }

    private void showSummary(String text, File saved) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextIsSelectable(true);
        tv.setTextSize(15);
        int pad = (int) (12 * getResources().getDisplayMetrics().density);
        tv.setPadding(pad, pad, pad, pad);
        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        sv.addView(tv);
        new AlertDialog.Builder(this)
                .setTitle("课堂重点 / 作业安排")
                .setView(sv)
                .setPositiveButton("分享到…", (d, w) -> {
                    android.content.Intent it = new android.content.Intent(android.content.Intent.ACTION_SEND);
                    it.setType("text/plain");
                    it.putExtra(android.content.Intent.EXTRA_TEXT, text);
                    startActivity(android.content.Intent.createChooser(it, "分享到…"));
                })
                .setNegativeButton("关闭", null)
                .show();
        toast("已保存：" + saved.getAbsolutePath());
    }

    private void newFolder() {
        inputDialog("新建文件夹", "", name -> {
            if (name.isEmpty()) {
                return;
            }
            File d = new File(current, name);
            if (d.exists()) {
                toast("已存在同名");
                return;
            }
            if (d.mkdirs()) {
                toast("已创建");
                refresh();
            } else {
                toast("创建失败");
            }
        });
    }

    private void renameFolder(File f) {
        inputDialog("重命名文件夹", f.getName(), name -> {
            if (name.isEmpty()) {
                return;
            }
            File d = new File(f.getParentFile(), name);
            if (d.exists()) {
                toast("已存在同名");
                return;
            }
            if (f.renameTo(d)) {
                toast("已重命名");
                refresh();
            } else {
                toast("重命名失败");
            }
        });
    }

    private void deleteFolder(File f) {
        new AlertDialog.Builder(this).setTitle("删除文件夹")
                .setMessage("删除「" + f.getName() + "」及里面全部录音？不可恢复。")
                .setPositiveButton("删除", (d, w) -> {
                    LibraryStore.deleteRecursive(f);
                    if (f.equals(current)) {
                        current = root;
                    }
                    refresh();
                })
                .setNegativeButton("取消", null).show();
    }

    private void renameRecording(File rec) {
        inputDialog("重命名录音", LibraryStore.name(rec), name -> {
            if (name.isEmpty()) {
                return;
            }
            LibraryStore.setName(rec, name);
            refresh();
        });
    }

    private void deleteRecording(File rec) {
        new AlertDialog.Builder(this).setTitle("删除录音")
                .setMessage("删除这条录音和转写？不可恢复。")
                .setPositiveButton("删除", (d, w) -> {
                    LibraryStore.deleteRecursive(rec);
                    selected.remove(rec.getAbsolutePath());
                    refresh();
                })
                .setNegativeButton("取消", null).show();
    }

    private void moveRecording(File rec) {
        List<File> folders = new ArrayList<>();
        folders.add(root);
        LibraryStore.collectFolders(root, folders);
        List<String> labels = new ArrayList<>();
        for (File f : folders) {
            labels.add(f.equals(root) ? "根目录" : relOf(f));
        }
        new AlertDialog.Builder(this).setTitle("移动到…")
                .setItems(labels.toArray(new String[0]), (d, w) -> {
                    File target = folders.get(w);
                    if (target.equals(rec.getParentFile())) {
                        toast("已经在这里");
                        return;
                    }
                    File dst = new File(target, rec.getName());
                    if (dst.exists()) {
                        toast("目标已有同名录音");
                        return;
                    }
                    if (rec.renameTo(dst)) {
                        toast("已移动");
                        refresh();
                    } else {
                        toast("移动失败");
                    }
                }).show();
    }

    private void setSaveFolder() {
        prefs.edit().putString("save_folder", rel()).apply();
        toast("以后录音保存到：" + (rel().isEmpty() ? "根目录" : rel()));
    }

    private void clearAll() {
        new AlertDialog.Builder(this).setTitle("清空全部录音")
                .setMessage("会删除所有录音、转写和文件夹（含收藏），不可恢复。")
                .setPositiveButton("删除", (d, w) -> {
                    File[] kids = root.listFiles();
                    if (kids != null) {
                        for (File k : kids) {
                            LibraryStore.deleteRecursive(k);
                        }
                    }
                    selected.clear();
                    current = root;
                    refresh();
                    toast("已清空");
                })
                .setNegativeButton("取消", null).show();
    }

    private void editHotwords() {
        String cur = Hotwords.customText(this);
        String init = cur.isEmpty() ? Hotwords.defaultText() : cur;
        EditText et = new EditText(this);
        et.setText(init);
        et.setMinLines(8);
        et.setGravity(Gravity.TOP | Gravity.START);
        new AlertDialog.Builder(this)
                .setTitle("热词纠正表（每行：误识词=正确词）")
                .setView(et)
                .setPositiveButton("保存", (d, w) -> {
                    Hotwords.setCustomText(this, et.getText().toString());
                    toast("已保存，下次转写生效");
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void inputDialog(String title, String initial, Consumer<String> onOk) {
        EditText et = new EditText(this);
        et.setText(initial);
        new AlertDialog.Builder(this).setTitle(title).setView(et)
                .setPositiveButton("确定", (d, w) -> onOk.accept(et.getText().toString().trim()))
                .setNegativeButton("取消", null).show();
    }

    private String readFile(File f) {
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] b = new byte[(int) f.length()];
            int n = in.read(b);
            return new String(b, 0, Math.max(n, 0), "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
