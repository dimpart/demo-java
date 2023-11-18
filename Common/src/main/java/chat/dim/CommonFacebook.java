/* license: https://mit-license.org
 *
 *  DIM-SDK : Decentralized Instant Messaging Software Development Kit
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
import chat.dim.mkm.BroadcastHelper;
import chat.dim.mkm.DocumentHelper;
import chat.dim.mkm.User;
import chat.dim.protocol.Bulletin;
import chat.dim.protocol.Document;
import chat.dim.protocol.EntityType;
import chat.dim.protocol.ID;
import chat.dim.protocol.Meta;
import chat.dim.utils.Log;

/**
 *  Common Facebook with Database
 */
public abstract class CommonFacebook extends Facebook {

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

    protected abstract Archivist getArchivist();

    @Override
    public List<User> getLocalUsers() {
        List<User> localUsers = new ArrayList<>();
        User user;
        AccountDBI db = getDatabase();
        List<ID> array = db.getLocalUsers();
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
        AccountDBI db = getDatabase();
        return db.saveMeta(meta, identifier);
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
        AccountDBI db = getDatabase();
        return db.saveDocument(doc);
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
        AccountDBI db = getDatabase();
        Meta meta = db.getMeta(entity);
        Archivist archivist = getArchivist();
        if (archivist.checkMeta(entity, meta)) {
            Log.info("querying meta for: " + entity);
        }
        return meta;
    }

    @Override
    public List<Document> getDocuments(ID entity) {
        /*/
        if (entity.isBroadcast()) {
            // broadcast ID has no documents
            return null;
        }
        /*/
        AccountDBI db = getDatabase();
        List<Document> docs = db.getDocuments(entity);
        Archivist archivist = getArchivist();
        if (archivist.checkDocuments(entity, docs)) {
            Log.info("querying documents for: " + entity);
        }
        return docs;
    }

    //
    //  UserDataSource
    //

    @Override
    public List<ID> getContacts(ID user) {
        AccountDBI db = getDatabase();
        return db.getContacts(user);
    }

    @Override
    public List<DecryptKey> getPrivateKeysForDecryption(ID user) {
        AccountDBI db = getDatabase();
        return db.getPrivateKeysForDecryption(user);
    }

    @Override
    public SignKey getPrivateKeyForSignature(ID user) {
        AccountDBI db = getDatabase();
        return db.getPrivateKeyForSignature(user);
    }

    @Override
    public SignKey getPrivateKeyForVisaSignature(ID user) {
        AccountDBI db = getDatabase();
        return db.getPrivateKeyForVisaSignature(user);
    }

    //
    //  GroupDataSource
    //

    @Override
    public ID getFounder(ID group) {
        assert group.isGroup() : "group ID error: " + group;
        // check broadcast group
        if (group.isBroadcast()) {
            // founder of broadcast group
            return BroadcastHelper.getBroadcastFounder(group);
        }
        // check bulletin document
        Bulletin doc = getBulletin(group);
        if (doc == null) {
            // the owner(founder) should be set in the bulletin document of group
            return null;
        }
        // check local storage
        AccountDBI db = getDatabase();
        ID user = db.getFounder(group);
        if (user != null) {
            // got from local storage
            return user;
        }
        // get from bulletin document
        user = doc.getFounder();
        assert user != null : "founder not designated for group: " + group;
        return user;
    }

    @Override
    public ID getOwner(ID group) {
        assert group.isGroup() : "group ID error: " + group;
        // check broadcast group
        if (group.isBroadcast()) {
            // owner of broadcast group
            return BroadcastHelper.getBroadcastOwner(group);
        }
        // check bulletin document
        Bulletin doc = getBulletin(group);
        if (doc == null) {
            // the owner(founder) should be set in the bulletin document of group
            return null;
        }
        // check local storage
        AccountDBI db = getDatabase();
        ID user = db.getOwner(group);
        if (user != null) {
            // got from local storage
            return user;
        }
        // check group type
        if (EntityType.GROUP.equals(group.getType())) {
            // Polylogue owner is its founder
            user = db.getFounder(group);
            if (user == null) {
                user = doc.getFounder();
            }
        }
        assert user != null : "owner not found for group: " + group;
        return user;
    }

    @Override
    public List<ID> getMembers(ID group) {
        assert group.isGroup() : "group ID error: " + group;
        // check group owner
        ID owner = getOwner(group);
        if (owner == null) {
            // assert false : "group owner not found: " + group;
            return null;
        }
        // check local storage
        AccountDBI db = getDatabase();
        List<ID> members = db.getMembers(group);
        Archivist archivist = getArchivist();
        if (archivist.checkMembers(group, members)) {
            Log.info("querying members for group: " + group);
        }
        if (members == null || members.isEmpty()) {
            members = new ArrayList<>();
            members.add(owner);
        } else {
            assert members.get(0).equals(owner) : "group owner must be the first member: " + group;
        }
        return members;
    }

    @Override
    public List<ID> getAssistants(ID group) {
        assert group.isGroup() : "group ID error: " + group;
        // check bulletin document
        Bulletin doc = getBulletin(group);
        if (doc == null) {
            // the assistants should be set in the bulletin document of group
            return null;
        }
        // check local storage
        AccountDBI db = getDatabase();
        List<ID> bots = db.getAssistants(group);
        if (bots != null && !bots.isEmpty()) {
            // got from local storage
            return bots;
        }
        // get from bulletin document
        return doc.getAssistants();
    }

    public List<ID> getAdministrators(ID group) {
        assert group.isGroup() : "group ID error: " + group;
        // check bulletin document
        Bulletin doc = getBulletin(group);
        if (doc == null) {
            // the administrators should be set in the bulletin document
            return null;
        }
        // the 'administrators' should be saved into local storage
        // when the newest bulletin document received,
        // so we must get them from the local storage only,
        // not from the bulletin document.
        AccountDBI db = getDatabase();
        return db.getAdministrators(group);
    }

}
