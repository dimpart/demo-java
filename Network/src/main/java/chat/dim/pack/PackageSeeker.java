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
package chat.dim.pack;

import chat.dim.type.ByteArray;
import chat.dim.type.Pair;

public abstract class PackageSeeker<H, P> {

    private final byte[] magicCode;
    private final int magicOffset;
    private final int maxHeadLength;

    public PackageSeeker(byte[] code, int offset, int headLength) {
        super();
        magicCode = code;
        magicOffset = offset;
        maxHeadLength = headLength;
    }

    /**
     *  Get package header from data buffer
     *
     * @param data - data received
     * @return Header
     */
    public abstract H parseHeader(ByteArray data);

    /**
     *  Get length of header
     *
     * @param head - package header
     * @return header length
     */
    public abstract int getHeaderLength(H head);

    /**
     *  Get body length from header
     *
     * @param head - package header
     * @return body length
     */
    public abstract int getBodyLength(H head);

    /**
     *  Create package with buffer, head & body
     *
     * @param data - data buffer
     * @param head - package head
     * @param body - package body
     * @return Package
     */
    public abstract P createPackage(ByteArray data, H head, ByteArray body);

    /**
     *  Seek package header in received data buffer
     *
     * @param data - received data buffer
     * @return header & it's offset, -1 on data error
     */
    public Pair<H, Integer> seekHeader(ByteArray data) {
        int dadtaLen = data.getSize();
        int start = 0;
        int offset;
        int remaining;
        H head;
        while (start < dadtaLen) {
            // try to parse header
            head = parseHeader(data.slice(start));
            if (head != null) {
                // got header with start position
                return new Pair<>(head, start);
            }
            // header not found, check remaining data
            remaining = dadtaLen - start;
            if (remaining < maxHeadLength) {
                // waiting for more data
                break;
            }
            // data error, locate next header
            offset = nextOffset(data, start + 1);
            if (offset < 0) {
                // header not found
                if (remaining < 65536) {
                    // waiting for more data
                    break;
                }
                // skip the whole buffer
                return new Pair<>(null, -1);
            }
            // try again from new offset
            start += offset;
        }
        // header not found, waiting for more data
        return new Pair<>(null, start);
    }

    // locate next header
    private int nextOffset(ByteArray data, int start) {
        start = magicOffset + start;
        int end = start + magicCode.length;
        if (end > data.getSize()) {
            // not enough data
            return -1;
        }
        int offset = data.find(magicCode, start);
        if (offset < 0) {
            // header not found
            return -1;
        }
        //assert offset > magicOffset : "magic code error: " + data;
        return offset - magicOffset;
    }

    /**
     *  Seek data package from received data buffer
     *
     * @param data - received data buffer
     * @return package & it's offset, -1 on data error
     */
    public Pair<P, Integer> seekPackage(ByteArray data) {
        // 1. seek header in received data
        Pair<H, Integer> result = seekHeader(data);
        H head = result.first;
        int offset = result.second;
        if (offset < 0) {
            // data error, ignore the whole buffer
            return new Pair<>(null, -1);
        } else if (head == null) {
            // header not found
            return new Pair<>(null, offset);
        } else if (offset > 0) {
            // skip the error part
            data = data.slice(offset);
        }
        // 2. check length
        int dataLen = data.getSize();
        int headLen = getHeaderLength(head);
        int bodyLen = getBodyLength(head);
        int packLen;
        if (bodyLen < 0) {
            packLen = dataLen;
        } else {
            packLen = headLen + bodyLen;
        }
        // check data buffer
        if (dataLen < packLen) {
            // package not completed, waiting for more data
            return new Pair<>(null, offset);
        } else if (dataLen > packLen) {
            // cut the tail
            data = data.slice(0, packLen);
        }
        // OK
        ByteArray body = data.slice(headLen);
        P pack = createPackage(data, head, body);
        return new Pair<>(pack, offset);
    }
}
