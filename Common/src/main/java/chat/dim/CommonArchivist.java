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

import chat.dim.crypto.EncryptKey;
import chat.dim.crypto.VerifyKey;
import chat.dim.dbi.AccountDBI;
import chat.dim.mkm.BaseGroup;
import chat.dim.mkm.BaseUser;
import chat.dim.mkm.Bot;
import chat.dim.mkm.DocumentHelper;
import chat.dim.mkm.Group;
import chat.dim.mkm.ServiceProvider;
import chat.dim.mkm.Station;
import chat.dim.mkm.User;
import chat.dim.protocol.Document;
import chat.dim.protocol.EntityType;
import chat.dim.protocol.ID;
import chat.dim.protocol.Meta;
import chat.dim.protocol.Visa;

public class CommonArchivist implements Archivist {

    private final WeakReference<Facebook> barrack;

    protected final AccountDBI database;

    public CommonArchivist(Facebook facebook, AccountDBI db) {
        super();
        barrack = new WeakReference<>(facebook);
        database = db;
    }

    protected Facebook getFacebook() {
        return barrack.get();
    }

    @Override
    public User createUser(ID identifier) {
        assert identifier.isUser() : "user ID error: " + identifier;
        // check visa key
        if (!identifier.isBroadcast()) {
            Facebook facebook = getFacebook();
            if (facebook.getPublicKeyForEncryption(identifier) == null) {
                assert false : "visa.key not found: " + identifier;
                return null;
            }
            // NOTICE: if visa.key exists, then visa & meta must exist too.
        }
        int type = identifier.getType();
        // check user type
        if (EntityType.STATION.equals(type)) {
            return new Station(identifier);
        } else if (EntityType.BOT.equals(type)) {
            return new Bot(identifier);
        }
        // general user, or 'anyone@anywhere'
        return new BaseUser(identifier);
    }

    @Override
    public Group createGroup(ID identifier) {
        assert identifier.isGroup() : "group ID error: " + identifier;
        // check members
        if (!identifier.isBroadcast()) {
            Facebook facebook = getFacebook();
            List<ID> members = facebook.getMembers(identifier);
            if (members == null || members.isEmpty()) {
                assert false : "group members not found: " + identifier;
                return null;
            }
            // NOTICE: if members exist, then owner (founder) must exist,
            //         and bulletin & meta must exist too.
        }
        int type = identifier.getType();
        // check group type
        if (EntityType.ISP.equals(type)) {
            return new ServiceProvider(identifier);
        }
        // general group, or 'everyone@everywhere'
        return new BaseGroup(identifier);
    }

    @Override
    public VerifyKey getMetaKey(ID user) {
        Facebook facebook = getFacebook();
        Meta meta = facebook.getMeta(user);
        if (meta != null/* && meta.isValid()*/) {
            return meta.getPublicKey();
        }
        //throw new NullPointerException("failed to get meta for ID: " + user);
        return null;
    }

    @Override
    public EncryptKey getVisaKey(ID user) {
        Facebook facebook = getFacebook();
        List<Document> documents = facebook.getDocuments(user);
        Visa doc = DocumentHelper.lastVisa(documents);
        if (doc != null/* && doc.isValid()*/) {
            return doc.getPublicKey();
        }
        return null;
    }

    @Override
    public List<User> getLocalUsers() {
        List<ID> array = database.getLocalUsers();
        if (array == null) {
            return null;
        }
        List<User> localUsers = new ArrayList<>();
        User user;
        Facebook facebook = getFacebook();
        for (ID item : array) {
            assert facebook.getPrivateKeyForSignature(item) != null : "private key not found: " + item;
            user = facebook.getUser(item);
            if (user != null) {
                localUsers.add(user);
            } else {
                assert false : "failed to create user: " + item;
            }
        }
        return localUsers;
    }

}
