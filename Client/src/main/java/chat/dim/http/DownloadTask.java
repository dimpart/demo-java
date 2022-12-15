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
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;

import chat.dim.digest.MD5;
import chat.dim.filesys.LocalCache;
import chat.dim.filesys.PathUtils;
import chat.dim.filesys.Paths;
import chat.dim.format.Hex;
import chat.dim.format.UTF8;

public class DownloadTask implements Runnable {

    private final WeakReference<HTTPDelegate> delegateRef;

    private final String urlString;
    private final String cachePath;

    public DownloadTask(String url, HTTPDelegate delegate) {
        super();
        urlString = url;
        cachePath = getCachePath(url);
        delegateRef = new WeakReference<>(delegate);
    }

    @Override
    public boolean equals(Object other) {
        if (super.equals(other)) {
            // same object
            return true;
        } else if (other instanceof DownloadTask) {
            DownloadTask task = (DownloadTask) other;
            return urlString.equals(task.urlString);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return urlString.hashCode();
    }

    public HTTPDelegate getDelegate() {
        return delegateRef.get();
    }

    public String getUrlString() {
        return urlString;
    }

    /**
     *  Get downloaded file path
     *
     * @return local file path
     */
    public String getFilePath() {
        if (LocalCache.exists(cachePath)) {
            return cachePath;
        } else {
            return null;
        }
    }

    // "/sdcard/chat.dim.sechat/caches/{XX}/{YY}/{filename}"
    private static String getCachePath(String urlString) {
        // get file ext
        String filename = Paths.getFilename(urlString);
        String ext = Paths.getExtension(filename);
        if (ext == null || ext.length() == 0) {
            ext = "tmp";
        }
        // get filename
        byte[] data = UTF8.encode(urlString);
        String hash = Hex.encode(MD5.digest(data));
        return LocalCache.getCacheFilePath(hash + "." + ext);
    }

    private static String download(String urlString, String cachePath) throws IOException {
        String filepath = null;

        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setDoOutput(false);
        connection.setDoInput(true);
        connection.setRequestMethod("GET");
        connection.setUseCaches(true);
        connection.setInstanceFollowRedirects(true);
        connection.setConnectTimeout(5000);
        //connection.connect();

        int code = connection.getResponseCode();
        if (code == 200) {
            try (InputStream inputStream = connection.getInputStream()) {
                File file = new File(cachePath);
                try (FileOutputStream outputStream = new FileOutputStream(file)) {
                    byte[] buffer = new byte[1024];
                    int len;
                    while ((len = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, len);
                    }
                    outputStream.flush();
                    filepath = cachePath;
                }
            }
        }
        //connection.disconnect();

        return filepath;
    }

    @Override
    public void run() {
        HTTPDelegate delegate = getDelegate();
        try {
            // 1. prepare directory
            String dir = PathUtils.parent(cachePath);
            assert dir != null : "cache path error: " + cachePath;
            LocalCache.mkdirs(dir);
            // 2. start download
            String path = download(urlString, cachePath);
            delegate.downloadSuccess(this, path);
        } catch (IOException e) {
            e.printStackTrace();
            delegate.downloadFailed(this, e);
        }
    }
}
