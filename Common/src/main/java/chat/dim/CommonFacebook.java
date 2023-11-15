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
import chat.dim.mkm.DocumentHelper;
import chat.dim.mkm.User;
import chat.dim.protocol.Document;
import chat.dim.protocol.ID;
import chat.dim.protocol.Meta;
import chat.dim.utils.Log;

/**
 *  Common Facebook with Database
 */
public class CommonFacebook extends Facebook {

    private final AccountDBI database;
    private User current;

    public CommonFacebook(AccountDBI db) {
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
        if (array == null || array.isEmpty()) {
            user = current;
            if (user != null) {
                localUsers.add(user);
            }
        } else {
            for (ID item : array) {
                assert getPrivateKeyForSignature(item) != null : "private key not found: " + item;
                user = getUser(item);
                if (user != null) {
                    localUsers.add(user);
                } else {
                    assert false : "failed to create user: " + item;
                }
            }
        }
        return localUsers;
    }

    public User getCurrentUser() {
        // Get current user (for signing and sending message)
        User user = current;
        if (user == null) {
            //current = super.getCurrentUser();
            List<User> localUsers = getLocalUsers();
            if (/*localUsers != null && */!localUsers.isEmpty()) {
                user = localUsers.get(0);
                current = user;
            }
        }
        return user;
    }

    public void setCurrentUser(User user) {
        if (user.getDataSource() == null) {
            user.setDataSource(this);
        }
        current = user;
    }

    public Document getDocument(ID identifier, String type) {
        List<Document> documents = getDocuments(identifier);
        Document doc = DocumentHelper.lastDocument(documents, type);
        // compatible for document type
        if (doc == null && Document.VISA.equals(type)) {
            doc = DocumentHelper.lastDocument(documents, "profile");
        }
        return doc;
    }

    public String getName(ID identifier) {
        String type;
        if (identifier.isUser()) {
            type = Document.VISA;
        } else if (identifier.isGroup()) {
            type = Document.BULLETIN;
        } else {
            type = "*";
        }
        // get name from document
        Document doc = getDocument(identifier, type);
        if (doc != null) {
            String name = doc.getName();
            if (name != null && name.length() > 0) {
                return name;
            }
        }
        // get name from ID
        return Anonymous.getName(identifier);
    }

    @Override
    public boolean saveMeta(Meta meta, ID identifier) {
        if (!meta.isValid() || !meta.matchIdentifier(identifier)) {
            assert false : "meta not valid: " + identifier;
            return false;
        }
        // check old meta
        Meta old = getMeta(identifier);
        if (old != null) {
            assert meta.equals(old) : "meta would not changed";
            return true;
        }
        // meta not exists yet, save it
        return database.saveMeta(meta, identifier);
    }

    @Override
    public boolean saveDocument(Document doc) {
        ID identifier = doc.getIdentifier();
        if (identifier == null) {
            assert false : "document error: " + doc;
            return false;
        }
        if (!doc.isValid()) {
            // try to verify
            Meta meta = getMeta(identifier);
            if (meta == null) {
                Log.error("meta not found: " + identifier);
                return false;
            } else if (doc.verify(meta.getPublicKey())) {
                Log.debug("document verified: " + identifier);
            } else {
                Log.error("failed to verify document: " + identifier);
                assert false : "document not valid: " + identifier;
                return false;
            }
        }
        String type = doc.getType();
        // check old documents with type
        List<Document> documents = getDocuments(identifier);
        Document old = DocumentHelper.lastDocument(documents, type);
        if (old != null && DocumentHelper.isExpired(doc, old)) {
            Log.warning("drop expired document: " + identifier);
            return false;
        }
        return database.saveDocument(doc);
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
    public List<Document> getDocuments(ID entity) {
        /*/
        if (entity.isBroadcast()) {
            // broadcast ID has no documents
            return null;
        }
        /*/
        return database.getDocuments(entity);
    }

    //
    //  UserDataSource
    //

    @Override
    public List<ID> getContacts(ID user) {
        return database.getContacts(user);
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
        ID owner = getOwner(group);
        if (owner == null) {
            assert false : "group owner not found: " + group;
            return null;
        }
        List<ID> users = database.getMembers(group);
        if (users == null || users.isEmpty()) {
            users = super.getMembers(group);
            if (users == null || users.isEmpty()) {
                users = new ArrayList<>();
                users.add(owner);
            }
        }
        assert users.get(0).equals(owner) : "group owner must be the first member: " + group;
        return users;
    }

    @Override
    public List<ID> getAssistants(ID group) {
        List<ID> bots = database.getAssistants(group);
        if (bots != null && !bots.isEmpty()) {
            // got from database
            return bots;
        }
        return super.getAssistants(group);
    }
}
