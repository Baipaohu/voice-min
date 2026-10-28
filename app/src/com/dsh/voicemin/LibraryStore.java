package com.dsh.voicemin;

import android.content.Context;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class LibraryStore {
    private LibraryStore() {
    }

    public static File root(Context c) {
        File r = new File(c.getExternalFilesDir(null), "library");
        if (!r.exists()) {
            r.mkdirs();
        }
        return r;
    }

    public static boolean isRecording(File dir) {
        return dir != null && dir.isDirectory() && new File(dir, "meta.json").isFile();
    }

    public static JSONObject readMeta(File rec) {
        try {
            File f = new File(rec, "meta.json");
            byte[] b = new byte[(int) f.length()];
            try (FileInputStream in = new FileInputStream(f)) {
                int n = in.read(b);
                return new JSONObject(new String(b, 0, Math.max(n, 0), "UTF-8"));
            }
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    public static void writeMeta(File rec, JSONObject meta) {
        try (FileOutputStream out = new FileOutputStream(new File(rec, "meta.json"))) {
            out.write(meta.toString().getBytes("UTF-8"));
        } catch (Exception ignored) {
        }
    }

    public static String name(File rec) {
        return readMeta(rec).optString("name", rec.getName());
    }

    public static boolean favorite(File rec) {
        return readMeta(rec).optBoolean("favorite", false);
    }

    public static void setFavorite(File rec, boolean fav) {
        JSONObject m = readMeta(rec);
        try {
            m.put("favorite", fav);
        } catch (Exception ignored) {
        }
        writeMeta(rec, m);
    }

    public static void setName(File rec, String name) {
        JSONObject m = readMeta(rec);
        try {
            m.put("name", name);
        } catch (Exception ignored) {
        }
        writeMeta(rec, m);
    }

    public static long created(File rec) {
        long v = readMeta(rec).optLong("created", 0);
        return v != 0 ? v : rec.lastModified();
    }

    public static void deleteRecursive(File f) {
        if (f == null || !f.exists()) {
            return;
        }
        File[] kids = f.listFiles();
        if (kids != null) {
            for (File k : kids) {
                deleteRecursive(k);
            }
        }
        f.delete();
    }

    public static void collectRecordings(File dir, List<File> out) {
        File[] kids = dir.listFiles();
        if (kids == null) {
            return;
        }
        for (File k : kids) {
            if (k.isDirectory()) {
                if (isRecording(k)) {
                    out.add(k);
                } else {
                    collectRecordings(k, out);
                }
            }
        }
    }

    public static void collectFolders(File dir, List<File> out) {
        File[] kids = dir.listFiles();
        if (kids == null) {
            return;
        }
        for (File k : kids) {
            if (k.isDirectory() && !isRecording(k)) {
                out.add(k);
                collectFolders(k, out);
            }
        }
    }

    /** 只保留最近 max 条“未收藏”录音，收藏的不计入、不删除。 */
    public static int enforceLimit(Context c, int max) {
        File root = root(c);
        List<File> all = new ArrayList<>();
        collectRecordings(root, all);
        List<File> nonFav = new ArrayList<>();
        for (File r : all) {
            if (!favorite(r)) {
                nonFav.add(r);
            }
        }
        Collections.sort(nonFav, (a, b) -> Long.compare(created(b), created(a)));
        int deleted = 0;
        for (int i = max; i < nonFav.size(); i++) {
            deleteRecursive(nonFav.get(i));
            deleted++;
        }
        return deleted;
    }
}
