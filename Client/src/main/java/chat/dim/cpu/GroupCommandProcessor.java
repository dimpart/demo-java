/* license: https://mit-license.org
 *
 *  DIM-SDK : Decentralized Instant Messaging Software Development Kit
 *
 *                                Written in 2019 by Moky <albert.moky@gmail.com>
 *
 * ==============================================================================
 * The MIT License (MIT)
 *
 * Copyright (c) 2019 Albert Moky
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
package chat.dim.cpu;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import chat.dim.CommonFacebook;
import chat.dim.CommonMessenger;
import chat.dim.Facebook;
import chat.dim.Messenger;
import chat.dim.dbi.AccountDBI;
import chat.dim.mkm.User;
import chat.dim.protocol.Content;
import chat.dim.protocol.Document;
import chat.dim.protocol.EntityType;
import chat.dim.protocol.Envelope;
import chat.dim.protocol.ForwardContent;
import chat.dim.protocol.GroupCommand;
import chat.dim.protocol.ID;
import chat.dim.protocol.InstantMessage;
import chat.dim.protocol.ReliableMessage;
import chat.dim.protocol.SecureMessage;
import chat.dim.protocol.group.InviteCommand;
import chat.dim.protocol.group.JoinCommand;
import chat.dim.protocol.group.QuitCommand;
import chat.dim.protocol.group.ResetCommand;
import chat.dim.protocol.group.ResignCommand;
import chat.dim.type.Copier;
import chat.dim.type.Pair;

public class GroupCommandProcessor extends HistoryCommandProcessor {

    public GroupCommandProcessor(Facebook facebook, Messenger messenger) {
        super(facebook, messenger);
    }

    protected CommonFacebook getFacebook() {
        Facebook facebook = super.getFacebook();
        assert facebook instanceof CommonFacebook : "facebook error: " + facebook;
        return (CommonFacebook) facebook;
    }

    protected CommonMessenger getMessenger() {
        Messenger messenger = super.getMessenger();
        assert messenger instanceof CommonMessenger : "messenger error: " + messenger;
        return (CommonMessenger) messenger;
    }

    protected ID getOwner(ID group) {
        return getFacebook().getOwner(group);
    }
    protected List<ID> getMembers(ID group) {
        return getFacebook().getMembers(group);
    }
    protected List<ID> getAssistants(ID group) {
        return getFacebook().getAssistants(group);
    }
    protected List<ID> getAdministrators(ID group) {
        AccountDBI db = getFacebook().getDatabase();
        return db.getAdministrators(group);
    }

    protected boolean saveMembers(List<ID> members, ID group) {
        AccountDBI db = getFacebook().getDatabase();
        return db.saveMembers(members, group);
    }
    protected boolean saveAdministrators(List<ID> members, ID group) {
        AccountDBI db = getFacebook().getDatabase();
        return db.saveAdministrators(members, group);
    }

    protected static List<ID> getMembers(GroupCommand content) {
        // get from 'members'
        List<ID> members = content.getMembers();
        if (members == null) {
            members = new ArrayList<>();
            // get from 'member'
            ID member = content.getMember();
            if (member != null) {
                members.add(member);
            }
        }
        return members;
    }

    @Override
    public List<Content> process(Content content, ReliableMessage rMsg) {
        assert content instanceof GroupCommand : "group command error: " + content;
        GroupCommand command = (GroupCommand) content;
        return respondReceipt("Command not support.", rMsg, command.getGroup(), newMap(
                "template", "Group command (name: ${command}) not support yet!",
                "replacements", newMap(
                        "command", command.getCmd()
                )
        ));
    }

    protected boolean isCommandExpired(GroupCommand content) {
        CommonFacebook facebook = getFacebook();
        ID group = content.getGroup();
        if (content instanceof ResignCommand) {
            // administrator command, check with document time
            Document bulletin = facebook.getDocument(group, "*");
            if (bulletin == null) {
                return false;
            }
            return AccountDBI.isExpired(bulletin.getTime(), content.getTime());
        }
        // membership command, check with reset command
        AccountDBI db = facebook.getDatabase();
        Pair<ResetCommand, ReliableMessage> pair = db.getResetCommandMessage(group);
        if (pair == null || pair.first == null/* || pair.second == null*/) {
            return false;
        }
        return AccountDBI.isExpired(pair.first.getTime(), content.getTime());
    }

    /**
     *  attach 'invite', 'join', 'quit' commands to 'reset' command message for owner/admins to review
     */
    @SuppressWarnings("unchecked")
    protected boolean addApplication(GroupCommand content, ReliableMessage rMsg) {
        assert content instanceof InviteCommand ||
                content instanceof JoinCommand ||
                content instanceof QuitCommand ||
                content instanceof ResignCommand : "group command error: " + content;
        // TODO: attach 'resign' command to document?
        CommonFacebook facebook = getFacebook();
        AccountDBI db = facebook.getDatabase();
        ID group = content.getGroup();
        Pair<ResetCommand, ReliableMessage> pair = db.getResetCommandMessage(group);
        if (pair == null || pair.first == null || pair.second == null) {
            User user = facebook.getCurrentUser();
            assert user != null : "failed to get current user";
            ID me = user.getIdentifier();
            // TODO: check whether current user is the owner or an administrator
            //       if True, create a new 'reset' command with current members
            assert EntityType.BOT.equals(me.getType()) : "failed to get reset command for group: " + group;
            return false;
        }
        ResetCommand cmd = pair.first;
        ReliableMessage msg = pair.second;
        Object applications = msg.get("applications");
        List<Map<?, ?>> array;
        if (applications instanceof List) {
            array = (List<Map<?, ?>>) applications;
        } else {
            array = new ArrayList<>();
            msg.put("applications", array);
        }
        array.add(rMsg.toMap());
        return db.saveResetCommandMessage(group, cmd, msg);
    }

    /**
     *  send a reset command with newest members to the receiver
     */
    protected boolean sendResetCommand(ID group, List<ID> members, ID receiver) {
        CommonFacebook facebook = getFacebook();
        User user = facebook.getCurrentUser();
        assert user != null : "failed to get current user";
        ID me = user.getIdentifier();
        AccountDBI db = facebook.getDatabase();
        Pair<ResetCommand, ReliableMessage> pair = db.getResetCommandMessage(group);
        if (pair == null || pair.second == null) {
            // 'reset' command message not found in local storage
            // check permission for creating a new one
            ID owner = getOwner(group);
            if (!me.equals(owner)) {
                // not group owner, check administrators
                List<ID> admins = getAdministrators(group);
                if (admins == null || !admins.contains(me)) {
                    // only group owner or administrators can reset group members
                    return false;
                }
            }
            assert !EntityType.BOT.equals(me.getType()) : "a bot should not be admin: " + me;
            // this is the group owner (or administrator), so
            // it has permission to reset group members here.
            pair = createResetCommand(me, group, members);
            if (!db.saveResetCommandMessage(group, pair.first, pair.second)) {
                // failed to save 'reset' command message
                return false;
            }
        }
        // OK, forward the 'reset' command message
        Content content = ForwardContent.create(pair.second);
        getMessenger().sendContent(me, receiver, content, 1);
        return true;
    }

    /**
     *  create 'reset' command message for anyone in the group
     */
    protected Pair<ResetCommand, ReliableMessage> createResetCommand(ID sender, ID group, List<ID> members) {
        Envelope head = Envelope.create(sender, ID.ANYONE, null);
        ResetCommand body = GroupCommand.reset(group, members);
        InstantMessage iMsg = InstantMessage.create(head, body);
        // encrypt & sign
        Messenger messenger = getMessenger();
        SecureMessage sMsg = messenger.encryptMessage(iMsg);
        assert sMsg != null : "failed to encrypt message: " + sender + " => " + group;
        ReliableMessage rMsg = messenger.signMessage(sMsg);
        assert rMsg != null : "failed to sign message: " + sender + " => " + group;
        return new Pair<>(body, rMsg);
    }

    /**
     *  save 'reset' command message with 'applications
     */
    @SuppressWarnings("unchecked")
    protected boolean updateResetCommandMessage(ID group, ResetCommand content, ReliableMessage rMsg) {
        AccountDBI db = getFacebook().getDatabase();
        // 1. get applications
        List<Map<?, ?>> applications = null;
        Pair<ResetCommand, ReliableMessage> pair = db.getResetCommandMessage(group);
        if (pair != null && pair.second != null) {
            applications = (List<Map<?, ?>>) pair.second.get("applications");
        }
        if (applications == null) {
            applications = (List<Map<?, ?>>) rMsg.get("applications");
        } else {
            List<Map<?, ?>> invitations = (List<Map<?, ?>>) rMsg.get("applications");
            if (invitations != null) {
                applications = Copier.copyList(applications);
                // merge applications
                applications.addAll(invitations);
            }
        }
        // 2. update applications
        if (applications != null) {
            rMsg.put("applications", applications);
        }
        // 3. save reset command message
        return db.saveResetCommandMessage(group, content, rMsg);
    }
}
