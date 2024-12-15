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

import java.util.ArrayList;
import java.util.List;

import chat.dim.dbi.AccountDBI;
import chat.dim.group.BroadcastHelper;
import chat.dim.mkm.User;
import chat.dim.protocol.Address;
import chat.dim.protocol.Bulletin;
import chat.dim.protocol.Document;
import chat.dim.protocol.EntityType;
import chat.dim.protocol.ID;
import chat.dim.protocol.Meta;

/**
 *  Client Facebook with Address Name Service
 */
public abstract class ClientFacebook extends CommonFacebook {

    public ClientFacebook(AccountDBI database) {
        super(database);
    }

    @Override
    public User selectLocalUser(ID receiver) {
        if (receiver.isUser()) {
            return super.selectLocalUser(receiver);
        }
        // group message (recipient not designated)
        assert receiver.isGroup() : "receiver error: " + receiver;
        // the messenger will check group info before decrypting message,
        // so we can trust that the group's meta & members MUST exist here.
        Archivist archivist = getArchivist();
        List<User> users = archivist.getLocalUsers();
        List<ID> members = getMembers(receiver);
        assert !members.isEmpty() : "members not found: " + receiver;
        for (User item : users) {
            if (members.contains(item.getIdentifier())) {
                // DISCUSS: set this item to be current user?
                return item;
            }
        }
        return null;
    }

    @Override
    public boolean saveDocument(Document doc) {
        boolean ok = super.saveDocument(doc);
        if (ok && doc instanceof Bulletin) {
            // check administrators
            Object array = doc.getProperty("administrators");
            if (array instanceof List) {
                ID group = doc.getIdentifier();
                assert group.isGroup() : "group ID error: " + group;
                List<ID> admins = ID.convert((List<?>) array);
                ok = saveAdministrators(admins, group);
            }
        }
        return ok;
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
            user = getFounder(group);
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
        // check broadcast group
        if (group.isBroadcast()) {
            // members of broadcast group
            return BroadcastHelper.getBroadcastMembers(group);
        }
        // check group owner
        ID owner = getOwner(group);
        if (owner == null) {
            // assert false : "group owner not found: " + group;
            return null;
        }
        // check local storage
        AccountDBI db = getDatabase();
        List<ID> members = db.getMembers(group);
        EntityChecker checker = getEntityChecker();
        checker.checkMembers(group, members);
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

    //
    //  Organizational Structure
    //

    @Override
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

    @Override
    public boolean saveAdministrators(List<ID> members, ID group) {
        AccountDBI db = getDatabase();
        return db.saveAdministrators(members, group);
    }

    @Override
    public boolean saveMembers(List<ID> newMembers, ID group) {
        AccountDBI db = getDatabase();
        return db.saveMembers(newMembers, group);
    }

    //
    //  Address Name Service
    //
    public static AddressNameServer ans = null;

    static void prepare() {
        if (loaded) {
            return;
        }

        // load plugins
        Register.prepare();

        identifierFactory = ID.getFactory();
        ID.setFactory(new ID.Factory() {

            @Override
            public ID generateIdentifier(Meta meta, int type, String terminal) {
                return identifierFactory.generateIdentifier(meta, type, terminal);
            }

            @Override
            public ID createIdentifier(String name, Address address, String terminal) {
                return identifierFactory.createIdentifier(name, address, terminal);
            }

            @Override
            public ID parseIdentifier(String identifier) {
                // try ANS record
                ID id = ans.identifier(identifier);
                if (id != null) {
                    return id;
                }
                // parse by original factory
                return identifierFactory.parseIdentifier(identifier);
            }
        });

        loaded = true;
    }
    private static boolean loaded = false;

    private static ID.Factory identifierFactory;

}
