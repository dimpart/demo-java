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

import java.util.List;

import chat.dim.core.Archivist;
import chat.dim.dbi.AccountDBI;
import chat.dim.mkm.DocumentUtils;
import chat.dim.mkm.User;
import chat.dim.protocol.Bulletin;
import chat.dim.protocol.DecryptKey;
import chat.dim.protocol.Document;
import chat.dim.protocol.DocumentType;
import chat.dim.protocol.ID;
import chat.dim.protocol.Meta;
import chat.dim.protocol.SignKey;
import chat.dim.protocol.Visa;

/**
 *  Common Facebook with Database
 */
public abstract class CommonFacebook extends Facebook {

    protected final AccountDBI database;

    private CommonArchivist barrack;
    private EntityChecker checker;

    private User currentUser;

    public CommonFacebook(AccountDBI db) {
        super();
        database = db;
        barrack = null;
        checker = null;
        currentUser = null;
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
    public Archivist getArchivist() {
        return barrack;
    }

    @Override
    protected CommonArchivist getBarrack() {
        return barrack;
    }
    public void setBarrack(CommonArchivist archivist) {
        barrack = archivist;
    }

    //
    //  Current User
    //

    public User getCurrentUser() {
        // Get current user (for signing and sending message)
        User current = currentUser;
        if (current != null) {
            return current;
        }
        List<ID> array = database.getLocalUsers();
        if (array == null || array.isEmpty()) {
            return null;
        }
        current = getUser(array.get(0));
        currentUser = current;
        return current;
    }

    public void setCurrentUser(User user) {
        if (user.getDataSource() == null) {
            user.setDataSource(this);
        }
        currentUser = user;
    }

    @Override
    public ID selectLocalUser(ID receiver) {
        User user = currentUser;
        if (user != null) {
            ID current = user.getIdentifier();
            if (receiver.isBroadcast()) {
                // broadcast message can be decrypted by anyone, so
                // just return current user here
                return current;
            } else if (receiver.isGroup()) {
                // group message (recipient not designated)
                //
                // the messenger will check group info before decrypting message,
                // so we can trust that the group's meta & members MUST exist here.
                List<ID> members = getMembers(receiver);
                if (members == null || members.isEmpty()) {
                    assert false : "members not found: " + receiver;
                    return null;
                } else if (members.contains(current)) {
                    return current;
                }
            } else if (receiver.equals(current)) {
                return current;
            }
        }
        // check local users
        return super.selectLocalUser(receiver);
    }

    //
    //  Documents
    //

    public Document getDocument(ID identifier, String type) {
        List<Document> documents = getDocuments(identifier);
        Document doc = DocumentUtils.lastDocument(documents, type);
        // compatible for document type
        if (doc == null && DocumentType.VISA.equals(type)) {
            doc = DocumentUtils.lastDocument(documents, DocumentType.PROFILE);
        }
        return doc;
    }

    public Visa getVisa(ID user) {
        // assert user.isUser() : "user ID error: " + user;
        List<Document> documents = getDocuments(user);
        return DocumentUtils.lastVisa(documents);
    }


    public Bulletin getBulletin(ID group) {
        // assert group.isGroup() : "group ID error: " + group;
        List<Document> documents = getDocuments(group);
        return DocumentUtils.lastBulletin(documents);
    }

    public String getName(ID identifier) {
        String type;
        if (identifier.isUser()) {
            type = DocumentType.VISA;
        } else if (identifier.isGroup()) {
            type = DocumentType.BULLETIN;
        } else {
            type = "*";
        }
        // get name from document
        Document doc = getDocument(identifier, type);
        if (doc != null) {
            String name = (String) doc.getProperty("name");
            if (name != null && name.length() > 0) {
                return name;
            }
        }
        // get name from ID
        return Anonymous.getName(identifier);
    }

    // -------- Storage

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
