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
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import chat.dim.Register;
import chat.dim.filesys.ExternalStorage;
import chat.dim.filesys.Paths;
import chat.dim.skywalker.Processor;

public enum HTTPClient implements Runnable, Processor {

    INSTANCE;

    public static HTTPClient getInstance() {
        return INSTANCE;
    }

    /**
     *  Base directory
     */
    private String base = "/tmp/.dim";  // "/sdcard/chat.dim.sechat"
    private boolean built = false;

    private final List<UploadTask> uploadTasks;
    private final ReadWriteLock uploadLock;
    private final List<DownloadTask> downloadTasks;
    private final ReadWriteLock downloadLock;

    private WeakReference<HTTPDelegate> delegateRef;
    private Thread thread;
    private boolean running;

    HTTPClient() {
        uploadTasks = new ArrayList<>();
        uploadLock = new ReentrantReadWriteLock();
        downloadTasks = new ArrayList<>();
        downloadLock = new ReentrantReadWriteLock();
        delegateRef = null;
        thread = null;
        running = false;

        // load plugins
        Register.prepare();
    }

    public HTTPDelegate getDelegate() {
        WeakReference<HTTPDelegate> ref = delegateRef;
        return ref == null ? null : ref.get();
    }
    public void setDelegate(HTTPDelegate delegate) {
        if (delegate == null) {
            delegateRef = null;
        } else {
            delegateRef = new WeakReference<>(delegate);
        }
    }

    public void setRoot(String dir) {
        // lazy create
        base = dir;
    }
    public String getRoot() {
        if (built) {
            return base;
        }
        try {
            // make sure base directory built
            ExternalStorage.mkdirs(base);
            // forbid the gallery from scanning media files
            ExternalStorage.setNoMedia(base);
            built = true;
            return base;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public String getCachesDirectory() {
        return Paths.appendPathComponent(getRoot(), "caches");
    }

    public String getTemporaryDirectory() {
        return Paths.appendPathComponent(getRoot(), "tmp");
    }

    /**
     *  Get cache file path: "/sdcard/chat.dim.sechat/caches/{XX}/{YY}/{filename}"
     *
     * @param filename - cache file name
     * @return cache file path
     */
    public String getCacheFilePath(String filename) {
        assert filename.length() > 4 : "filename too short " + filename;
        String dir = getCachesDirectory();
        String xx = filename.substring(0, 2);
        String yy = filename.substring(2, 4);
        return Paths.appendPathComponent(dir, xx, yy, filename);
    }

    /**
     *  Get temporary file path: "/sdcard/chat.dim.sechat/tmp/{filename}"
     *
     * @param filename - temporary file name
     * @return temporary file path
     */
    public String getTemporaryFilePath(String filename) {
        String dir = getTemporaryDirectory();
        return Paths.appendPathComponent(dir, filename);
    }

    /**
     *  Add an upload task
     *
     * @param url      - target URL
     * @param name     - var name in form
     * @param filename - filename
     * @param data     - file data
     */
    public void upload(String url, String name, String filename, byte[] data) {
        UploadTask task = new UploadTask(url, name, filename, data, getDelegate());
        Lock writeLock = uploadLock.writeLock();
        writeLock.lock();
        try {
            // check duplicated task
            if (!uploadTasks.contains(task)) {
                uploadTasks.add(task);
            }
        } finally {
            writeLock.unlock();
        }
    }

    /**
     *  Add a download task
     *
     * @param url      - target URL
     * @return cached file path if already downloaded; null for waiting to download
     */
    public String download(String url) {
        DownloadTask task = new DownloadTask(url, getDelegate());
        String path = task.getFilePath();
        if (path != null) {
            // already downloaded
            return path;
        }
        Lock writeLock = downloadLock.writeLock();
        writeLock.lock();
        try {
            // check duplicated task
            if (!downloadTasks.contains(task)) {
                downloadTasks.add(task);
            }
        } finally {
            writeLock.unlock();
        }
        // waiting to download
        return null;
    }

    private UploadTask nextUploadTask() {
        UploadTask task = null;
        Lock writeLock = uploadLock.writeLock();
        writeLock.lock();
        try {
            if (uploadTasks.size() > 0) {
                task = uploadTasks.remove(0);
            }
        } finally {
            writeLock.unlock();
        }
        return task;
    }

    private DownloadTask nextDownloadTask() {
        DownloadTask task = null;
        Lock writeLock = downloadLock.writeLock();
        writeLock.lock();
        try {
            if (downloadTasks.size() > 0) {
                task = downloadTasks.remove(0);
            }
        } finally {
            writeLock.unlock();
        }
        return task;
    }

    public void start() {
        forceStop();
        running = true;
        Thread thr = new Thread(this);
        thr.setDaemon(true);
        thr.start();
        thread = thr;
    }

    private void forceStop() {
        running = false;
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

    public void stop() {
        forceStop();
    }

    @Override
    public void run() {
        idle(1024);
        while (running) {
            if (process()) {
                // it's busy now, continue to process next task
                continue;
            }
            // no job to do now, have a rest. ^_^
            idle();
        }
    }

    protected void idle() {
        idle(512);
    }

    public static void idle(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean process() {
        UploadTask uploadTask = null;
        DownloadTask downloadTask = null;
        // 1. upload one
        try {
            uploadTask = nextUploadTask();
            if (uploadTask != null) {
                uploadTask.run();
            }
        } catch (Exception e) {
            e.printStackTrace();
            idle(1024);
        }
        // 2. download one
        try {
            downloadTask = nextDownloadTask();
            if (downloadTask != null) {
                downloadTask.run();
            }
        } catch (Exception e) {
            e.printStackTrace();
            idle(1024);
        }
        return uploadTask != null || downloadTask != null;
    }
}
