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

import java.util.Date;
import java.util.List;

import chat.dim.crypto.DecryptKey;
import chat.dim.crypto.SignKey;
import chat.dim.dbi.AccountDBI;
import chat.dim.mkm.DocumentHelper;
import chat.dim.mkm.User;
import chat.dim.protocol.Bulletin;
import chat.dim.protocol.Document;
import chat.dim.protocol.ID;
import chat.dim.protocol.Meta;
import chat.dim.protocol.Visa;

/**
 *  Common Facebook with Database
 */
public abstract class CommonFacebook extends Facebook {

    private final AccountDBI database;
    private CommonArchivist archivist;
    private EntityChecker checker;

    private User current;

    public CommonFacebook(AccountDBI database) {
        super();
        this.database = database;
        archivist = null;
        checker = null;
        // current user
        current = null;
    }

    public AccountDBI getDatabase() {
        return database;
    }

    public EntityChecker getEntityChecker() {
        return checker;
    }
    public void setEntityChecker(EntityChecker checker) {
        this.checker = checker;
    }

    @Override
    public CommonArchivist getArchivist() {
        return archivist;
    }
    public void setArchivist(CommonArchivist archivist) {
        this.archivist = archivist;
    }

    //
    //  Current User
    //

    public User getCurrentUser() {
        // Get current user (for signing and sending message)
        User user = current;
        if (user != null) {
            return user;
        }
        CommonArchivist archivist = getArchivist();
        List<User> localUsers = archivist.getLocalUsers();
        if (localUsers == null || localUsers.isEmpty()) {
            return null;
        }
        user = localUsers.get(0);
        current = user;
        return user;
    }

    public void setCurrentUser(User user) {
        if (user.getDataSource() == null) {
            user.setDataSource(this);
        }
        current = user;
    }

    //
    //  Documents
    //

    public Document getDocument(ID identifier, String type) {
        List<Document> documents = getDocuments(identifier);
        Document doc = DocumentHelper.lastDocument(documents, type);
        // compatible for document type
        if (doc == null && Document.VISA.equals(type)) {
            doc = DocumentHelper.lastDocument(documents, Document.PROFILE);
        }
        return doc;
    }

    public Visa getVisa(ID user) {
        // assert user.isUser() : "user ID error: " + user;
        List<Document> documents = getDocuments(user);
        return DocumentHelper.lastVisa(documents);
    }


    public Bulletin getBulletin(ID group) {
        // assert group.isGroup() : "group ID error: " + group;
        List<Document> documents = getDocuments(group);
        return DocumentHelper.lastBulletin(documents);
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

    // -------- Storage

    @Override
    public boolean saveMeta(Meta meta, ID identifier) {
        AccountDBI db = getDatabase();
        return db.saveMeta(meta, identifier);
    }

    @Override
    public boolean saveDocument(Document doc) {
        Date docTime = doc.getTime();
        if (docTime == null) {
            assert false : "document error: " + doc;
        } else {
            // calibrate the clock
            // make sure the document time is not in the far future
            long current = System.currentTimeMillis() + 65536;
            if (docTime.getTime() > current) {
                assert false : "document time error: " + docTime + ", " + doc;
                return false;
            }
        }
        AccountDBI db = getDatabase();
        return db.saveDocument(doc);
    }

    //
    //  Entity DataSource
    //

    @Override
    public Meta getMeta(ID entity) {
        AccountDBI db = getDatabase();
        Meta meta = db.getMeta(entity);
        EntityChecker checker = getEntityChecker();
        checker.checkMeta(entity, meta);
        return meta;
    }

    @Override
    public List<Document> getDocuments(ID entity) {
        AccountDBI db = getDatabase();
        List<Document> docs = db.getDocuments(entity);
        EntityChecker checker = getEntityChecker();
        checker.checkDocuments(entity, docs);
        return docs;
    }

    //
    //  User DataSource
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
    //  Organizational Structure
    //

    public abstract List<ID> getAdministrators(ID group);
    public abstract boolean saveAdministrators(List<ID> members, ID group);

    public abstract boolean saveMembers(List<ID> newMembers, ID group);

}
