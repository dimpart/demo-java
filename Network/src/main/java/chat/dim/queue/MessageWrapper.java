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
package chat.dim.queue;

import java.util.List;

import chat.dim.port.Arrival;
import chat.dim.port.Departure;
import chat.dim.protocol.ReliableMessage;

public final class MessageWrapper implements Departure {

    private int flag;
    private ReliableMessage msg;
    private final Departure ship;

    public MessageWrapper(ReliableMessage rMsg, Departure departure) {
        super();
        flag = 0;
        msg = rMsg;
        ship = departure;
    }

    public void mark() {
        flag = 1;
    }

    public boolean isVirgin() {
        return flag == 0;
    }

    public ReliableMessage getMessage() {
        return msg;
    }

    @Override
    public Object getSN() {
        return ship.getSN();
    }

    @Override
    public int getPriority() {
        return ship.getPriority();
    }

    @Override
    public List<byte[]> getFragments() {
        return ship.getFragments();
    }

    @Override
    public boolean checkResponse(Arrival response) {
        return ship.checkResponse(response);
    }

    @Override
    public boolean isNew() {
        return ship.isNew();
    }

    @Override
    public boolean isDisposable() {
        return ship.isDisposable();
    }

    @Override
    public boolean isTimeout(long now) {
        return ship.isTimeout(now);
    }

    @Override
    public boolean isFailed(long now) {
        return ship.isFailed(now);
    }

    @Override
    public void touch(long now) {
        ship.touch(now);
    }

    //
    //  Callback
    //

    /**
     *  Callback on message appended to outgoing queue
     */
    public void onAppended() {
        // this message was assigned to the worker of StarGate,
        // update status
        flag = 2;
    }

    /**
     *  Callback on success to send out
     */
    public void onSent() {
        // success, remove message
        msg = null;
    }

    /**
     *  Callback on failed to send ship
     */
    public void onFailed(Throwable error) {
        flag = -1;
    }

    /**
     *  Callback on error, failed to append
     */
    public void onError(Throwable error) {

    }
}
