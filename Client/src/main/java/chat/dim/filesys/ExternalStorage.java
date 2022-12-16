/* license: https://mit-license.org
 *
 *  File System
 *
 *                                Written in 2019 by Moky <albert.moky@gmail.com>
 *
 * ==============================================================================
 * The MIT License (MIT)
 *
 * Copyright (c) 2019 Albert Moky
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

import java.io.File;
import java.io.IOException;

import chat.dim.format.JSON;
import chat.dim.format.UTF8;

/**
 *  RAM access
 */
public abstract class ExternalStorage extends PathUtils {

    /**
     *  Base Directory
     */
    private static String base = "/tmp/.dim";  // "/sdcard/chat.dim.sechat"
    private static boolean built = false;

    public static String getRoot() {
        if (built) {
            return base;
        }
        try {
            mkdirs(base);
            // forbid the gallery from scanning media files
            String path = appendPathComponent(base, ".nomedia");
            if (!exists(path)) {
                saveText("Moky loves May Lee forever!", path);
            }
            built = true;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return base;
    }
    public static void setRoot(String dir) {
        assert dir != null : "root directory should not empty";
        if (dir.length() > separator.length() && dir.endsWith(separator)) {
            // remove last '/'
            dir = dir.substring(0, dir.length() - separator.length());
        }
        dir = tidy(dir);
        if (dir.equals(base)) {
            // base dir not change
            return;
        }
        base = dir;
        built = false;
    }

    /**
     *  Check whether file exists
     *
     * @param path - file path
     * @return True on exists
     */
    public static boolean exists(String path) {
        Storage file = new Storage();
        return file.exists(path);
    }

    /**
     *  Create directory
     *
     * @param path - dir path
     * @return absolute path
     */
    public static String mkdirs(String path) throws IOException {
        File dir = new File(path);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("failed to create directory: " + path);
        }
        return path;
    }

    /**
     *  Delete file
     *
     * @param path - file path
     * @return true on success
     */
    public static boolean delete(String path) throws IOException {
        Storage file = new Storage();
        return file.remove(abs(path, getRoot()));
    }

    //-------- read

    private static byte[] load(String path) throws IOException {
        Storage file = new Storage();
        file.read(abs(path, getRoot()));
        return file.getData();
    }

    /**
     *  Load binary data from file
     *
     * @param path - file path
     * @return file data
     */
    public static byte[] loadBinary(String path) throws IOException {
        byte[] data = load(path);
        if (data == null) {
            throw new IOException("failed to load binary file: " + path);
        }
        return data;
    }

    /**
     *  Load text from file path
     *
     * @param path - file path
     * @return text string
     */
    public static String loadText(String path) throws IOException {
        byte[] data = load(path);
        if (data == null) {
            throw new IOException("failed to load text file: " + path);
        }
        return UTF8.decode(data);
    }

    /**
     *  Load JSON from file path
     *
     * @param path - file path
     * @return Map/List object
     */
    public static Object loadJSON(String path) throws IOException {
        byte[] data = load(path);
        if (data == null) {
            throw new IOException("failed to load JSON file: " + path);
        }
        return JSON.decode(UTF8.decode(data));
    }

    //-------- write

    private static int save(byte[] data, String path) throws IOException {
        Storage file = new Storage();
        file.setData(data);
        return file.write(abs(path, getRoot()));
    }

    /**
     *  Save data into binary file
     *
     * @param data - binary data
     * @param path - file path
     * @return true on success
     */
    public static int saveBinary(byte[] data, String path) throws IOException {
        int len = save(data, path);
        if (len != data.length) {
            throw new IOException("failed to save binary file: " + path);
        }
        return len;
    }

    /**
     *  Save string into Text file
     *
     * @param text - text string
     * @param path - file path
     * @return true on success
     */
    public static int saveText(String text, String path) throws IOException {
        byte[] data = UTF8.encode(text);
        int len = save(data, path);
        if (len != data.length) {
            throw new IOException("failed to save text file: " + path);
        }
        return len;
    }

    /**
     *  Save Map/List into JSON file
     *
     * @param object - Map/List object
     * @param path - file path
     * @return true on success
     */
    public static int saveJSON(Object object, String path) throws IOException {
        byte[] json = UTF8.encode(JSON.encode(object));
        int len = save(json, path);
        if (len != json.length) {
            throw new IOException("failed to save text file: " + path);
        }
        return len;
    }
}
