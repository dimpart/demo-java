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

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import chat.dim.protocol.ID;

public enum QueryFrequencyChecker {

    INSTANCE;

    public static QueryFrequencyChecker getInstance() {
        return INSTANCE;
    }

    // query for meta
    private final FrequencyChecker<ID> metaQueries;
    private final ReadWriteLock metaLock;

    // query for document
    private final FrequencyChecker<ID> documentQueries;
    private final ReadWriteLock documentLock;

    // query for group members
    private final FrequencyChecker<ID> membersQueries;
    private final ReadWriteLock membersLock;

    QueryFrequencyChecker() {
        // each query will be expired after 10 minutes
        final int QUERY_EXPIRES = 600 * 1000;  // milliseconds
        metaQueries = new FrequencyChecker<>(QUERY_EXPIRES);
        metaLock = new ReentrantReadWriteLock();
        documentQueries = new FrequencyChecker<>(QUERY_EXPIRES);
        documentLock = new ReentrantReadWriteLock();
        membersQueries = new FrequencyChecker<>(QUERY_EXPIRES);
        membersLock = new ReentrantReadWriteLock();
    }

    public boolean isMetaQueryExpired(ID identifier, long now) {
        boolean expired;
        Lock writeLock = metaLock.writeLock();
        writeLock.lock();
        try {
            expired = metaQueries.isExpired(identifier, now);
        } finally {
            writeLock.unlock();
        }
        return expired;
    }

    public boolean isDocumentQueryExpired(ID identifier, long now) {
        boolean expired;
        Lock writeLock = documentLock.writeLock();
        writeLock.lock();
        try {
            expired = documentQueries.isExpired(identifier, now);
        } finally {
            writeLock.unlock();
        }
        return expired;
    }

    public boolean isMembersQueryExpired(ID identifier, long now) {
        boolean expired;
        Lock writeLock = membersLock.writeLock();
        writeLock.lock();
        try {
            expired = membersQueries.isExpired(identifier, now);
        } finally {
            writeLock.unlock();
        }
        return expired;
    }
}
