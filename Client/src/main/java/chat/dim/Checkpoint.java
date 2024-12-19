/* license: https://mit-license.org
 *
 *  DIM-SDK : Decentralized Instant Messaging Software Development Kit
 *
 *                                Written in 2024 by Moky <albert.moky@gmail.com>
 *
 * ==============================================================================
 * The MIT License (MIT)
 *
 * Copyright (c) 2024 Albert Moky
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

import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import chat.dim.protocol.ReliableMessage;
import chat.dim.type.Duration;

public enum Checkpoint {

    INSTANCE;

    public static Checkpoint getInstance() {
        return INSTANCE;
    }

    Checkpoint() {
    }

    private final SigPool pool = new SigPool();

    public boolean checkDuplicatedMessage(ReliableMessage msg) {
        boolean repeated = pool.checkDuplicated(msg);
        Date now = msg.getTime();
        if (now != null) {
            pool.purge(now);
        }
        return repeated;
    }

    public String getSig(ReliableMessage msg) {
        String sig = msg.getString("signature", null);
        return SigPool.getSig(sig, 8);
    }

}

/**
 *  Signature pool for messages
 */
class SigPool {

    static Duration EXPIRES = Duration.ofMinutes(60);

    // "signature:receiver" => Date Time
    private final Map<String, Date> caches = new HashMap<>();

    private Date nextTime;

    /**
     *  Remove expired traces
     */
    boolean purge(Date now) {
        Date next = nextTime;
        if (next != null) {
            if (now == null || now.before(next)) {
                return false;
            }
        }
        now = new Date();
        if (next != null && now.before(next)) {
            return false;
        } else {
            // purge it next 5 minutes
            nextTime = Duration.ofMinutes(5).addTo(now);
        }
        Date expired = EXPIRES.subtractFrom(now);
        Iterator<Map.Entry<String, Date>> it = caches.entrySet().iterator();
        Map.Entry<String, Date> entry;
        Date when;
        while (it.hasNext()) {
            when = it.next().getValue();
            if (when == null || when.before(expired)) {
                it.remove();
            }
        }
        return true;
    }

    boolean checkDuplicated(ReliableMessage msg) {
        String sig = msg.getString("signature", null);
        if (sig == null) {
            assert false : "message error: " + msg;
            return true;
        } else {
            sig = getSig(sig, 16);
        }
        String address = msg.getReceiver().getAddress().toString();
        String tag = sig + ":" + address;
        if (caches.containsKey(tag)) {
            return true;
        }
        // cache not found, create a new one with message time
        Date when = msg.getTime();
        if (when == null) {
            when = new Date();
        }
        caches.put(tag, when);
        return false;
    }

    static String getSig(String signature, int maxLen) {
        assert maxLen > 0;
        int len = signature == null ? 0 : signature.length();
        return len <= maxLen ? signature : signature.substring(len - maxLen);
    }

}
