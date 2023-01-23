/* license: https://mit-license.org
 *
 *  File System
 *
 *                                Written in 2020 by Moky <albert.moky@gmail.com>
 *
 * ==============================================================================
 * The MIT License (MIT)
 *
 * Copyright (c) 2020 Albert Moky
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
package chat.dim.filesys;

import java.io.IOException;

import chat.dim.format.JSON;
import chat.dim.format.UTF8;

/**
 *  ROM access
 */
public abstract class Resources {

    private static byte[] load(String path) throws IOException {
        Resource resource = new Resource();
        resource.read(path);
        return resource.getData();
    }

    public static byte[] loadBinary(String path) throws IOException {
        byte[] data = load(path);
        if (data == null) {
            throw new IOException("failed to load binary file: " + path);
        }
        return data;
    }

    public static String loadText(String path) throws IOException {
        byte[] data = load(path);
        if (data == null) {
            throw new IOException("failed to load text file: " + path);
        }
        return UTF8.decode(data);
    }

    public static Object loadJSON(String path) throws IOException {
        byte[] data = load(path);
        if (data == null) {
            throw new IOException("failed to load JSON file: " + path);
        }
        return JSON.decode(UTF8.decode(data));
    }
}
