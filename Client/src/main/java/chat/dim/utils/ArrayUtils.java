/* license: https://mit-license.org
 *
 *  DIMP : Decentralized Instant Messaging Protocol
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
package chat.dim.utils;

import java.util.ArrayList;
import java.util.List;

public interface ArrayUtils {

    byte SEPARATOR = '\n';

    static List<byte[]> splitLines(byte[] data) {
        return split(data, SEPARATOR);
    }
    static byte[] joinLines(List<byte[]> packages) {
        return join(packages, SEPARATOR);
    }

    static List<byte[]> split(byte[] data, byte separator) {
        List<byte[]> lines = new ArrayList<>();
        byte[] tmp;
        int pos1 = 0, pos2;
        while (pos1 < data.length) {
            pos2 = pos1;
            while (pos2 < data.length) {
                if (data[pos2] == separator) {
                    break;
                } else {
                    ++pos2;
                }
            }
            if (pos2 > pos1) {
                tmp = new byte[pos2 - pos1];
                System.arraycopy(data, pos1, tmp, 0, pos2 - pos1);
                lines.add(tmp);
            }
            pos1 = pos2 + 1;  // skip '\n'
        }
        return lines;
    }

    static byte[] join(List<byte[]> packages, byte separator) {
        final int count = packages.size();
        int index;
        // get buffer size
        int size = 0;
        byte[] pack;
        for (index = 0; index < count; ++index) {
            pack = packages.get(index);
            size += pack.length + 1;
        }
        if (size == 0) {
            return null;
        } else {
            size -= 1;  // remove last '\n'
        }
        // combine packages
        byte[] buffer = new byte[size];
        // copy first package
        pack = packages.get(0);
        System.arraycopy(pack, 0, buffer, 0, pack.length);
        // copy the others
        int offset = pack.length;
        for (index = 1; index < count; ++index) {
            // set separator
            buffer[offset] = separator;
            ++offset;
            // copy package data
            pack = packages.get(index);
            System.arraycopy(pack, 0, buffer, offset, pack.length);
            offset += pack.length;
        }
        return buffer;
    }
}
