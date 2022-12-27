/* license: https://mit-license.org
 *
 *  File System
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
package chat.dim.filesys;

import java.util.ArrayList;
import java.util.List;

import chat.dim.utils.ArrayUtils;

public abstract class PathUtils extends Paths {

    /**
     *  Get parent directory
     *
     * @param path - full path
     * @return parent path
     */
    public static String parent(String path) {
        int pos;
        if (path.endsWith(separator)) {
            pos = path.lastIndexOf(separator, path.length() - separator.length());
        } else {
            pos = path.lastIndexOf(separator);
        }
        if (pos < 0) {
            // relative path?
            return null;
        } else if (pos == 0) {
            // root dir: "/"
            return separator;
        }
        return path.substring(0, pos);
    }

    /**
     *  Get absolute path
     *
     * @param relative - relative path
     * @param base     - base directory
     * @return absolute path
     */
    static String abs(String relative, String base) {
        assert base.length() > 0 && relative.length() > 0 : "paths error: " + base + ", " + relative;
        if (relative.startsWith(separator) || relative.indexOf(":") > 0) {
            // Linux   - "/filename"
            // Windows - "C:\\filename"
            // URL     - "file://filename"
            return relative;
        }
        String path;
        if (base.endsWith(separator)) {
            path = base + relative;
        } else {
            path = base + separator + relative;
        }
        if (path.contains("./")) {
            return tidy(path);
        } else {
            return path;
        }
    }

    /**
     *  Remove relative components in full path
     *
     * @param path - full path
     * @return absolute path
     */
    static String tidy(String path) {
        List<String> array = new ArrayList<>();
        String next;
        int left, right = 0;
        while (right >= 0) {
            left = right;
            right = path.indexOf(separator, left);
            if (right < 0) {
                // last component
                next = path.substring(left);
            } else {
                // next component (ends with the separator)
                right += separator.length();
                next = path.substring(left, right);
            }
            if (next.equals("../")) {
                // backward
                assert array.size() > 0 : "path error: " + path;
                array.remove(array.size() - 1);
            } else if (!next.equals("./")) {
                array.add(next);
            }
        }
        return ArrayUtils.join("", array);
    }
}
