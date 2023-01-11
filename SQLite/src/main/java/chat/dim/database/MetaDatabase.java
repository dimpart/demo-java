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

import chat.dim.dbi.MetaDBI;
import chat.dim.mem.CacheHolder;
import chat.dim.mem.CacheManager;
import chat.dim.mem.CachePair;
import chat.dim.mem.CachePool;
import chat.dim.protocol.ID;
import chat.dim.protocol.Meta;
import chat.dim.sqlite.DatabaseConnector;
import chat.dim.sqlite.account.MetaTable;

public class MetaDatabase implements MetaDBI {

    private final MetaStorage metaStorage;
    private final MetaTable metaTable;

    private final CachePool<ID, Meta> metaCache;

    public MetaDatabase(String rootDir, String publicDir, String privateDir, DatabaseConnector sqliteConnector) {
        super();
        metaStorage = new MetaStorage(rootDir, publicDir, privateDir);
        metaTable = new MetaTable(sqliteConnector);
        CacheManager man = CacheManager.getInstance();
        metaCache = man.getPool("meta");
    }

    public void showInfo() {
        metaStorage.showInfo();
    }

    //
    //  Meta DBI
    //

    @Override
    public boolean saveMeta(Meta meta, ID entity) {
        // 0. check old record
        Meta old = getMeta(entity);
        if (old != null) {
            // meta exists, no need to update it
            return true;
        }
        // 1. update memory cache
        metaCache.update(entity, meta, 36000 * 1000, 0);
        // 2. update sqlite
        metaTable.saveMeta(meta, entity);
        // 3. store into local storage
        return metaStorage.saveMeta(meta, entity);
    }

    @Override
    public Meta getMeta(ID entity) {
        long now = System.currentTimeMillis();
        Meta meta = null;
        CacheHolder<Meta> holder = null;
        // 1. check memory cache
        CachePair<Meta> pair = metaCache.fetch(entity, now);
        if (pair != null) {
            meta = pair.value;
            holder = pair.holder;
        }
        if (meta == null) {
            // cache empty
            if (holder == null) {
                // meta not load yet, wait to load
                metaCache.update(entity, null, 128 * 1000, now);
            } else {
                if (holder.isAlive(now)) {
                    // meta not exists
                    return null;
                }
                // meta expired, wait to reload
                holder.renewal(128 * 1000, now);
            }
            // 2. check sqlite
            meta = metaTable.getMeta(entity);
            if (meta == null) {
                // 3. check local storage
                meta = metaStorage.getMeta(entity);
            }
            // update memory cache
            metaCache.update(entity, meta, 36000 * 1000, now);
        }
        // OK, return cached value
        return meta;
    }
}
