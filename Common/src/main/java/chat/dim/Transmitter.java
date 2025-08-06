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

import chat.dim.protocol.Content;
import chat.dim.protocol.ID;
import chat.dim.protocol.InstantMessage;
import chat.dim.protocol.ReliableMessage;
import chat.dim.type.Pair;

public interface Transmitter {

    /**
     *  Send content from sender to receiver with priority
     *
     * @param sender   - from where, null for current user
     * @param receiver - to where
     * @param content  - message content
     * @param priority - smaller is faster
     * @return (iMsg, None) on error
     */
    Pair<InstantMessage, ReliableMessage> sendContent(Content content, ID sender, ID receiver, int priority);

    /**
     *  Send instant message with priority
     *
     * @param iMsg     - plain message
     * @param priority - smaller is faster
     * @return null on error
     */
    ReliableMessage sendInstantMessage(InstantMessage iMsg, int priority);

    /**
     *  Send reliable message with priority
     *
     * @param rMsg     - encrypted &amp; signed message
     * @param priority - smaller is faster
     * @return false on error
     */
    boolean sendReliableMessage(ReliableMessage rMsg, int priority);
}
