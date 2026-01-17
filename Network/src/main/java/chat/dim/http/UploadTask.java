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

import java.io.IOError;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;
import java.util.UnknownFormatConversionException;

import chat.dim.format.JSONMap;
import chat.dim.format.UTF8;
import chat.dim.log.Log;

/**
 *  Upload Task
 *  ~~~~~~~~~~~
 *  running task
 *
 *  properties:
 *      url      - remote URL
 *      path     -
 *      secret   -
 *      name     - form var name ('avatar' or 'file')
 *      filename - form file name
 *      data     - form file data
 *      sender   -
 *      delegate - HTTP client
 */
public class UploadTask extends UploadRequest implements Runnable {

    public final String filename;  // file name
    public final byte[] data;      // file data

    public UploadTask(URL url, String var, String fileName, byte[] fileData, UploadDelegate delegate) {
        super(url, null, null, null, var, null, delegate);
        filename = fileName;
        data = fileData;
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

    private static String post(URL url, String varName, String fileName, byte[] fileData) throws IOException {
        String response = null;

        Log.info("upload " + fileName + " (" + fileData.length + " bytes) onto " + url);
        byte[] data = buildHTTPBody(varName, fileName, fileData);

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
        if (code == HttpURLConnection.HTTP_OK) {
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

    /*  response: {
            "code": {{code}},
            "message": "{{message}}",
            "filename": "{{filename}}",
            "url": "{{url}}"
        }
     */
    private static URL getURL(String json) throws NoSuchFieldException, MalformedURLException {
        if (json == null) {
            // no response?
            throw new NullPointerException("no response");
        }
        Map<?, ?> info = JSONMap.decode(json);
        if (info == null) {
            throw new UnknownFormatConversionException("json error: " + json);
        }
        String url = (String) info.get("url");
        if (url == null) {
            // response error
            throw new NoSuchFieldException("url not found:  " + json);
        }
        return new URL(url);
    }

    @Override
    public void run() {
        UploadDelegate delegate = getDelegate();
        touch();
        // 1. send to server
        String response;
        try {
            response = post(url, name, filename, data);
        } catch (IOException | AssertionError e) {
            IOException ie = e instanceof IOException ? (IOException) e : new IOException(e);
            e.printStackTrace();
            Log.error("failed to upload: " + filename + " -> " + url + ", error: " + e);
            onError();
            if (delegate != null) {
                delegate.onUploadFailed(this, ie);
            }
            onFinished();
            return;
        }
        // 2. get URL from server response
        URL url;
        try {
            url = getURL(response);
        } catch (NoSuchFieldException | MalformedURLException | RuntimeException e) {
            e.printStackTrace();
            onError();
            if (delegate != null) {
                delegate.onUploadError(this, new IOError(e));
            }
            onFinished();
            return;
        }
        // 3. upload success
        onSuccess();
        if (delegate != null) {
            delegate.onUploadSuccess(this, url);
        }
        onFinished();
    }
}
