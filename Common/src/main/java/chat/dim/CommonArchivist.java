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
import java.util.Date;
import java.util.List;

import chat.dim.core.Archivist;
import chat.dim.core.Barrack;
import chat.dim.crypto.EncryptKey;
import chat.dim.crypto.VerifyKey;
import chat.dim.dbi.AccountDBI;
import chat.dim.log.Log;
import chat.dim.mem.MemoryCache;
import chat.dim.mem.ThanosCache;
import chat.dim.mkm.DocumentUtils;
import chat.dim.mkm.Group;
import chat.dim.mkm.MetaUtils;
import chat.dim.mkm.User;
import chat.dim.protocol.Document;
import chat.dim.protocol.ID;
import chat.dim.protocol.Meta;
import chat.dim.protocol.Visa;
import chat.dim.type.Duration;

public class CommonArchivist extends Barrack implements Archivist {

    public CommonArchivist(Facebook facebook, AccountDBI db) {
        super();
        facebookRef = new WeakReference<>(facebook);
        database = db;
    }

    protected final AccountDBI database;

    private final WeakReference<Facebook> facebookRef;

    protected Facebook getFacebook() {
        return facebookRef.get();
    }

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

    //
    //  Archivist
    //

    @Override
    public boolean saveMeta(Meta meta, ID identifier) {
        Facebook facebook = getFacebook();
        assert facebook != null : "facebook lost";
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
        Meta old = facebook.getMeta(identifier);
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
        return meta.isValid() && MetaUtils.matches(identifier, meta);
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
            //assert false : "document error: " + doc;
            Log.warning("document without time: " + identifier);
        } else {
            // calibrate the clock
            // make sure the document time is not in the far future
            Date nearFuture = Duration.ofMinutes(30).addTo(new Date());
            if (docTime.after(nearFuture)) {
                assert false : "document time error: " + docTime + ", " + doc;
                Log.error("document time error: " + docTime + ", " + identifier);
                return false;
            }
        }
        // check valid
        return verifyDocument(doc);
    }

    protected boolean verifyDocument(Document doc) {
        if (doc.isValid()) {
            return true;
        }
        ID identifier = doc.getIdentifier();
        Facebook facebook = getFacebook();
        assert facebook != null : "facebook lost";
        Meta meta = facebook.getMeta(identifier);
        if (meta == null) {
            Log.warning("failed to get meta: " + identifier);
            return false;
        }
        return doc.verify(meta.getPublicKey());
    }

    protected boolean checkDocumentExpired(Document doc) {
        Facebook facebook = getFacebook();
        assert facebook != null : "facebook lost";
        ID identifier = doc.getIdentifier();
        String type = DocumentUtils.getDocumentType(doc);
        if (type == null) {
            type = "*";
        }
        // check old documents with type
        List<Document> documents = facebook.getDocuments(identifier);
        Document old = DocumentUtils.lastDocument(documents, type);
        return old != null && DocumentUtils.isExpired(doc, old);
    }

    @Override
    public VerifyKey getMetaKey(ID user) {
        Facebook facebook = getFacebook();
        assert facebook != null : "facebook lost";
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
        assert facebook != null : "facebook lost";
        List<Document> documents = facebook.getDocuments(user);
        Visa doc = DocumentUtils.lastVisa(documents);
        if (doc != null/* && doc.isValid()*/) {
            return doc.getPublicKey();
        }
        return null;
    }

    @Override
    public List<ID> getLocalUsers() {
        return database.getLocalUsers();
    }
}
