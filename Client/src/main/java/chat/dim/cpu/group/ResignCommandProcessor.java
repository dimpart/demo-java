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
package chat.dim.cpu.group;

import java.util.ArrayList;
import java.util.List;

import chat.dim.CommonFacebook;
import chat.dim.CommonMessenger;
import chat.dim.Facebook;
import chat.dim.Messenger;
import chat.dim.cpu.GroupCommandProcessor;
import chat.dim.crypto.SignKey;
import chat.dim.mkm.User;
import chat.dim.protocol.Content;
import chat.dim.protocol.Document;
import chat.dim.protocol.DocumentCommand;
import chat.dim.protocol.ID;
import chat.dim.protocol.Meta;
import chat.dim.protocol.ReliableMessage;
import chat.dim.protocol.group.ResignCommand;
import chat.dim.type.Copier;
import chat.dim.type.Pair;
import chat.dim.type.Triplet;
import chat.dim.utils.Log;

/**
 *  Resign Group Admin Command Processor
 *  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
 *
 *      1. remove the sender from administrators of the group
 *      2. administrator can be hired/fired by owner only
 */
public class ResignCommandProcessor extends GroupCommandProcessor {

    public ResignCommandProcessor(Facebook facebook, Messenger messenger) {
        super(facebook, messenger);
    }

    @Override
    public List<Content> process(Content content, ReliableMessage rMsg) {
        assert content instanceof ResignCommand : "resign command error: " + content;
        ResignCommand command = (ResignCommand) content;

        // 0. check command
        Pair<ID, List<Content>> pair = checkCommandExpired(command, rMsg);
        ID group = pair.first;
        if (group == null) {
            // ignore expired command
            return pair.second;
        }

        // 1. check group
        Triplet<ID, List<ID>, List<Content>> trip = checkGroupMembers(command, rMsg);
        ID owner = trip.first;
        List<ID> members = trip.second;
        if (owner == null || members == null || members.isEmpty()) {
            return trip.third;
        }

        ID sender = rMsg.getSender();
        List<ID> admins = getAdministrators(group);
        if (admins == null) {
            admins = new ArrayList<>();
        }
        boolean isOwner = owner.equals(sender);
        boolean isAdmin = admins.contains(sender);

        // 2. check permission
        if (isOwner) {
            return respondReceipt("Permission denied.", rMsg.getEnvelope(), command, newMap(
                    "template", "Owner cannot resign from group: ${ID}",
                    "replacements", newMap(
                            "ID", group.toString()
                    )
            ));
        }

        // 3. do resign
        if (isAdmin) {
            admins = Copier.copyList(admins);
            // admin do exist, remove it and update database
            admins.remove(sender);
            if (saveAdministrators(admins, group)) {
                List<String> removeList = new ArrayList<>();
                removeList.add(sender.toString());
                command.put("removed", removeList);
            } else {
                assert false : "failed to save administrators for group: " + group;
            }
        }

        User user = getFacebook().getCurrentUser();
        if (user == null) {
            assert false : "failed to get current user";
            return null;
        }
        ID me = user.getIdentifier();

        // 4. update bulletin property: 'administrators'
        if (owner.equals(me)) {
            // maybe the bulletin in the owner's storage not contains this administrator,
            // but if it can still receive a resign command here, then
            // the owner should update the bulletin and send it out again.
            boolean ok = refreshAdministrators(group, owner, admins);
            assert ok : "failed to refresh admins for group: " + group;
        } else if (attachApplication(command, rMsg)) {
            // add 'resign' application for querying by other members,
            // if thw owner wakeup, it will broadcast a new bulletin document
            // with the newest administrators, and this application will be erased.
            Log.info("added 'resign' application for group: " + group);
        } else {
            assert false : "failed to add 'resign' application for group: " + group;
        }

        // no need to response this group command
        return null;
    }

    private boolean refreshAdministrators(ID group, ID owner, List<ID> admins) {
        CommonFacebook facebook = getFacebook();
        CommonMessenger messenger = getMessenger();
        // 1. update bulletin
        Document bulletin = updateAdministrators(group, owner, admins);
        if (bulletin == null) {
            assert false : "failed to update administrators for group: " + group;
            return false;
        } else if (facebook.saveDocument(bulletin)) {
            Log.info("save document for group: " + group);
        } else {
            assert false : "failed to save document for group: " + group;
            return false;
        }
        Meta meta = facebook.getMeta(group);
        Content content = DocumentCommand.response(group, meta, bulletin);
        // 2. check assistants
        List<ID> bots = getAssistants(group);
        if (bots == null || bots.isEmpty()) {
            // TODO: broadcast to all members
            return true;
        }
        // 3. broadcast to all group assistants
        for (ID receiver : bots) {
            if (owner.equals(receiver)) {
                assert false : "group bot should not be owner: " + owner + ", group: " + group;
                continue;
            }
            messenger.sendContent(owner, receiver, content, 1);
        }
        return true;
    }

    private Document updateAdministrators(ID group, ID owner, List<ID> admins) {
        CommonFacebook facebook = getFacebook();
        // get document & sign key
        Document bulletin = facebook.getDocument(group, "*");
        SignKey sKey = facebook.getPrivateKeyForVisaSignature(owner);
        if (bulletin == null || sKey == null) {
            assert false : "failed to get document & sign key for group: " + group + ", owner: " + owner;
            return null;
        }
        // assert bulletin instanceof Bulletin : "group document error: " + bulletin;
        bulletin.setProperty("administrators", ID.revert(admins));
        byte[] signature = bulletin.sign(sKey);
        assert signature != null : "failed to sign bulletin for group: " + group;
        return bulletin;
    }
}
