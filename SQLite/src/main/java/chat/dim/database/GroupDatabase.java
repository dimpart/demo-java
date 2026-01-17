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

import chat.dim.dbi.GroupDBI;
import chat.dim.mem.CacheHolder;
import chat.dim.mem.CacheManager;
import chat.dim.mem.CachePair;
import chat.dim.mem.CachePool;
import chat.dim.protocol.ID;
import chat.dim.sqlite.DatabaseConnector;
import chat.dim.sqlite.account.GroupTable;

public class GroupDatabase implements GroupDBI {

    private final GroupTable groupTable;

    private final CachePool<ID, ID> founderCache;
    private final CachePool<ID, ID> ownerCache;
    private final CachePool<ID, List<ID>> membersCache;

    public GroupDatabase(DatabaseConnector sqliteConnector) {
        super();
        groupTable = new GroupTable(sqliteConnector);
        CacheManager man = CacheManager.getInstance();
        founderCache    = man.getPool("founder");
        ownerCache      = man.getPool("owner");
        membersCache    = man.getPool("members");
    }

    //
    //  Group DBI
    //

    @Override
    public ID getFounder(ID group) {
        long now = System.currentTimeMillis();
        ID value = null;
        CacheHolder<ID> holder = null;
        // 1. check memory cache
        CachePair<ID> pair = founderCache.fetch(group, now);
        if (pair != null) {
            value = pair.value;
            holder = pair.holder;
        }
        if (value == null) {
            // cache empty
            if (holder == null) {
                // cache not load yet, wait to load
                founderCache.update(group, null, 128 * 1000, now);
            } else {
                if (holder.isAlive(now)) {
                    // cache not exists
                    return null;
                }
                // cache expired, wait to load
                holder.renewal(128 * 1000, now);
            }
            // 2. check sqlite
            value = groupTable.getFounder(group);
            if (value == null) {
                // placeholder
                value = ID.FOUNDER;
            }
            // update memory cache
            founderCache.update(group, value, 36000 * 1000, now);
        } else if (value.isBroadcast()) {
            // placeholder
            value = null;
        }
        // OK, return cached value
        return value;
    }

    @Override
    public ID getOwner(ID group) {
        long now = System.currentTimeMillis();
        ID value = null;
        CacheHolder<ID> holder = null;
        // 1. check memory cache
        CachePair<ID> pair = ownerCache.fetch(group, now);
        if (pair != null) {
            value = pair.value;
            holder = pair.holder;
        }
        if (value == null) {
            // cache empty
            if (holder == null) {
                // cache not load yet, wait to load
                ownerCache.update(group, null, 128 * 1000, now);
            } else {
                if (holder.isAlive(now)) {
                    // cache not exists
                    return null;
                }
                // cache expired, wait to load
                holder.renewal(128 * 1000, now);
            }
            // 2. check sqlite
            value = groupTable.getOwner(group);
            if (value == null) {
                // placeholder
                value = ID.ANYONE;
            }
            // update memory cache
            ownerCache.update(group, value, 3600 * 1000, now);
        } else if (value.isBroadcast()) {
            // placeholder
            value = null;
        }
        // OK, return cached value
        return value;
    }

    @Override
    public List<ID> getMembers(ID group) {
        long now = System.currentTimeMillis();
        List<ID> value = null;
        CacheHolder<List<ID>> holder = null;
        // 1. check memory cache
        CachePair<List<ID>> pair = membersCache.fetch(group, now);
        if (pair != null) {
            value = pair.value;
            holder = pair.holder;
        }
        if (value == null) {
            // cache empty
            if (holder == null) {
                // cache not load yet, wait to load
                membersCache.update(group, null, 128 * 1000, now);
            } else {
                if (holder.isAlive(now)) {
                    // cache not exists
                    return new ArrayList<>();
                }
                // cache expired, wait to load
                holder.renewal(128 * 1000, now);
            }
            // 2. check sqlite
            value = groupTable.getMembers(group);
            if (value == null) {
                // placeholder
                value = new ArrayList<>();
            }
            // update memory cache
            membersCache.update(group, value, 3600 * 1000, now);
        }
        // OK, return cached value
        return value;
    }

    @Override
    public boolean saveMembers(List<ID> members, ID group) {
        long now = System.currentTimeMillis();
        // 1. update memory cache
        membersCache.update(group, members, 3600 * 1000, now);
        // 2. update sqlite
        return groupTable.saveMembers(members, group);
    }

    @Override
    public List<ID> getAdministrators(ID group) {
        // TODO: load admins
        return null;
    }

    @Override
    public boolean saveAdministrators(List<ID> members, ID group) {
        // TODO: save admins
        return false;
    }
}
