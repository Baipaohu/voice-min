package com.dsh.voicemin;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public final class AiClient {
    public static final String MODEL = "deepseek-flash";
    private static final String ENDPOINT = "https://api.deepseek.com/chat/completions";

    private AiClient() {
    }

    public static Result askText(String key, String system, String user) throws Exception {
        JSONArray msgs = new JSONArray();
        if (system != null && !system.isEmpty()) {
            msgs.put(new JSONObject().put("role", "system").put("content", system));
        }
        msgs.put(new JSONObject().put("role", "user").put("content", user));
        JSONObject body = new JSONObject()
                .put("model", MODEL)
                .put("messages", msgs)
                .put("stream", false);
        return post(key, body.toString());
    }

    public static Result askVision(String key, String prompt, String base64Jpeg) throws Exception {
        JSONArray content = new JSONArray();
        content.put(new JSONObject().put("type", "text").put("text", prompt));
        content.put(new JSONObject()
                .put("type", "image_url")
                .put("image_url", new JSONObject().put("url", "data:image/jpeg;base64," + base64Jpeg)));
        JSONArray msgs = new JSONArray();
        msgs.put(new JSONObject().put("role", "user").put("content", content));
        JSONObject body = new JSONObject()
                .put("model", MODEL)
                .put("messages", msgs)
                .put("stream", false);
        return post(key, body.toString());
    }

    private static Result post(String key, String body) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(ENDPOINT).openConnection();
        c.setRequestMethod("POST");
        c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        c.setRequestProperty("Authorization", "Bearer " + key);
        c.setDoOutput(true);
        c.setConnectTimeout(20000);
        c.setReadTimeout(120000);
        try (OutputStream os = c.getOutputStream()) {
            os.write(body.getBytes("UTF-8"));
        }
        int code = c.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? c.getInputStream() : c.getErrorStream();
        String resp = readAll(is);
        if (code < 200 || code >= 300) {
            throw new IOException("HTTP " + code + ": " + resp);
        }
        JSONObject j = new JSONObject(resp);
        String content = "";
        JSONArray choices = j.optJSONArray("choices");
        if (choices != null && choices.length() > 0) {
            JSONObject msg = choices.getJSONObject(0).optJSONObject("message");
            if (msg != null) {
                content = msg.optString("content", "");
            }
        }
        int pin = 0, pout = 0;
        JSONObject u = j.optJSONObject("usage");
        if (u != null) {
            pin = u.optInt("prompt_tokens", 0);
            pout = u.optInt("completion_tokens", 0);
        }
        return new Result(content, pin, pout);
    }

    static String readAll(InputStream is) throws IOException {
        if (is == null) {
            return "";
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) > 0) {
            bos.write(buf, 0, n);
        }
        return new String(bos.toByteArray(), "UTF-8");
    }

    public static final class Result {
        public final String text;
        public final int promptTokens;
        public final int completionTokens;

        Result(String text, int promptTokens, int completionTokens) {
            this.text = text;
            this.promptTokens = promptTokens;
            this.completionTokens = completionTokens;
        }
    }
}
