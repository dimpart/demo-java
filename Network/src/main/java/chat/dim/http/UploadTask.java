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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;

import chat.dim.format.UTF8;
import chat.dim.utils.Log;

public class UploadTask implements Runnable {

    private final WeakReference<HTTPDelegate> delegateRef;

    private final String urlString;
    private final String varName;
    private final String fileName;
    private final byte[] fileData;

    /**
     *  Upload data to URL with filename and variable name in form
     *
     * @param url      - API
     * @param name     - variable name in form
     * @param filename - file name
     * @param data     - file data
     */
    public UploadTask(String url, String name, String filename, byte[] data, HTTPDelegate delegate) {
        super();
        urlString = url;
        varName = name;
        fileName = filename;
        fileData = data;
        delegateRef = new WeakReference<>(delegate);
    }

    public String getUrlString() {
        return urlString;
    }
    public String getVarName() {
        return varName;
    }
    public String getFileName() {
        return fileName;
    }
    public byte[] getFileData() {
        return fileData;
    }

    public HTTPDelegate getDelegate() {
        return delegateRef.get();
    }

    @Override
    public boolean equals(Object other) {
        if (super.equals(other)) {
            // same object
            return true;
        } else if (other instanceof UploadTask) {
            UploadTask task = (UploadTask) other;
            return urlString.equals(task.urlString) &&
                    Arrays.equals(fileData, task.fileData);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return urlString.hashCode() * 13 + Arrays.hashCode(fileData);
    }

    private static final String BOUNDARY = "BU1kUJ19yLYPqv5xoT3sbKYbHwjUu1JU7roix";

    private static final String CONTENT_TYPE = "multipart/form-data; boundary=" + BOUNDARY;

    private static final String BEGIN = "--" + BOUNDARY + "\r\n"
            + "Content-Disposition: form-data; name=%s; filename=%s\r\n"
            + "Content-Type: application/octet-stream\r\n\r\n";
    private static final String END = "\r\n--" + BOUNDARY + "--";
    private static final byte[] TAIL = UTF8.encode(END);

    private static byte[] buildHTTPBody(String name, String filename, byte[] data) {
        String begin = String.format(BEGIN, name, filename);
        byte[] head = UTF8.encode(begin);
        byte[] buffer = new byte[head.length + data.length + TAIL.length];
        System.arraycopy(head, 0, buffer, 0, head.length);
        System.arraycopy(data, 0, buffer, head.length, data.length);
        System.arraycopy(TAIL, 0, buffer, head.length + data.length, TAIL.length);
        return buffer;
    }

    private static String post(String urlString, String varName, String fileName, byte[] fileData) throws IOException {
        String response = null;

        byte[] data = buildHTTPBody(varName, fileName, fileData);

        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setDoOutput(true);
        connection.setDoInput(true);
        connection.setRequestMethod("POST");
        connection.setUseCaches(false);
        connection.setInstanceFollowRedirects(true);
        connection.setConnectTimeout(32000);

        connection.setRequestProperty("Content-Type", CONTENT_TYPE);
        connection.setRequestProperty("Content-Length", String.valueOf(data.length));
        //connection.connect();

        try (OutputStream outputStream = connection.getOutputStream()) {
            outputStream.write(data);
            outputStream.flush();
        }

        int code = connection.getResponseCode();
        if (code == 200) {
            try (InputStream inputStream = connection.getInputStream()) {
                StringBuilder sb = new StringBuilder();
                byte[] buffer = new byte[1024];
                int len;
                while ((len = inputStream.read(buffer)) != -1) {
                    sb.append(new String(buffer, 0, len));
                }
                response = sb.toString();
            }
        }
        //connection.disconnect();

        return response;
    }

    @Override
    public void run() {
        HTTPDelegate delegate = getDelegate();
        try {
            String response = post(urlString, varName, fileName, fileData);
            delegate.uploadSuccess(this, response);
        } catch (IOException e) {
            //e.printStackTrace();
            Log.error("failed to upload: " + urlString);
            delegate.uploadFailed(this, e);
        }
    }
}
