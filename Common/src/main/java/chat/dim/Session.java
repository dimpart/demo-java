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
package chat.dim;

import java.net.SocketAddress;

import chat.dim.dbi.SessionDBI;
import chat.dim.protocol.ID;
import chat.dim.protocol.ReliableMessage;

public interface Session extends Transmitter {

    SessionDBI getDatabase();

    /**
     *  Get remote socket address
     *
     * @return host & port
     */
    SocketAddress getRemoteAddress();

    // session key
    String getKey();

    /**
     *  Update user ID
     *
     * @param identifier - login user ID
     * @return true on changed
     */
    boolean setIdentifier(ID identifier);
    ID getIdentifier();

    /**
     *  Update active flag
     *
     * @param active - flag
     * @param when   - now
     * @return true on changed
     */
    boolean setActive(boolean active, long when);
    boolean isActive();

    /**
     *  Pack message into a waiting queue
     *
     * @param msg      - network message
     * @param data     - serialized message
     * @param priority - smaller is faster
     * @return false on error
     */
    boolean queueMessagePackage(ReliableMessage msg, byte[] data, int priority);
}
