/* license: https://mit-license.org
 *
 *  HTTP
 *
 *                                Written in 2023 by Moky <albert.moky@gmail.com>
 *
 * ==============================================================================
 * The MIT License (MIT)
 *
 * Copyright (c) 2023 Albert Moky
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

import java.net.URL;

/**
 *  Base Task
 */
class AbstractTask {

    public static long EXPIRES = 300 * 1000;

    public final URL url;      // remote URL
    public final String path;  // temporary file path

    private long lastActive;   // last update time
    private int flag;

    AbstractTask(URL remoteURL, String filePath) {
        super();
        url = remoteURL;
        path = filePath;

        lastActive = 0;
        flag = 0;
    }

    /**
     *  Update active time
     */
    void touch() {
        lastActive = System.currentTimeMillis();
    }

    void onError() {
        assert flag == 0 : "flag updated before";
        flag = -1;
    }

    void onSuccess() {
        assert flag == 0 : "flag updated before";
        flag = 1;
    }

    void onFinished() {
        assert flag != 0 : "flag error: " + flag;
        if (flag == 1) {
            flag = 2;
        }
    }

    TaskStatus getStatus() {
        if (flag == -1) {
            return TaskStatus.Error;
        } else if (flag == 1) {
            return TaskStatus.Success;
        } else if (flag == 2) {
            return TaskStatus.Finished;
        } else if (lastActive == 0) {
            return TaskStatus.Waiting;
        }
        long now = System.currentTimeMillis();
        long expired = lastActive + EXPIRES;
        if (now < expired) {
            return TaskStatus.Running;
        } else {
            return TaskStatus.Expired;
        }
    }
}

enum TaskStatus {
    Error  (-1),
    Waiting (0),  // initialized
    Running (1),  // uploading/downloading
    Success (2),  // upload/download completed, calling delegates
    Expired (3),  // long time no response, task failed
    Finished(4);  // task finished

    final int value;
    TaskStatus(int status) {
        value = status;
    }
}
