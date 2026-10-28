package com.dsh.voicemin;

import android.content.Context;

import java.util.LinkedHashMap;
import java.util.Map;

public final class Hotwords {
    private static final String PREFS = "voicemin";
    private static final String KEY = "hotword_custom";

    private Hotwords() {
    }

    private static LinkedHashMap<String, String> defaults() {
        LinkedHashMap<String, String> m = new LinkedHashMap<>();
        m.put("断容器", "断路器");
        m.put("断电器", "断路器");
        m.put("继电保户", "继电保护");
        m.put("继电保护器", "继电保护");
        m.put("批尔西", "PLC");
        m.put("P L C", "PLC");
        m.put("斯卡达", "SCADA");
        m.put("斯可达", "SCADA");
        m.put("矢量控件", "矢量控制");
        m.put("便频器", "变频器");
        m.put("护感器", "互感器");
        m.put("整锭值", "整定值");
        m.put("莫德巴斯", "Modbus");
        m.put("莫迪巴斯", "Modbus");
        m.put("IEC六一八五零", "IEC 61850");
        m.put("IEC 六一八五零", "IEC 61850");
        return m;
    }

    public static String defaultText() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : defaults().entrySet()) {
            sb.append(e.getKey()).append('=').append(e.getValue()).append('\n');
        }
        return sb.toString();
    }

    public static String customText(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "");
    }

    public static void setCustomText(Context c, String text) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, text).apply();
    }

    public static String apply(String text, Context c) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String out = text;
        for (Map.Entry<String, String> e : defaults().entrySet()) {
            out = out.replace(e.getKey(), e.getValue());
        }
        String custom = customText(c);
        if (!custom.isEmpty()) {
            for (String line : custom.split("\\r?\\n")) {
                int i = line.indexOf('=');
                if (i > 0) {
                    String w = line.substring(0, i).trim();
                    String r = line.substring(i + 1).trim();
                    if (!w.isEmpty() && !r.isEmpty()) {
                        out = out.replace(w, r);
                    }
                }
            }
        }
        return out;
    }
}
