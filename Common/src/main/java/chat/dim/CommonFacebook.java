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
package chat.dim;

import java.util.ArrayList;
import java.util.List;

import chat.dim.crypto.DecryptKey;
import chat.dim.crypto.SignKey;
import chat.dim.dbi.AccountDBI;
import chat.dim.mkm.Group;
import chat.dim.mkm.User;
import chat.dim.protocol.Document;
import chat.dim.protocol.ID;
import chat.dim.protocol.Meta;

public class CommonFacebook extends Facebook {

    private final AccountDBI database;
    private User current;

    protected CommonFacebook(AccountDBI db) {
        super();
        database = db;
        current = null;
    }

    public AccountDBI getDatabase() {
        return database;
    }

    @Override
    public List<User> getLocalUsers() {
        List<User> localUsers = new ArrayList<>();
        User user;
        List<ID> array = database.getLocalUsers();
        if (array == null || array.size() == 0) {
            user = current;
            if (user != null) {
                localUsers.add(user);
            }
        } else {
            for (ID item : array) {
                assert getPrivateKeyForSignature(item) != null : "private key not found: " + item;
                user = getUser(item);
                assert user != null : "failed to create user: " + item;
                localUsers.add(user);
            }
        }
        return localUsers;
    }

    @Override
    public User getCurrentUser() {
        // Get current user (for signing and sending message)
        User user = current;
        if (user == null) {
            //current = super.getCurrentUser();
            List<User> localUsers = getLocalUsers();
            if (localUsers.size() > 0) {
                user = localUsers.get(0);
                current = user;
            }
        }
        return user;
    }

    public void setCurrentUser(User user) {
        current = user;
    }

    @Override
    public boolean saveMeta(Meta meta, ID identifier) {
        return database.saveMeta(meta, identifier);
    }

    @Override
    public boolean saveDocument(Document doc) {
        return database.saveDocument(doc);
    }

    @Override
    public boolean saveMembers(List<ID> members, ID group) {
        return database.saveMembers(members, group);
    }

    public boolean saveAssistants(List<ID> bots, ID group) {
        return database.saveAssistants(bots, group);
    }

    @Override
    protected User createUser(ID user) {
        if (!user.isBroadcast()) {
            if (getPublicKeyForEncryption(user) == null) {
                // visa.key not found
                return null;
            }
        }
        return super.createUser(user);
    }

    @Override
    protected Group createGroup(ID group) {
        if (!group.isBroadcast()) {
            if (getMeta(group) == null) {
                // group meta not found
                return null;
            }
        }
        return super.createGroup(group);
    }

    //
    //  EntityDataSource
    //

    @Override
    public Meta getMeta(ID entity) {
        /*/
        if (entity.isBroadcast()) {
            // broadcast ID has no meta
            return null;
        }
        /*/
        return database.getMeta(entity);
    }

    @Override
    public Document getDocument(ID entity, String type) {
        /*/
        if (entity.isBroadcast()) {
            // broadcast ID has no document
            return null;
        }
        /*/
        return database.getDocument(entity, type);
    }

    //
    //  UserDataSource
    //

    @Override
    public List<ID> getContacts(ID user) {
        return database.getContacts();
    }

    @Override
    public List<DecryptKey> getPrivateKeysForDecryption(ID user) {
        return database.getPrivateKeysForDecryption(user);
    }

    @Override
    public SignKey getPrivateKeyForSignature(ID user) {
        return database.getPrivateKeyForSignature(user);
    }

    @Override
    public SignKey getPrivateKeyForVisaSignature(ID user) {
        return database.getPrivateKeyForVisaSignature(user);
    }

    //
    //  GroupDataSource
    //

    @Override
    public ID getFounder(ID group) {
        ID user = database.getFounder(group);
        if (user != null) {
            // got from database
            return user;
        }
        return super.getFounder(group);
    }

    @Override
    public ID getOwner(ID group) {
        ID user = database.getOwner(group);
        if (user != null) {
            // got from database
            return user;
        }
        return super.getOwner(group);
    }

    @Override
    public List<ID> getMembers(ID group) {
        List<ID> users = database.getMembers(group);
        if (users != null && users.size() > 0) {
            // got from database
            return users;
        }
        return super.getMembers(group);
    }

    @Override
    public List<ID> getAssistants(ID group) {
        List<ID> bots = database.getAssistants(group);
        if (bots != null && bots.size() > 0) {
            // got from database
            return bots;
        }
        return super.getAssistants(group);
    }
}
