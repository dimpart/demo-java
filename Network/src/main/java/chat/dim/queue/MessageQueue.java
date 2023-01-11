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
                    if (item != null && signature.equals(item.get("signature"))) {
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
            Iterator<MessageWrapper> iterator;
            List<MessageWrapper> array;
            MessageWrapper item;
            for (int priority : priorities) {
                // 1. get messages with priority
                array = fleets.get(priority);
                if (array == null) {
                    continue;
                }
                // 2. seeking new task in this priority
                iterator = array.iterator();
                while (iterator.hasNext()) {
                    item = iterator.next();
                    if (item.isVirgin()) {
                        // got it, mark sent
                        target = item;
                        item.mark();
                        break;
                    }
                }
                if (target != null) {
                    // got
                    break;
                }
            }
        } finally {
            writeLock.unlock();
        }
        return target;
    }

    private MessageWrapper eject(long now) {
        MessageWrapper target = null;
        Lock writeLock = lock.writeLock();
        writeLock.lock();
        try {
            Iterator<MessageWrapper> iterator;
            List<MessageWrapper> array;
            MessageWrapper item;
            for (int priority : priorities) {
                // 1. get messages with priority
                array = fleets.get(priority);
                if (array == null) {
                    continue;
                }
                // 2. seeking new task in this priority
                iterator = array.iterator();
                while (iterator.hasNext()) {
                    item = iterator.next();
                    if (item.getMessage() == null || item.isFailed(now)) {
                        // got it, remove from the queue
                        target = item;
                        iterator.remove();
                        break;
                    }
                }
                if (target != null) {
                    // got
                    break;
                }
            }
        } finally {
            writeLock.unlock();
        }
        return target;
    }

    public int purge() {
        int count = 0;
        long now = System.currentTimeMillis();
        MessageWrapper wrapper = eject(now);
        while (wrapper != null) {
            count += 1;
            // TODO: callback for failed task?
            wrapper = eject(now);
        }
        return count;
    }
}
