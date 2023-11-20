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

import java.util.Date;
import java.util.List;

import chat.dim.crypto.DecryptKey;
import chat.dim.crypto.EncryptKey;
import chat.dim.crypto.SignKey;
import chat.dim.crypto.VerifyKey;
import chat.dim.dbi.AccountDBI;
import chat.dim.mkm.Group;
import chat.dim.mkm.User;
import chat.dim.protocol.Document;
import chat.dim.protocol.GroupCommand;
import chat.dim.protocol.ID;
import chat.dim.protocol.Meta;
import chat.dim.protocol.ReliableMessage;
import chat.dim.type.Pair;

public abstract class CommonArchivist extends Archivist implements User.DataSource, Group.DataSource {

    private final AccountDBI database;

    public CommonArchivist(AccountDBI db) {
        super(QUERY_EXPIRES);
        database = db;
    }

    public AccountDBI getDatabase() {
        return database;
    }

    @Override
    public Date getLastGroupHistoryTime(ID group) {
        AccountDBI adb = getDatabase();
        List<Pair<GroupCommand, ReliableMessage>> array = adb.getGroupHistories(group);
        if (array == null || array.isEmpty()) {
            return null;
        }
        Date lastTime = null;
        GroupCommand his;
        Date hisTime;
        for (Pair<GroupCommand, ReliableMessage> pair : array) {
            his = pair.first;
            if (his == null) {
                assert false : "group command error: " + pair;
                continue;
            }
            hisTime = his.getTime();
            if (hisTime == null) {
                assert false : "group command error: " + his;
            } else if (lastTime == null || lastTime.before(hisTime)) {
                lastTime = hisTime;
            }
        }
        return lastTime;
    }

    public List<ID> getLocalUsers() {
        AccountDBI db = getDatabase();
        return db.getLocalUsers();
    }

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
            long current = System.currentTimeMillis() + 15000;
            if (docTime.getTime() > current) {
                assert false : "document time error: " + docTime + ", " + doc;
                return false;
            }
        }
        AccountDBI db = getDatabase();
        return db.saveDocument(doc);
    }

    //
    //  EntityDataSource
    //

    @Override
    public Meta getMeta(ID entity) {
        AccountDBI db = getDatabase();
        return db.getMeta(entity);
    }

    @Override
    public List<Document> getDocuments(ID entity) {
        AccountDBI db = getDatabase();
        return db.getDocuments(entity);
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
    public EncryptKey getPublicKeyForEncryption(ID user) {
        assert false : "DON'T call me!";
        return null;
    }

    @Override
    public List<VerifyKey> getPublicKeysForVerification(ID user) {
        assert false : "DON'T call me!";
        return null;
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
        AccountDBI db = getDatabase();
        return db.getFounder(group);
    }

    @Override
    public ID getOwner(ID group) {
        AccountDBI db = getDatabase();
        return db.getOwner(group);
    }

    @Override
    public List<ID> getMembers(ID group) {
        AccountDBI db = getDatabase();
        return db.getMembers(group);
    }

    @Override
    public List<ID> getAssistants(ID group) {
        AccountDBI db = getDatabase();
        return db.getAssistants(group);
    }

    public List<ID> getAdministrators(ID group) {
        AccountDBI db = getDatabase();
        return db.getAdministrators(group);
    }
    public boolean saveAdministrators(List<ID> members, ID group) {
        AccountDBI db = getDatabase();
        return db.saveAdministrators(members, group);
    }

    public boolean saveMembers(List<ID> newMembers, ID group) {
        AccountDBI db = getDatabase();
        return db.saveMembers(newMembers, group);
    }

}
