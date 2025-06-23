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
package chat.dim.database;

import chat.dim.crypto.PlainKey;
import chat.dim.crypto.SymmetricAlgorithms;
import chat.dim.crypto.SymmetricKey;
import chat.dim.dbi.CipherKeyDBI;
import chat.dim.mem.CacheManager;
import chat.dim.mem.CachePair;
import chat.dim.mem.CachePool;
import chat.dim.protocol.ID;
import chat.dim.sqlite.DatabaseConnector;

public class CipherKeyDatabase implements CipherKeyDBI {

    private final CachePool<String, SymmetricKey> keyCache;

    public CipherKeyDatabase(DatabaseConnector sqliteConnector) {
        super();
        CacheManager man = CacheManager.getInstance();
        keyCache = man.getPool("cipher_key");
    }

    @Override
    public SymmetricKey getCipherKey(ID sender, ID receiver, boolean generate) {
        if (receiver.isBroadcast()) {
            return PlainKey.getInstance();
        }
        long now = System.currentTimeMillis();
        CachePair<SymmetricKey> pair = keyCache.fetch(sender + "->" + receiver, now);
        SymmetricKey key = pair == null ? null : pair.value;
        if (key == null && generate) {
            // generate and cache it
            key = SymmetricKey.generate(SymmetricAlgorithms.AES);
            assert key != null : "failed to generate symmetric key";
            keyCache.update(sender + "->" + receiver, key, 7*24*3600*1000, now);
        }
        return key;
    }

    @Override
    public void cacheCipherKey(ID sender, ID receiver, SymmetricKey key) {
        keyCache.update(sender + "->" + receiver, key, 7*24*3600*1000, 0);
    }
}
