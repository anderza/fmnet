package com.anderza.fmnet;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/** Читает из радиопотока строку «Исполнитель - Трек» (ICY / Shoutcast / Icecast метаданные). */
final class IcyReader {

    private IcyReader() {
    }

    static String read(String streamUrl) {
        HttpURLConnection c = null;
        try {
            String url = streamUrl;
            for (int hop = 0; hop < 4; hop++) {
                c = (HttpURLConnection) new URL(url).openConnection();
                c.setInstanceFollowRedirects(false);
                c.setConnectTimeout(6000);
                c.setReadTimeout(8000);
                c.setRequestProperty("Icy-MetaData", "1");
                c.setRequestProperty("User-Agent", "RadioNet/1.0 (Android)");
                int code = c.getResponseCode();
                if (code >= 300 && code < 400) {
                    String loc = c.getHeaderField("Location");
                    c.disconnect();
                    c = null;
                    if (loc == null) return null;
                    url = new URL(new URL(url), loc).toString();
                    continue;
                }
                break;
            }
            if (c == null) return null;
            String mi = c.getHeaderField("icy-metaint");
            if (mi == null) return null;
            int metaInt = Integer.parseInt(mi.trim());
            if (metaInt <= 0 || metaInt > 1_000_000) return null;
            InputStream in = c.getInputStream();
            long skipped = 0;
            byte[] buf = new byte[8192];
            while (skipped < metaInt) {
                int n = in.read(buf, 0, (int) Math.min(buf.length, metaInt - skipped));
                if (n < 0) return null;
                skipped += n;
            }
            int len = in.read() * 16;
            if (len <= 0) return null;
            byte[] meta = new byte[len];
            int got = 0;
            while (got < len) {
                int n = in.read(meta, got, len - got);
                if (n < 0) break;
                got += n;
            }
            String text = decode(meta, got);
            int i = text.indexOf("StreamTitle='");
            if (i < 0) return null;
            int start = i + 13;
            int end = text.indexOf("';", start);
            if (end < 0) end = text.lastIndexOf('\'');
            if (end <= start) return null;
            return text.substring(start, end).trim();
        } catch (Exception e) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    /** UTF-8, а если текст «ломается» — Windows-1251 (так вещают многие русские станции). */
    private static String decode(byte[] b, int len) {
        String utf = new String(b, 0, len, StandardCharsets.UTF_8);
        if (utf.indexOf('�') < 0) return utf;
        try {
            return new String(b, 0, len, Charset.forName("windows-1251"));
        } catch (Exception e) {
            return utf;
        }
    }
}
