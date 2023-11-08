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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import chat.dim.port.Departure;
import chat.dim.protocol.ID;
import chat.dim.protocol.ReliableMessage;
import chat.dim.utils.Log;

public final class MessageQueue {

    private final List<Integer> priorities = new ArrayList<>();
    private final Map<Integer, List<MessageWrapper>> fleets = new HashMap<>();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    /**
     *  Append message with departure ship
     *
     * @param rMsg - outgoing message
     * @param ship - departure ship
     * @return false on duplicated
     */
    public boolean append(ReliableMessage rMsg, Departure ship) {
        boolean ok = true;
        Lock writeLock = lock.writeLock();
        writeLock.lock();
        try {
            // 1. choose an array with priority
            int priority = ship.getPriority();
            List<MessageWrapper> array = fleets.get(priority);
            if (array == null) {
                // 1.1. create new array for this priority
                array = new ArrayList<>();
                fleets.put(priority, array);
                // 1.2. insert the priority in a sorted list
                insert(priority);
            } else {
                // 1.3. check duplicated
                Object signature = rMsg.get("signature");
                assert signature != null : "signature not found: " + rMsg;
                ReliableMessage item;
                for (MessageWrapper wrapper : array) {
                    item = wrapper.getMessage();
                    if (isDuplicated(item, rMsg)) {
                        Log.warning("[QUEUE] duplicated message: " + signature);
                        ok = false;
                        break;
                    }
                }
            }
            if (ok) {
                // 2. append with wrapper
                MessageWrapper wrapper = new MessageWrapper(rMsg, ship);
                array.add(wrapper);
            }
        } finally {
            writeLock.unlock();
        }
        return ok;
    }
    private boolean isDuplicated(ReliableMessage msg1, ReliableMessage msg2) {
        if (msg1 == null || msg2 == null) {
            return false;
        }
        Object sig1 = msg1.get("signature");
        Object sig2 = msg2.get("signature");
        if (sig1 == null || sig2 == null) {
            assert false : "signature should not empty here: " + msg1 + ", " + msg2;
            return false;
        } else if (!sig1.equals(sig2)) {
            return false;
        }
        // maybe it's a group message split for every members,
        // so we still need to check receiver here.
        ID to1 = msg1.getReceiver();
        ID to2 = msg2.getReceiver();
        if (to1 == null || to2 == null) {
            assert false : "receiver should not empty here: " + msg1 + ", " + msg2;
            return false;
        }
        return to1.equals(to2);
    }
    private void insert(int priority) {
        int total = priorities.size();
        int index = 0, value;
        // seeking position for new priority
        for (; index < total; ++index) {
            value = priorities.get(index);
            if (value == priority) {
                // duplicated
                return;
            } else if (value > priority) {
                // got it
                break;
            }
            // current value is smaller than the new value,
            // keep going
        }
        // insert new value before the bigger one
        priorities.add(index, priority);
    }

    /**
     *  Get next new message
     *
     * @return MessageWrapper
     */
    public MessageWrapper next() {
        MessageWrapper target = null;
        Lock writeLock = lock.writeLock();
        writeLock.lock();
        try {
            for (int priority : priorities) {
                // get first task
                List<MessageWrapper> array = fleets.get(priority);
                if (array != null && array.size() > 0) {
                    target = array.remove(0);
                    break;
                }
            }
        } finally {
            writeLock.unlock();
        }
        return target;
    }

    public void purge() {
        Lock writeLock = lock.writeLock();
        writeLock.lock();
        try {
            Iterator<Integer> pit = priorities.iterator();
            int prior;
            List<MessageWrapper> array;
            while (pit.hasNext()) {
                prior = pit.next();
                array = fleets.get(prior);
                if (array == null) {
                    // this priority is empty
                    pit.remove();
                } else if (array.size() == 0) {
                    // this priority is empty
                    fleets.remove(prior);
                    pit.remove();
                }
            }
        } finally {
            writeLock.unlock();
        }
    }
}
