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

import chat.dim.dbi.UserDBI;
import chat.dim.mem.CacheHolder;
import chat.dim.mem.CacheManager;
import chat.dim.mem.CachePair;
import chat.dim.mem.CachePool;
import chat.dim.protocol.ID;
import chat.dim.sqlite.DatabaseConnector;
import chat.dim.sqlite.account.ContactTable;
import chat.dim.sqlite.account.UserTable;

public class UserDatabase implements UserDBI {

    private final UserTable userTable;
    private final ContactTable contactTable;

    private final CachePool<String, List<ID>> dimCache;
    private final CachePool<ID, List<ID>> contactCache;

    public UserDatabase(DatabaseConnector sqliteConnector) {
        super();
        userTable = new UserTable(sqliteConnector);
        contactTable = new ContactTable(sqliteConnector);
        CacheManager man = CacheManager.getInstance();
        dimCache = man.getPool("dim");
        contactCache = man.getPool("contacts");
    }

    @Override
    public List<ID> getLocalUsers() {
        long now = System.currentTimeMillis();
        List<ID> users = null;
        CacheHolder<List<ID>> holder = null;
        // 1. check memory cache
        CachePair<List<ID>> pair = dimCache.fetch("local_users", now);
        if (pair != null) {
            users = pair.value;
            holder = pair.holder;
        }
        if (users == null) {
            // cache empty
            if (holder == null) {
                // cache not load yet, wait to load
                dimCache.update("local_users", null, 128 * 1000, now);
            } else {
                if (holder.isAlive(now)) {
                    // cache not exists
                    return new ArrayList<>();
                }
                // cache expired, wait to reload
                holder.renewal(128 * 1000, now);
            }
            // 2. check sqlite
            users = userTable.getLocalUsers();
            if (users == null) {
                // placeholder
                users = new ArrayList<>();
            }
            // update memory cache
            dimCache.update("local_users", users, 36000 * 1000, now);
        }
        // OK, return cached value
        return users;
    }

    @Override
    public boolean saveLocalUsers(List<ID> users) {
        // 1. update memory cache
        dimCache.update("local_users", users, 36000 * 1000, 0);
        // 2. update sqlite
        return userTable.saveLocalUsers(users);
    }

    @Override
    public List<ID> getContacts(ID user) {
        long now = System.currentTimeMillis();
        List<ID> contacts = null;
        CacheHolder<List<ID>> holder = null;
        // 1. check memory cache
        CachePair<List<ID>> pair = contactCache.fetch(user, now);
        if (pair != null) {
            contacts = pair.value;
            holder = pair.holder;
        }
        if (contacts == null) {
            // cache empty
            if (holder == null) {
                // cache not load yet, wait to load
                contactCache.update(user, null, 128 * 1000, now);
            } else {
                if (holder.isAlive(now)) {
                    // cache not exists
                    return new ArrayList<>();
                }
                // cache expired, wait to reload
                holder.renewal(128 * 1000, now);
            }
            // 2. check sqlite
            contacts = contactTable.getContacts(user);
            if (contacts == null) {
                // placeholder
                contacts = new ArrayList<>();
            }
            // update memory cache
            contactCache.update(user, contacts, 36000 * 1000, now);
        }
        // OK, return cached value
        return contacts;
    }

    @Override
    public boolean saveContacts(List<ID> contacts, ID user) {
        // 1. update memory cache
        contactCache.update(user, contacts, 36000 * 1000, 0);
        // 2. update sqlite
        return contactTable.saveContacts(contacts, user);
    }
}
