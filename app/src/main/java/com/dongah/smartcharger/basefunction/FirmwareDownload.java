package com.dongah.smartcharger.basefunction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Executors;

public class FirmwareDownload {
    private static final Logger logger = LoggerFactory.getLogger(FirmwareDownload.class);


    public interface Callback {
        void onSuccess(File file);
        void onFail(String message);
    }


    private final String url;
    private final String fileName;
    private final int retry;
    private final Callback callback;


    public FirmwareDownload(String url, String fileName,
                            int retry, Callback callback) {
        this.url = url;
        this.fileName = fileName;
        this.retry = retry;
        this.callback = callback;
    }

    public void start() {
        Executors.newSingleThreadExecutor().execute(this::download);
    }


    private void download() {
        HttpURLConnection conn = null;
        InputStream is = null;
        FileOutputStream fos = null;
        boolean success = false;

        try {
            URL u = new URL(url);
            for (int i = 0; i < retry; i++) {
                HttpURLConnection attempt = (HttpURLConnection) u.openConnection();
                attempt.setRequestMethod("GET");
                attempt.setConnectTimeout(10000);
                attempt.setReadTimeout(15000);
                attempt.connect();

                if (attempt.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    conn = attempt;
                    File file = new File(GlobalVariables.getRootPath() + File.separator + fileName);
                    is = new BufferedInputStream(conn.getInputStream());
                    fos = new FileOutputStream(file);
                    byte[] buffer = new byte[4096];
                    int len;
                    while ((len = is.read(buffer)) != -1) {
                        fos.write(buffer, 0, len);
                    }
                    fos.flush();

                    success = true;
                    callback.onSuccess(file);
                    break;
                } else {
                    attempt.disconnect();
                }
            }
        } catch (Exception e) {
            logger.error("download error : {}", e.getMessage());
        } finally {
            try { if (is != null) is.close(); } catch (Exception ignored) {}
            try { if (fos != null) fos.close(); } catch (Exception ignored) {}
            if (conn != null) conn.disconnect();
        }

        if (!success) {
            callback.onFail("Fail");
        }
    }
}
