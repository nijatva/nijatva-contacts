package com.nijatva.contacts;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * HTTPS client for JSON APIs (GET/POST only - these are allowed on HttpURLConnection).
 * HttpURLConnection correctly handles Content-Length, chunked responses, gzip and redirects.
 */
public class HttpJson {

    public static class Resp {
        public int code;
        public String body = "";
        public String location = null;
        public Resp(int c, String b, String l) { code = c; body = b; location = l; }
    }

    public static Resp request(String method, String urlStr, String body, String contentType, String authHeader)
            throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(urlStr).openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(20000);
        c.setReadTimeout(60000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("Accept", "application/json");
        c.setRequestProperty("User-Agent", "NijatvaContacts/1.0 (Android)");
        if (authHeader != null && authHeader.length() > 0) {
            c.setRequestProperty("Authorization", authHeader);
        }
        if (body != null) {
            byte[] bb = body.getBytes("UTF-8");
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type",
                    contentType == null ? "application/x-www-form-urlencoded" : contentType);
            c.setFixedLengthStreamingMode(bb.length);
            OutputStream os = c.getOutputStream();
            os.write(bb);
            os.flush();
            os.close();
        }

        int code = c.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? c.getInputStream() : c.getErrorStream();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        if (is != null) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
            try { is.close(); } catch (Exception ignore) {}
        }
        String payload = new String(bos.toByteArray(), "UTF-8");
        String location = c.getHeaderField("Location");
        try { c.disconnect(); } catch (Exception ignore) {}
        return new Resp(code, payload, location);
    }
}
