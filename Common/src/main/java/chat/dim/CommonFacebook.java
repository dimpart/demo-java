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
import chat.dim.mkm.MetaHelper;
import chat.dim.mkm.User;
import chat.dim.protocol.Bulletin;
import chat.dim.protocol.Document;
import chat.dim.protocol.ID;
import chat.dim.protocol.Meta;
import chat.dim.protocol.Visa;
import chat.dim.utils.Log;

/**
 *  Common Facebook with Database
 */
public abstract class CommonFacebook extends Facebook {

    protected final AccountDBI database;

    private CommonArchivist archivist;
    private EntityChecker checker;

    private User current;

    public CommonFacebook(AccountDBI db) {
        super();
        database = db;
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
        //
        //  1. check valid
        //
        if (!checkMeta(meta, identifier)) {
            assert false : "meta not valid: " + identifier;
            Log.warning("meta not valid: " + identifier);
            return false;
        }
        //
        //  2. check duplicated
        //
        Meta old = getMeta(identifier);
        if (old != null) {
            Log.debug("meta duplicated: " + identifier);
            return true;
        }
        //
        //  3. save into database
        //
        return database.saveMeta(meta, identifier);
    }

    protected boolean checkMeta(Meta meta, ID identifier) {
        return meta.isValid() && MetaHelper.matches(identifier, meta);
    }

    @Override
    public boolean saveDocument(Document doc) {
        //
        //  1. check valid
        //
        boolean valid = checkDocumentValid(doc);
        if (!valid) {
            assert false : "document not valid: " + doc.getIdentifier();
            Log.warning("document not valid: " + doc.getIdentifier());
            return false;
        }
        //
        //  2. check expired
        //
        if (checkDocumentExpired(doc)) {
            Log.info("drop expired document: " + doc.getIdentifier());
            return false;
        }
        //
        //  3. save into database
        //
        return database.saveDocument(doc);
    }

    protected boolean checkDocumentValid(Document doc) {
        ID identifier = doc.getIdentifier();
        Date docTime = doc.getTime();
        // check document time
        if (docTime == null) {
            assert false : "document error: " + doc;
            Log.warning("document without time: " + identifier);
        } else {
            // calibrate the clock
            // make sure the document time is not in the far future
            long nearFuture = System.currentTimeMillis() + 65536;
            if (docTime.getTime() > nearFuture) {
                assert false : "document time error: " + docTime + ", " + doc;
                Log.error("document time error: " + docTime + ", " + identifier);
                return false;
            }
        }
        // check valid
        return doc.isValid() || verifyDocument(doc);
    }

    protected boolean verifyDocument(Document doc) {
        ID identifier = doc.getIdentifier();
        Meta meta = getMeta(identifier);
        if (meta == null) {
            Log.warning("failed to get meta: " + identifier);
            return false;
        }
        return doc.verify(meta.getPublicKey());
    }

    protected boolean checkDocumentExpired(Document doc) {
        ID identifier = doc.getIdentifier();
        String type = doc.getType();
        // check old documents with type
        List<Document> documents = getDocuments(identifier);
        Document old = DocumentHelper.lastDocument(documents, type);
        return old != null && DocumentHelper.isExpired(doc, old);
    }

    //
    //  Entity DataSource
    //

    @Override
    public Meta getMeta(ID entity) {
        Meta meta = database.getMeta(entity);
        EntityChecker checker = getEntityChecker();
        checker.checkMeta(entity, meta);
        return meta;
    }

    @Override
    public List<Document> getDocuments(ID entity) {
        List<Document> docs = database.getDocuments(entity);
        EntityChecker checker = getEntityChecker();
        checker.checkDocuments(entity, docs);
        return docs;
    }

    //
    //  User DataSource
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
    //  Organizational Structure
    //

    public abstract List<ID> getAdministrators(ID group);
    public abstract boolean saveAdministrators(List<ID> members, ID group);

    public abstract boolean saveMembers(List<ID> newMembers, ID group);

}
