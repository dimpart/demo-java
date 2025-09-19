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
import java.util.Random;

import chat.dim.dbi.AccountDBI;
import chat.dim.dbi.PrivateKeyDBI;
import chat.dim.mkm.BaseBulletin;
import chat.dim.mkm.BaseVisa;
import chat.dim.protocol.AsymmetricAlgorithms;
import chat.dim.protocol.Bulletin;
import chat.dim.protocol.EncryptKey;
import chat.dim.protocol.EntityType;
import chat.dim.protocol.ID;
import chat.dim.protocol.Meta;
import chat.dim.protocol.MetaType;
import chat.dim.protocol.PortableNetworkFile;
import chat.dim.protocol.PrivateKey;
import chat.dim.protocol.SignKey;
import chat.dim.protocol.Visa;

public class Register {

    private final AccountDBI database;

    public Register(AccountDBI adb) {
        super();
        database = adb;
    }

    /**
     *  Generate user account
     *
     * @param nickname - user name
     * @param avatar   - photo URL
     * @return user ID
     */
    public ID createUser(String nickname, PortableNetworkFile avatar) {
        //
        //  Step 1: generate private key (with asymmetric algorithm)
        //
        PrivateKey idKey = PrivateKey.generate(AsymmetricAlgorithms.ECC);
        //
        //  Step 2: generate meta with private key (and meta seed)
        //
        Meta meta = Meta.generate(MetaType.ETH, idKey, null);
        //
        //  Step 3: generate ID with meta
        //
        ID identifier = ID.generate(meta, EntityType.USER.value, null);
        //
        //  Step 4: generate visa with ID and sign with private key
        //
        PrivateKey msgKey = PrivateKey.generate(AsymmetricAlgorithms.RSA);
        EncryptKey visaKey = (EncryptKey) msgKey.getPublicKey();
        Visa visa = createVisa(identifier, visaKey, idKey, nickname, avatar);
        //
        //  Step 5: save private key, meta & visa in local storage
        //
        database.savePrivateKey(idKey, PrivateKeyDBI.META, identifier);
        database.savePrivateKey(msgKey, PrivateKeyDBI.VISA, identifier);
        database.saveMeta(meta, identifier);
        database.saveDocument(visa);
        // OK
        return identifier;
    }

    /**
     *  Generate group account
     *
     * @param founder - group founder
     * @param title   - group name
     * @return group ID
     */
    public ID createGroup(ID founder, String title) {
        Random random = new Random();
        long r = random.nextInt(999990000) + 10000; // 10,000 ~ 999,999,999
        return createGroup(founder, title, "Group-" + r);
    }
    public ID createGroup(ID founder, String title, String seed) {
        assert seed.length() > 0 : "group's meta seed should not be empty";
        //
        //  Step 1: get private key of founder
        //
        SignKey privateKey = database.getPrivateKeyForVisaSignature(founder);
        //
        //  Step 2: generate meta with private key (and meta seed)
        //
        Meta meta = Meta.generate(MetaType.MKM, privateKey, seed);
        //
        //  Step 3: generate ID with meta
        //
        ID identifier = ID.generate(meta, EntityType.GROUP.value, null);
        //
        //  Step 4: generate bulletin with ID and sign with founder's private key
        //
        Bulletin doc = createBulletin(identifier, privateKey, title, founder);
        //
        //  Step 5: save meta & bulletin in local storage
        //
        database.saveMeta(meta, identifier);
        database.saveDocument(doc);
        //
        //  Step 6: add founder as first member
        //
        List<ID> members = new ArrayList<>();
        members.add(founder);
        database.saveMembers(members, identifier);
        // OK
        return identifier;
    }

    protected Visa createVisa(ID identifier, EncryptKey visaKey, SignKey idKey,
                              String nickname, PortableNetworkFile avatar) {
        assert identifier.isUser() : "user ID error: " + identifier;
        Visa doc = new BaseVisa(identifier);
        // App ID
        doc.setProperty("app_id", "chat.dim.tarsier");
        // nickname
        doc.setName(nickname);
        // avatar
        if (avatar != null) {
            doc.setAvatar(avatar);
        }
        // public key
        doc.setPublicKey(visaKey);
        // sign it
        byte[] sig = doc.sign(idKey);
        assert sig != null : "failed to sign visa: " + identifier;
        return doc;
    }
    protected Bulletin createBulletin(ID identifier, SignKey privateKey,
                                      String title, ID founder) {
        assert identifier.isGroup() : "group ID error: " + identifier;
        Bulletin doc = new BaseBulletin(identifier);
        // App ID
        doc.setProperty("app_id", "chat.dim.tarsier");
        // group founder
        doc.setProperty("founder", founder.toString());
        // group name
        doc.setName(title);
        // sign it
        byte[] sig = doc.sign(privateKey);
        assert sig != null : "failed to sign bulletin: " + identifier;
        return doc;
    }

}
