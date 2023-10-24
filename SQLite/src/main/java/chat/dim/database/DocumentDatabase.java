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

import java.util.List;

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

    private final DocumentTable documentTable;

    private final CachePool<ID, List<Document>> documentCache;

    public DocumentDatabase(DatabaseConnector sqliteConnector) {
        super();
        documentTable = new DocumentTable(sqliteConnector);
        CacheManager man = CacheManager.getInstance();
        documentCache = man.getPool("document");
    }

    //
    //  Document DBI
    //

    @Override
    public boolean saveDocument(Document doc) {
        // TODO: must check old records before calling this
        ID identifier = doc.getIdentifier();
        // 1. clear for reload
        documentCache.erase(identifier, 0);
        // 2. update sqlite
        return documentTable.saveDocument(doc);
    }

    @Override
    public boolean clearDocuments(ID entity, String type) {
        // 1. clear for reload
        documentCache.erase(entity, 0);
        // 2. update sqlite
        return documentTable.clearDocuments(entity, type);
    }

    @Override
    public List<Document> getDocuments(ID entity) {
        long now = System.currentTimeMillis();
        List<Document> documents = null;
        CacheHolder<List<Document>> holder = null;
        // 1. check memory cache
        CachePair<List<Document>> pair = documentCache.fetch(entity, now);
        if (pair != null) {
            documents = pair.value;
            holder = pair.holder;
        }
        if (documents == null) {
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
            documents = documentTable.getDocuments(entity);
            // update memory cache
            documentCache.update(entity, documents, 36000 * 1000, now);
        }
        // OK, return cached value
        return documents;
    }
}
