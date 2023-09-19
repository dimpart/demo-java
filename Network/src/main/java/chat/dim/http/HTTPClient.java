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
import java.io.IOError;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import chat.dim.digest.MD5;
import chat.dim.filesys.ExternalStorage;
import chat.dim.filesys.Paths;
import chat.dim.format.Hex;
import chat.dim.protocol.Address;
import chat.dim.protocol.ID;
import chat.dim.skywalker.Runner;
import chat.dim.utils.Log;
import chat.dim.utils.Template;

public abstract class HTTPClient extends Runner implements UploadDelegate, DownloadDelegate {

    // cache for uploaded file's URL
    private final Map<String, URL> cdn = new HashMap<>();     // filename => URL

    // requests waiting to upload/download
    private final List<UploadRequest> uploads = new ArrayList<>();
    private final List<DownloadRequest> downloads = new ArrayList<>();

    // tasks running
    private UploadTask uploadingTask = null;
    private UploadRequest uploadingRequest = null;
    private DownloadTask downloadingTask = null;
    private DownloadRequest downloadingRequest = null;

    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private Thread thread = null;

    /**
     *  Add an upload task
     *
     * @param api      - remote URL
     * @param secret   - authentication algorithm: hex(md5(data + secret + salt))
     * @param data     - file data
     * @param path     - temporary file path
     * @param var      - form variable
     * @param sender   - message sender
     * @param delegate - callback
     * @return remote URL for downloading when same file already uploaded to CDN
     */
    public URL upload(URL api, byte[] secret, byte[] data, String path, String var, ID sender,
                      UploadDelegate delegate) throws IOException {
        // 1. check previous upload
        String filename = Paths.filename(path);
        URL url = getURL(filename);  // filename in format: hex(md5(data)) + ext
        if (url != null) {
            // already uploaded
            return url;
        }
        // 2. save file data to the local path
        int len = ExternalStorage.saveBinary(data, path);
        assert len == data.length : "failed to save binary: " + path;
        // 3. build request
        addUploadRequest(new UploadRequest(api, path, secret, var, sender, delegate));
        return null;
    }

    /**
     *  Add a download task
     *
     * @param url      - remote URL
     * @param path     - temporary file path
     * @param delegate - callback
     * @return temporary file path when same file already downloaded from CDN
     */
    public String download(URL url, String path, DownloadDelegate delegate) {
        // 1. check previous download
        if (Paths.exists(path)) {
            // already downloaded
            return path;
        }
        // 2. build request
        addDownloadRequest(new DownloadRequest(url, path, delegate));
        return null;
    }

    private URL getURL(String filename) {
        URL url;
        Lock writeLock = lock.writeLock();
        writeLock.lock();
        try {
            url = cdn.get(filename);
        } finally {
            writeLock.unlock();
        }
        return url;
    }
    private void addUploadRequest(UploadRequest req) {
        Lock writeLock = lock.writeLock();
        writeLock.lock();
        try {
            uploads.add(req);
        } finally {
            writeLock.unlock();
        }
    }
    private void addDownloadRequest(DownloadRequest req) {
        Lock writeLock = lock.writeLock();
        writeLock.lock();
        try {
            downloads.add(req);
        } finally {
            writeLock.unlock();
        }
    }

    private UploadRequest getUploadRequest() {
        UploadRequest req = null;
        Lock writeLock = lock.writeLock();
        writeLock.lock();
        try {
            if (uploads.size() > 0) {
                req = uploads.remove(0);
            }
        } finally {
            writeLock.unlock();
        }
        return req;
    }
    private DownloadRequest getDownloadRequest() {
        DownloadRequest req = null;
        Lock writeLock = lock.writeLock();
        writeLock.lock();
        try {
            if (downloads.size() > 0) {
                req = downloads.remove(0);
            }
        } finally {
            writeLock.unlock();
        }
        return req;
    }

    /**
     *  Start a background thread
     */
    public void start() {
        stop();
        Thread thr = new Thread(this);
        thr.setDaemon(true);
        thr.start();
        thread = thr;
    }

