/* license: https://mit-license.org
 *
 *  HTTP
 *
 *                                Written in 2022 by Moky <albert.moky@gmail.com>
 *
 * ==============================================================================
 * The MIT License (MIT)
 *
 * Copyright (c) 2022 Albert Moky
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 * ==============================================================================
 */
package chat.dim.http;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOError;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import chat.dim.filesys.Paths;
import chat.dim.log.Log;

/**
 *  Download Task
 *  ~~~~~~~~~~~~~
 *  running task
 *
 *  properties:
 *      url      - remote URL
 *      path     - temporary file path
 *      delegate - HTTP client
 */
public class DownloadTask extends DownloadRequest implements Runnable {

    public DownloadTask(URL url, String path, DownloadDelegate delegate) {
        super(url, path, delegate);
    }

    private static String getTemporaryPath(String filePath) {
        String dir = Paths.parent(filePath);
        String filename = Paths.filename(filePath);
        return Paths.append(dir, filename + ".tmp");
    }

    private static IOError download(URL url, String filePath) throws IOException {
        Log.info("download from " + url);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setDoOutput(false);
        connection.setDoInput(true);
        connection.setRequestMethod("GET");
        connection.setUseCaches(true);
        connection.setInstanceFollowRedirects(true);
        connection.setConnectTimeout(5000);
        //connection.connect();

        IOError error;
        String tmpPath = getTemporaryPath(filePath);

        int code = connection.getResponseCode();
        if (code == HttpURLConnection.HTTP_OK) {
            int contentLength = connection.getContentLength();
            try (InputStream inputStream = connection.getInputStream()) {
                File file = new File(tmpPath);
                try (FileOutputStream outputStream = new FileOutputStream(file)) {
                    byte[] buffer = new byte[1024];
                    int readLength = 0;
                    int len;
                    while ((len = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, len);
                        readLength += len;
                    }
                    outputStream.flush();
                    // OK
                    error = null;
                    // check read length
                    if (contentLength <= 0 || contentLength == readLength) {
                        Log.info("[FTP] downloaded " + readLength + "(" + file.length()
                                + "), content-length: " + contentLength + ", URL: " + url);
                        boolean ok = file.renameTo(new File(filePath));
                        Log.info("move temporary file: " + tmpPath + " => " + filePath + ", " + ok);
                    } else {
                        Log.error("[FTP] download length error: " + readLength + "(" + file.length()
                                + "), content-length: " + contentLength + ", URL: " + url);
                    }
                }
            }
        } else {
            // TODO: fetch error response
            error = new IOError(null);
        }
        //connection.disconnect();

        return error;
    }

    @Override
    public void run() {
        DownloadDelegate delegate = getDelegate();
        touch();
        IOError error;
        try {
            // 1. prepare directory
            String dir = Paths.parent(path);
            assert dir != null : "download file path error: " + path;
            if (Paths.mkdirs(dir)) {
                // 2. start download
                error = download(url, path);
            } else {
                error = new IOError(new IOException("failed to create dir: " + dir));
            }
        } catch (IOException | AssertionError e) {
            IOException ie = e instanceof IOException ? (IOException) e : new IOException(e);
            //e.printStackTrace();
            Log.error("failed to download: " + url + ", error: " + e);
            onError();
            if (delegate != null) {
                delegate.onDownloadFailed(this, ie);
            }
            onFinished();
            return;
        }
        if (error == null) {
            onSuccess();
            delegate.onDownloadSuccess(this, path);
        } else {
            onError();
            delegate.onDownloadError(this, error);
        }
        onFinished();
    }
}
