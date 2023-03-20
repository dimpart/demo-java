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

import java.lang.ref.WeakReference;
import java.net.URL;

import chat.dim.protocol.ID;

/**
 *  Upload Request
 *  ~~~~~~~~~~~~~~
 *  waiting task
 *
 *  properties:
 *      url      - upload API
 *      path     - temporary file path
 *      secret   - authentication key
 *      name     - form var name ('avatar' or 'file')
 *      sender   - message sender
 *      delegate - callback
 */
public class UploadRequest extends AbstractTask {

    public final byte[] secret;     // authentication algorithm: hex(md5(data + secret + salt))

    public final String name;       // form var

    public final ID sender;         // message sender

    private final WeakReference<UploadDelegate> delegateRef;

    public UploadRequest(URL url, String path, byte[] key, String var, ID from, UploadDelegate delegate) {
        super(url, path);
        secret = key;
        name = var;
        sender = from;
        delegateRef = new WeakReference<>(delegate);
    }

    public UploadDelegate getDelegate() {
        return delegateRef.get();
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof UploadRequest) {
            if (super.equals(other)) {
                // same object
                return true;
            }
            UploadRequest task = (UploadRequest) other;
            return path.equals(task.path);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return path.hashCode();
    }

    @Override
    public String toString() {
        return String.format("<%s api=\"%s\" sender=\"%s\" name=\"%s\" path=\"%s\" />",
                this.getClass().getName(), url, sender, name, path);
    }
}
