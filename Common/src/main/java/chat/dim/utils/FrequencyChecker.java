/* license: https://mit-license.org
 *
 *  DIM-SDK : Decentralized Instant Messaging Software Development Kit
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

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import chat.dim.type.Duration;

/**
 *  Frequency checker for duplicated queries
 */
public class FrequencyChecker <K> {

    private final Duration expires;
    private final Map<K, Date> records = new HashMap<>();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    public FrequencyChecker(Duration lifeSpan) {
        super();
        expires = lifeSpan;
    }

    private boolean checkExpired(K key, Date now) {
        Date expired = records.get(key);
        if (expired != null && expired.after(now)) {
            // record exists and not expired yet
            return false;
        }
        records.put(key, expires.addTo(now));
        return true;
    }

    private boolean forceExpired(K key, Date now) {
        records.put(key, expires.addTo(now));
        return true;
    }
    public boolean isExpired(K key, Date now, boolean force) {
        boolean expired;
        Lock writeLock = lock.writeLock();
        writeLock.lock();
        try {
            if (now == null) {
                now = new Date();
            }
            // if force == true:
            //     ignore last updated time, force to update now
            // else:
            //     check last update time
            if (force) {
                expired = forceExpired(key, now);
            } else {
                expired = checkExpired(key, now);
            }
        } finally {
            writeLock.unlock();
        }
        return expired;
    }
}
