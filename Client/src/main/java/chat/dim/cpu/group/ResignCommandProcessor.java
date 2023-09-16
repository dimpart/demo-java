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
import chat.dim.protocol.Bulletin;
import chat.dim.protocol.Content;
import chat.dim.protocol.Document;
import chat.dim.protocol.DocumentCommand;
import chat.dim.protocol.ID;
import chat.dim.protocol.Meta;
import chat.dim.protocol.ReliableMessage;
import chat.dim.protocol.group.ResignCommand;
import chat.dim.type.Copier;

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
        if (isCommandExpired(command)) {
            // ignore expired command
            return null;
        }
        ID group = command.getGroup();

        // 1. check group
        ID owner = getOwner(group);
        List<ID> members = getMembers(group);
        if (owner == null || members == null || members.size() == 0) {
            // TODO: query group members?
            return respondReceipt("Group empty.", rMsg, group, newMap(
                    "template", "Group empty: ${ID}",
                    "replacements", newMap(
                            "ID", group.toString()
                    )
            ));
        }

        // 2. check permission
        ID sender = rMsg.getSender();
        if (owner.equals(sender)) {
            return respondReceipt("Permission denied.", rMsg, group, newMap(
                    "template", "Owner cannot resign from group: ${ID}",
                    "replacements", newMap(
                            "ID", group.toString()
                    )
            ));
        }
        List<ID> admins = getAdministrators(group);

        // 3. do resign
        if (admins == null || admins.size() == 0) {
            admins = new ArrayList<>();
        } else {
            admins = Copier.copyList(admins);
        }
        boolean isAdmin = admins.contains(sender);
        if (isAdmin) {
            // admin do exist, remove it and update database
            admins.remove(sender);
            boolean ok = saveAdministrators(admins, group);
            assert ok : "failed to save administrators for group: " + group;
        }

        // 4. update bulletin property: 'administrators'
        User user = getFacebook().getCurrentUser();
        assert user != null : "failed to get current user";
        ID me = user.getIdentifier();
        if (owner.equals(me)) {
            // maybe the bulletin in the owner's storage not contains this administrator,
            // but if it can still receive a resign command here, then
            // the owner should update the bulletin and send it out again.
            boolean ok = refreshAdministrators(group, owner, admins);
            assert ok : "failed to refresh admins for group: " + group;
        } else {
            // add 'resign' application for waiting owner to update
            boolean ok = addApplication(command, rMsg);
            assert ok : "failed to add 'resign' application for group: " + group;
        }
        if (!isAdmin) {
            return respondReceipt("Permission denied.", rMsg, group, newMap(
                    "template", "Not an administrator of group: ${ID}",
                    "replacements", newMap(
                            "ID", group.toString()
                    )
            ));
        }

        // no need to response this group command
        return null;
    }

    private boolean refreshAdministrators(ID group, ID owner, List<ID> admins) {
        CommonFacebook facebook = getFacebook();
        // 1. update bulletin
        Document bulletin = updateAdministrators(group, owner, admins);
        if (!facebook.saveDocument(bulletin)) {
            return false;
        }
        Meta meta = facebook.getMeta(group);
        Content content = DocumentCommand.response(group, meta, bulletin);
        // 2. send to assistants
        CommonMessenger messenger = getMessenger();
        List<ID> bots = facebook.getAssistants(group);
        for (ID receiver : bots) {
            assert !owner.equals(receiver) : "group bot should not be owner: " + owner;
            messenger.sendContent(owner, receiver, content, 1);
        }
        return true;
    }

    private Document updateAdministrators(ID group, ID owner, List<ID> admins) {
        CommonFacebook facebook = getFacebook();
        // update document property
        Document bulletin = facebook.getDocument(group, "*");
        if (bulletin == null) {
            assert false : "failed to get document for group: " + group;
            return null;
        }
        assert bulletin instanceof Bulletin : "group document error: " + bulletin;
        bulletin.setProperty("administrators", ID.revert(admins));
        // sign document
        SignKey sKey = facebook.getPrivateKeyForVisaSignature(owner);
        if (sKey == null) {
            assert false : "failed to get sign key for group owner: " + owner + ", group: " + group;
            return null;
        }
        byte[] signature = bulletin.sign(sKey);
        assert signature != null : "failed to sign bulletin for group: " + group;
        return bulletin;
    }
}