    @Override
    public void stop() {
        super.stop();
        // wait for thread stop
        Thread thr = thread;
        if (thr != null) {
            // waiting 2 seconds for stopping the thread
            thread = null;
            try {
                thr.join(2000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public boolean process() {
        try {
            // drive upload tasks as priority
            if (driveUpload() || driveDownload()) {
                // it's busy
                return true;
            } else {
                // nothing to do now, cleanup temporary files
                cleanup();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        // have a rest
        return false;
    }

    // clean expired temporary files for upload/download
    protected abstract void cleanup();

    private boolean driveUpload() throws IOException {
        // 1. check running task
        UploadTask task = uploadingTask;
        if (task != null) {
            TaskStatus status = task.getStatus();
            switch (status) {
                case Error:
                    Log.error("task error: " + task);
                    break;

                case Running:
                case Success:
                    // task is busy now
                    return true;

                case Expired:
                    Log.error("task expired: " + task);
                    break;

                case Finished:
                    Log.info("task finished: " + task);
                    break;

                default:
                    assert TaskStatus.Waiting.equals(status) : "unknown status: " + task;
                    Log.warning("task status error: " + task);
                    break;
            }
            // remove task
            uploadingTask = null;
            uploadingRequest = null;
        }

        // 2. get next request
        UploadRequest req = getUploadRequest();
        if (req == null) {
            // nothing to upload now
            return false;
        }

        // 3. check previous upload
        String path = req.path;
        String filename = Paths.filename(path);
        URL url = getURL(filename);
        if (url != null) {
            // uploaded previously
            assert req.getStatus() == TaskStatus.Waiting : "request status error: " + req.getStatus();
            req.onSuccess();
            UploadDelegate delegate = req.getDelegate();
            if (delegate != null) {
                delegate.onUploadSuccess(req, url);
            }
            req.onFinished();
            return true;
        }

        // hash: md5(data + secret + salt)
        byte[] data = ExternalStorage.loadBinary(path);
        byte[] secret = req.secret;
        byte[] salt = random_salt();
        byte[] hash = MD5.digest(concat(data, secret, salt));

        // 4. build task
        String urlString = req.url.toString();
        // "https://sechat.dim.chat/{ID}/upload?md5={MD5}&salt={SALT}"
        Address address = req.sender.getAddress();
        urlString = Template.replace(urlString, "ID", address.toString());
        urlString = Template.replace(urlString, "MD5", Hex.encode(hash));
        urlString = Template.replace(urlString, "SALT", Hex.encode(salt));
        task = new UploadTask(new URL(urlString), req.name, filename, data, this);

        // 5. run it
        uploadingRequest = req;
        uploadingTask = task;
        task.run();
        return true;
    }
    private static byte[] concat(byte[] data, byte[] secret, byte[] salt) {
        byte[] buffer = new byte[data.length + secret.length + salt.length];
        System.arraycopy(data, 0, buffer, 0, data.length);
        System.arraycopy(secret, 0, buffer, data.length, secret.length);
        System.arraycopy(salt, 0, buffer, data.length + secret.length, salt.length);
        return buffer;
    }
    private static byte[] random_salt() {
        Random random = new Random();
        byte[] buffer = new byte[16];
        random.nextBytes(buffer);
        return buffer;
    }

    private boolean driveDownload() {
        // 1. check running task
        DownloadTask task = downloadingTask;
        if (task != null) {
            TaskStatus status = task.getStatus();
            switch (status) {
                case Error:
                    Log.error("task error: " + task);
                    break;

                case Running:
                case Success:
                    // task is busy now
                    return true;

                case Expired:
                    Log.error("task failed: " + task);
                    break;

                case Finished:
                    Log.info("task finished: " + task);
                    break;

                default:
                    assert TaskStatus.Waiting.equals(status) : "unknown status: " + task;
                    Log.warning("task status error: " + task);
                    break;
            }
            // remove task
            downloadingTask = null;
            downloadingRequest = null;
        }

        // 2. get next request
        DownloadRequest req = getDownloadRequest();
        if (req == null) {
            // nothing to download now
            return false;
        }

        // 3. check previous download
        String path = req.path;
        File file = new File(path);
        if (file.exists() && file.length() > 0) {
            // downloaded previously
            assert req.getStatus() == TaskStatus.Waiting : "request status error: " + req.getStatus();
            req.onSuccess();
            DownloadDelegate delegate = req.getDelegate();
            if (delegate != null) {
                delegate.onDownloadSuccess(req, path);
            }
            req.onFinished();
            return true;
        }

        // 4. build task
        task = new DownloadTask(req.url, path, this);

        // 5. run it
        downloadingRequest = req;
        downloadingTask = task;
        task.run();
        return true;
    }

    //-------- UploadDelegate

    @Override
    public void onUploadSuccess(UploadRequest request, URL url) {
        assert request instanceof UploadTask : "should not happen: " + request;
        UploadTask task = (UploadTask) request;
        UploadRequest req = uploadingRequest;
        assert task == uploadingTask : "upload tasks not match: " + task + ", " + uploadingTask;
        assert req != null && req.path.endsWith(task.filename) : "upload error: " + task + ", " + req;
        // 1. cache upload result
        if (url != null) {
            cdn.put(task.filename, url);
        }
        // 2. callback
        UploadDelegate delegate = req.getDelegate();
        if (delegate != null) {
            delegate.onUploadSuccess(req, url);
        }
    }

    @Override
    public void onUploadFailed(UploadRequest request, IOException error) {
        assert request instanceof UploadTask : "should not happen: " + request;
        UploadTask task = (UploadTask) request;
        UploadRequest req = uploadingRequest;
        assert task == uploadingTask : "upload tasks not match: " + task + ", " + uploadingTask;
        assert req != null && req.path.endsWith(task.filename) : "upload error: " + task + ", " + req;
        // callback
        UploadDelegate delegate = req.getDelegate();
        if (delegate != null) {
            delegate.onUploadFailed(req, error);
        }
    }

    @Override
    public void onUploadError(UploadRequest request, IOError error) {
        assert request instanceof UploadTask : "should not happen: " + request;
        UploadTask task = (UploadTask) request;
        UploadRequest req = uploadingRequest;
        assert task == uploadingTask : "upload tasks not match: " + task + ", " + uploadingTask;
        assert req != null && req.path.endsWith(task.filename) : "upload error: " + task + ", " + req;
        // callback
        UploadDelegate delegate = req.getDelegate();
        if (delegate != null) {
            delegate.onUploadError(req, error);
        }
    }

    //-------- DownloadDelegate

    @Override
    public void onDownloadSuccess(DownloadRequest request, String path) {
        assert request instanceof DownloadTask : "should not happen: " + request;
        DownloadTask task = (DownloadTask) request;
        DownloadRequest req = downloadingRequest;
        assert task == downloadingTask : "download tasks not match: " + task + ", " + downloadingTask;
        assert req != null && req.url.equals(task.url) : "download error: " + task + ", " + req;
        // callback
        DownloadDelegate delegate = req.getDelegate();
        if (delegate != null) {
            delegate.onDownloadSuccess(req, path);
        }
    }

    @Override
    public void onDownloadFailed(DownloadRequest request, IOException error) {
        assert request instanceof DownloadTask : "should not happen: " + request;
        DownloadTask task = (DownloadTask) request;
        DownloadRequest req = downloadingRequest;
        assert task == downloadingTask : "download tasks not match: " + task + ", " + downloadingTask;
        assert req != null && req.url.equals(task.url) : "download error: " + task + ", " + req;
        // callback
        DownloadDelegate delegate = req.getDelegate();
        if (delegate != null) {
            delegate.onDownloadFailed(req, error);
        }
    }

    @Override
    public void onDownloadError(DownloadRequest request, IOError error) {
        assert request instanceof DownloadTask : "should not happen: " + request;
        DownloadTask task = (DownloadTask) request;
        DownloadRequest req = downloadingRequest;
        assert task == downloadingTask : "download tasks not match: " + task + ", " + downloadingTask;
        assert req != null && req.url.equals(task.url) : "download error: " + task + ", " + req;
        // callback
        DownloadDelegate delegate = req.getDelegate();
        if (delegate != null) {
            delegate.onDownloadError(req, error);
        }
    }
}
