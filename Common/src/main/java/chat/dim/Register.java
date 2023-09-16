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

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import chat.dim.core.FactoryManager;
import chat.dim.crypto.AsymmetricKey;
import chat.dim.crypto.EncryptKey;
import chat.dim.crypto.PrivateKey;
import chat.dim.crypto.SignKey;
import chat.dim.dbi.AccountDBI;
import chat.dim.dbi.PrivateKeyDBI;
import chat.dim.format.Base64;
import chat.dim.format.DataCoder;
import chat.dim.format.PortableNetworkFile;
import chat.dim.mkm.BaseBulletin;
import chat.dim.mkm.BaseVisa;
import chat.dim.protocol.AnsCommand;
import chat.dim.protocol.BlockCommand;
import chat.dim.protocol.Bulletin;
import chat.dim.protocol.Command;
import chat.dim.protocol.EntityType;
import chat.dim.protocol.HandshakeCommand;
import chat.dim.protocol.ID;
import chat.dim.protocol.LoginCommand;
import chat.dim.protocol.Meta;
import chat.dim.protocol.MetaType;
import chat.dim.protocol.MuteCommand;
import chat.dim.protocol.ReportCommand;
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
     * @param nickname  - user name
     * @param avatarUrl - photo URL
     * @return user ID
     */
    public ID createUser(String nickname, String avatarUrl) {
        //
        //  Step 1: generate private key (with asymmetric algorithm)
        //
        PrivateKey idKey = PrivateKey.generate(PrivateKey.ECC);
        //
        //  Step 2: generate meta with private key (and meta seed)
        //
        Meta meta = Meta.generate(MetaType.ETH.value, idKey, null);
        //
        //  Step 3: generate ID with meta
        //
        ID identifier = ID.generate(meta, EntityType.USER.value, null);
        //
        //  Step 4: generate visa with ID and sign with private key
        //
        PrivateKey msgKey = PrivateKey.generate(AsymmetricKey.RSA);
        Visa visa = createVisa(identifier, nickname, avatarUrl,
                (EncryptKey) msgKey.getPublicKey(), idKey);
        //
        //  Step 5: save private key, meta & visa in local storage
        //          don't forget to upload them onto the DIM station
        //
        database.saveMeta(meta, identifier);
        database.savePrivateKey(idKey, PrivateKeyDBI.META, identifier);
        database.savePrivateKey(msgKey, PrivateKeyDBI.VISA, identifier);
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
        //
        //  Step 1: get private key of founder
        //
        SignKey privateKey = database.getPrivateKeyForVisaSignature(founder);
        //
        //  Step 2: generate meta with private key (and meta seed)
        //
        Meta meta = Meta.generate(MetaType.MKM.value, privateKey, seed);
        //
        //  Step 3: generate ID with meta
        //
        ID identifier = ID.generate(meta, EntityType.GROUP.value, null);
        //
        //  Step 4: generate bulletin with ID and sign with founder's private key
        //
        Bulletin doc = createBulletin(identifier, title, privateKey);
        //
        //  Step 5: save meta & bulletin in local storage
        //          don't forget to upload then onto the DIM station
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

    private static Visa createVisa(ID identifier, String nickname, String avatarUrl,
                                   EncryptKey visaKey, SignKey idKey) {
        assert identifier.isUser() : "user ID error: " + identifier;
        BaseVisa visa = new BaseVisa(identifier);
        // nick name
        visa.setName(nickname);
        // avatar
        if (avatarUrl != null) {
            URI url = URI.create(avatarUrl);
            visa.setAvatar(PortableNetworkFile.create(url, null));
        }
        // public key
        visa.setPublicKey(visaKey);
        // sign it
        byte[] sig = visa.sign(idKey);
        assert sig != null : "failed to sign visa: " + identifier;
        return visa;
    }
    private static Bulletin createBulletin(ID identifier, String title, SignKey privateKey) {
        assert identifier.isGroup() : "group ID error: " + identifier;
        BaseBulletin doc = new BaseBulletin(identifier);
        doc.setName(title);
        byte[] sig = doc.sign(privateKey);
        assert sig != null : "failed to sign bulletin: " + identifier;
        return doc;
    }

    public static void prepare() {
        if (loaded) {
            return;
        }

        // load plugins
        chat.dim.Plugins.registerPlugins();

        // load message/content factories
        registerAllFactories();

        // fix base64 coder
        Base64.coder = new DataCoder() {

            @Override
            public String encode(byte[] data) {
                return java.util.Base64.getEncoder().encodeToString(data);
            }

            @Override
            public byte[] decode(String string) {
                string = string.replace(" ", "");
                string = string.replace("\t", "");
                string = string.replace("\r", "");
                string = string.replace("\n", "");
                return java.util.Base64.getDecoder().decode(string);
            }
        };

        loaded = true;
    }
    private static boolean loaded = false;


    /**
     *  Register All Message/Content/Command Factories
     */
    public static void registerAllFactories() {
        //
        //  Register core factories
        //
        FactoryManager man = FactoryManager.getInstance();
        man.registerAllFactories();

        // Handshake
        Command.setFactory(HandshakeCommand.HANDSHAKE, HandshakeCommand::new);
        // Login
        Command.setFactory(LoginCommand.LOGIN, LoginCommand::new);
        // Report
        Command.setFactory(ReportCommand.REPORT, ReportCommand::new);
        // Mute
        Command.setFactory(MuteCommand.MUTE, MuteCommand::new);
        // Block
        Command.setFactory(BlockCommand.BLOCK, BlockCommand::new);
        // ANS
        Command.setFactory(AnsCommand.ANS, AnsCommand::new);
    }

}
