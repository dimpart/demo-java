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
package chat.dim.group;

import java.util.List;

import chat.dim.CommonFacebook;
import chat.dim.CommonMessenger;
import chat.dim.crypto.SignKey;
import chat.dim.mkm.Station;
import chat.dim.mkm.User;
import chat.dim.protocol.Bulletin;
import chat.dim.protocol.Command;
import chat.dim.protocol.DocumentCommand;
import chat.dim.protocol.ID;
import chat.dim.protocol.Meta;
import chat.dim.utils.Log;

public class AdminManager {

    protected final GroupDelegate delegate;

    public AdminManager(GroupDelegate dataSource) {
        super();
        delegate = dataSource;
    }

    protected CommonFacebook getFacebook() {
        return delegate.getFacebook();
    }
    protected CommonMessenger getMessenger() {
        return delegate.getMessenger();
    }

    /**
     *  Update 'administrators' in bulletin document
     *  (broadcast new document to all members and neighbor station)
     *
     * @param group     - group ID
     * @param newAdmins - administrator list
     * @return false on error
     */
    public boolean updateAdministrators(ID group, List<ID> newAdmins) {
        assert group.isGroup() : "group ID error: " + group;
        CommonFacebook facebook = getFacebook();
        assert facebook != null : "facebook not ready";

        //
        //  0. get current user
        //
        User user = facebook.getCurrentUser();
        if (user == null) {
            assert false : "failed to get current user";
            return false;
        }
        ID me = user.getIdentifier();
        SignKey sKey = facebook.getPrivateKeyForVisaSignature(me);
        assert sKey != null : "failed to get sign key for current user: " + me;

        //
        //  1. check permission
        //
        boolean isOwner = delegate.isOwner(me, group);
        if (!isOwner) {
            assert false : "cannot update administrators for group: " + group + ", " + me;
            return false;
        }

        //
        //  2. update document
        //
        Bulletin doc = delegate.getBulletin(group);
        if (doc == null) {
            // TODO: create new one?
            assert false : "failed to get group document: " + group + ", owner: " + me;
            return false;
        }
        doc.setProperty("administrators", ID.revert(newAdmins));
        byte[] signature = doc.sign(sKey);
        if (signature == null) {
            assert false : "failed to sign document for group: " + group + ", owner: " + me;
            return false;
        } else if (!delegate.saveDocument(doc)) {
            assert false : "failed to save document for group: " + group;
            return false;
        } else {
            Log.info("group document updated: " + group);
        }

        //
        //  3. broadcast bulletin document
        //
        return broadcastDocument((Bulletin) doc);
    }

    /**
     *  Broadcast group document
     */
    public boolean broadcastDocument(Bulletin doc) {
        CommonFacebook facebook = getFacebook();
        CommonMessenger messenger = getMessenger();
        assert facebook != null && messenger != null : "facebook messenger not ready: " + facebook + ", " + messenger;

        //
        //  0. get current user
        //
        User user = facebook.getCurrentUser();
        if (user == null) {
            assert false : "failed to get current user";
            return false;
        }
        ID me = user.getIdentifier();

        //
        //  1. create 'document' command, and send to current station
        //
        ID group = doc.getIdentifier();
        Meta meta = facebook.getMeta(group);
        Command command = DocumentCommand.response(group, meta, doc);
        messenger.sendContent(me, Station.ANY, command, 1);

        //
        //  2. check group bots
        //
        List<ID> bots = delegate.getAssistants(group);
        if (bots != null && bots.size() > 0) {
            // group bots exist, let them to deliver to all other members
            for (ID item : bots) {
                if (me.equals(item)) {
                    assert false : "should not be a bot here: " + me;
                    continue;
                }
                messenger.sendContent(me, item, command, 1);
            }
            return true;
        }

        //
        //  3. broadcast to all members
        //
        List<ID> members = delegate.getMembers(group);
        if (members == null || members.isEmpty()) {
            assert false : "failed to get group members: " + group;
            return false;
        }
        for (ID item : members) {
            if (me.equals(item)) {
                Log.info("skip cycled message: " + item + ", " + group);
                continue;
            }
            messenger.sendContent(me, item, command, 1);
        }
        return true;
    }

}
