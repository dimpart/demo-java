/* license: https://mit-license.org
 *
 *  DIM-SDK : Decentralized Instant Messaging Software Development Kit
 *
 *                                Written in 2023 by Moky <albert.moky@gmail.com>
 *
 * ==============================================================================
 * The MIT License (MIT)
 *
 * Copyright (c) 2023 Albert Moky
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

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import chat.dim.core.Barrack;
import chat.dim.dbi.AccountDBI;
import chat.dim.mkm.Group;
import chat.dim.mkm.User;
import chat.dim.protocol.ID;
import chat.dim.utils.MemoryCache;
import chat.dim.utils.ThanosCache;

public class CommonArchivist extends Barrack {

    public CommonArchivist(Facebook facebook, AccountDBI db) {
        super();
        barrack = new WeakReference<>(facebook);
        database = db;
    }

    protected Facebook getFacebook() {
        return barrack.get();
    }

    private final WeakReference<Facebook> barrack;

    protected final AccountDBI database;

    // memory caches
    protected final MemoryCache<ID, User>   userCache = createUserCache();
    protected final MemoryCache<ID, Group> groupCache = createGroupCache();

    protected MemoryCache<ID, User> createUserCache() {
        return new ThanosCache<>();
    }
    protected MemoryCache<ID, Group> createGroupCache() {
        return new ThanosCache<>();
    }

    /**
     * Call it when received 'UIApplicationDidReceiveMemoryWarningNotification',
     * this will remove 50% of cached objects
     *
     * @return number of survivors
     */
    public int reduceMemory() {
        int cnt1 = userCache.reduceMemory();
        int cnt2 = groupCache.reduceMemory();
        return cnt1 + cnt2;
    }

    //
    //  Barrack
    //

    @Override
    public void cacheUser(User user) {
        if (user.getDataSource() == null) {
            user.setDataSource(getFacebook());
        }
        userCache.put(user.getIdentifier(), user);
    }

    @Override
    public void cacheGroup(Group group) {
        if (group.getDataSource() == null) {
            group.setDataSource(getFacebook());
        }
        groupCache.put(group.getIdentifier(), group);
    }

    @Override
    public User getUser(ID identifier) {
        return userCache.get(identifier);
    }

    @Override
    public Group getGroup(ID identifier) {
        return groupCache.get(identifier);
    }

    @Override
    public List<User> getLocalUsers() {
        Facebook facebook = getFacebook();
        List<ID> array = database.getLocalUsers();
        if (facebook == null || array == null) {
            return null;
        }
        List<User> allUsers = new ArrayList<>();
        User user;
        for (ID item : array) {
            assert facebook.getPrivateKeyForSignature(item) != null : "private key not found: " + item;
            user = facebook.getUser(item);
            if (user != null) {
                allUsers.add(user);
            } else {
                assert false : "failed to create user: " + item;
            }
        }
        return allUsers;
    }

}
