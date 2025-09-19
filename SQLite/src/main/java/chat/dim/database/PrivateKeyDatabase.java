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

import java.util.ArrayList;
import java.util.List;

import chat.dim.dbi.PrivateKeyDBI;
import chat.dim.mem.CacheHolder;
import chat.dim.mem.CacheManager;
import chat.dim.mem.CachePair;
import chat.dim.mem.CachePool;
import chat.dim.protocol.DecryptKey;
import chat.dim.protocol.ID;
import chat.dim.protocol.PrivateKey;
import chat.dim.sqlite.DatabaseConnector;
import chat.dim.sqlite.account.PrivateKeyTable;

public class PrivateKeyDatabase implements PrivateKeyDBI {

    private final PrivateKeyTable privateKeyTable;

    private final CachePool<ID, PrivateKey> idKeyCache;
    private final CachePool<ID, List<DecryptKey>> msgKeysCache;

    public PrivateKeyDatabase(DatabaseConnector sqliteConnector) {
        super();
        privateKeyTable = new PrivateKeyTable(sqliteConnector);
        CacheManager man = CacheManager.getInstance();
        idKeyCache = man.getPool("private_id_key");
        msgKeysCache = man.getPool("private_msg_keys");
    }

    //
    //  PrivateKey DBI
    //

    @Override
    public boolean savePrivateKey(PrivateKey key, String type, ID user) {
        long now = System.currentTimeMillis();
        // 1. update memory cache
        if (type != null && type.equals(PrivateKeyDBI.META)) {
            // update 'id_key'
            idKeyCache.update(user, key, 36000*1000, now);
        } else {
            // add to old keys
            List<DecryptKey> decryptKeys = getPrivateKeysForDecryption(user);
            List<PrivateKey> privateKeys = PrivateKeyDBI.convertPrivateKeys(decryptKeys);
            privateKeys = PrivateKeyDBI.insertKey(key, privateKeys);
            if (privateKeys == null) {
                // key already exists, nothing changed
                return false;
            }
            // update 'msg_keys"
            decryptKeys = PrivateKeyDBI.convertDecryptKeys(privateKeys);
            msgKeysCache.update(user, decryptKeys, 36000*1000, now);
        }
        // 2. update sqlite
        return privateKeyTable.savePrivateKey(key, type, user);
    }

    @Override
    public List<DecryptKey> getPrivateKeysForDecryption(ID user) {
        long now = System.currentTimeMillis();
        List<DecryptKey> decryptKeys = null;
        CacheHolder<List<DecryptKey>> holder = null;
        // 1. check memory cache
        CachePair<List<DecryptKey>> pair = msgKeysCache.fetch(user, now);
        if (pair != null) {
            decryptKeys = pair.value;
            holder = pair.holder;
        }
        if (decryptKeys == null) {
            // cache empty
            if (holder == null) {
                // msg keys not load yet, wait to load
                msgKeysCache.update(user, null, 128 * 1000, now);
            } else {
                if (holder.isAlive(now)) {
                    // msg keys not exists
                    return new ArrayList<>();
                }
                // msg keys expired, wait to reload
                holder.renewal(128 * 1000, now);
            }
            // 2. check sqlite
            decryptKeys = privateKeyTable.getPrivateKeysForDecryption(user);
            if (decryptKeys == null) {
                // placeholder
                decryptKeys = new ArrayList<>();
            }
            // update memory cache
            msgKeysCache.update(user, decryptKeys, 36000 * 1000, now);
        }
        // OK, return cached value
        return decryptKeys;
    }

    @Override
    public PrivateKey getPrivateKeyForSignature(ID user) {
        // TODO: support multi private keys
        return getPrivateKeyForVisaSignature(user);
    }

    @Override
    public PrivateKey getPrivateKeyForVisaSignature(ID user) {
        long now = System.currentTimeMillis();
        PrivateKey privateKey = null;
        CacheHolder<PrivateKey> holder = null;
        // 1. check memory cache
        CachePair<PrivateKey> pair = idKeyCache.fetch(user, now);
        if (pair != null) {
            privateKey = pair.value;
            holder = pair.holder;
        }
        if (privateKey == null) {
            // cache empty
            if (holder == null) {
                // id key not load yet, wait to load
                idKeyCache.update(user, null, 128 * 1000, now);
            } else {
                if (holder.isAlive(now)) {
                    // id key not exists
                    return null;
                }
                // id key expired, wait to reload
                holder.renewal(128 * 1000, now);
            }
            // 2. check sqlite
            privateKey = privateKeyTable.getPrivateKeyForVisaSignature(user);
            // update memory cache
            idKeyCache.update(user, privateKey, 36000 * 1000, now);
        }
        // OK, return cached value
        return privateKey;
    }
}
