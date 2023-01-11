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

import java.util.Date;

import chat.dim.dbi.DocumentDBI;
import chat.dim.mem.CacheHolder;
import chat.dim.mem.CacheManager;
import chat.dim.mem.CachePair;
import chat.dim.mem.CachePool;
import chat.dim.protocol.Document;
import chat.dim.protocol.ID;
import chat.dim.sqlite.DatabaseConnector;
import chat.dim.sqlite.account.DocumentTable;

public class DocumentDatabase implements DocumentDBI {

    private final DocumentStorage documentStorage;
    private final DocumentTable documentTable;

    private final CachePool<ID, Document> documentCache;

    public DocumentDatabase(String rootDir, String publicDir, String privateDir, DatabaseConnector sqliteConnector) {
        super();
        documentStorage = new DocumentStorage(rootDir, publicDir, privateDir);
        documentTable = new DocumentTable(sqliteConnector);
        CacheManager man = CacheManager.getInstance();
        documentCache = man.getPool("document");
    }

    public void showInfo() {
        documentStorage.showInfo();
    }

    //
    //  Document DBI
    //

    @Override
    public boolean saveDocument(Document doc) {
        ID identifier = doc.getIdentifier();
        String type = doc.getType();
        // 0. check old record with time
        Document old = getDocument(identifier, type);
        if (old != null) {
            Date oldTime = old.getTime();
            Date newTime = doc.getTime();
            if (newTime != null && oldTime != null) {
                if (oldTime.getTime() >= newTime.getTime()) {
                    // document expired, drop it
                    return false;
                }
            }
        }
        // 1. update memory cache
        documentCache.update(identifier, doc, 3600 * 1000, 0);
        // 2. update sqlite
        documentTable.saveDocument(doc);
        // 3. store into local storage
        return documentStorage.saveDocument(doc);
    }

    @Override
    public Document getDocument(ID entity, String type) {
        long now = System.currentTimeMillis();
        Document doc = null;
        CacheHolder<Document> holder = null;
        // 1. check memory cache
        CachePair<Document> pair = documentCache.fetch(entity, now);
        if (pair != null) {
            doc = pair.value;
            holder = pair.holder;
        }
        if (doc == null) {
            // cache empty
            if (holder == null) {
                // document not load yet, wait to load
                documentCache.update(entity, null, 128 * 1000, now);
            } else {
                if (holder.isAlive(now)) {
                    // document not exists
                    return null;
                }
                // document expired, wait to reload
                holder.renewal(128 * 1000, now);
            }
            // 2. check sqlite
            doc = documentTable.getDocument(entity, type);
            if (doc == null) {
                // 3. check local storage
                doc = documentStorage.getDocument(entity, type);
            }
            // update memory cache
            documentCache.update(entity, doc, 36000 * 1000, now);
        }
        // OK, return cached value
        return doc;
    }
}
