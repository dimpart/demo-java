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
import chat.dim.mkm.User;
import chat.dim.protocol.Content;
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
import chat.dim.type.Pair;
import chat.dim.type.Triplet;

public class GroupCommandProcessor extends HistoryCommandProcessor {

    public GroupCommandProcessor(Facebook facebook, Messenger messenger) {
        super(facebook, messenger);
    }

    private GroupCommandHelper helper;

    protected GroupCommandHelper getHelper() {
        GroupCommandHelper delegate = helper;
        if (delegate == null) {
            helper = delegate = createGroupCommandHelper();
        }
        return delegate;
    }
    protected GroupCommandHelper createGroupCommandHelper() {
        // override for customized helper
        return new GroupCommandHelper(getFacebook(), getMessenger());
    }

    @Override
    protected CommonFacebook getFacebook() {
        Facebook facebook = super.getFacebook();
        assert facebook instanceof CommonFacebook : "facebook error: " + facebook;
        return (CommonFacebook) facebook;
    }

    @Override
    protected CommonMessenger getMessenger() {
        Messenger messenger = super.getMessenger();
        assert messenger instanceof CommonMessenger : "messenger error: " + messenger;
        return (CommonMessenger) messenger;
    }

    protected ID getOwner(ID group) {
        return getHelper().getOwner(group);
    }
    protected List<ID> getAssistants(ID group) {
        return getHelper().getAssistants(group);
    }

    protected List<ID> getAdministrators(ID group) {
        return getHelper().getAdministrators(group);
    }
    protected boolean saveAdministrators(List<ID> members, ID group) {
        return getHelper().saveAdministrators(members, group);
    }

    protected List<ID> getMembers(ID group) {
        return getHelper().getMembers(group);
    }
    protected boolean saveMembers(List<ID> members, ID group) {
        return getHelper().saveMembers(members, group);
    }

    protected Pair<ResetCommand, ReliableMessage> getResetCommandMessage(ID group) {
        return getHelper().getResetCommandMessage(group);
    }
    protected boolean saveResetCommandMessage(ID group, ResetCommand content, ReliableMessage rMsg) {
        return getHelper().saveResetCommandMessage(group, content, rMsg);
    }

    @Override
    public List<Content> process(Content content, ReliableMessage rMsg) {
        assert content instanceof GroupCommand : "group command error: " + content;
        GroupCommand command = (GroupCommand) content;
        return respondReceipt("Command not support.", rMsg.getEnvelope(), command, newMap(
                "template", "Group command (name: ${command}) not support yet!",
                "replacements", newMap(
                        "command", command.getCmd()
                )
        ));
    }

    protected Pair<ID, List<Content>> checkCommandExpired(GroupCommand content, ReliableMessage rMsg) {
        ID group = content.getGroup();
        assert group != null : "group command error: " + content;
        List<Content> errors;
        boolean expired = getHelper().isCommandExpired(content);
        if (expired) {
            errors = respondReceipt("Command expired.", rMsg.getEnvelope(), content, newMap(
                    "template", "Group command expired: ${ID}",
                    "replacements", newMap(
                            "ID", group.toString()
                    )
            ));
            group = null;
        } else {
            errors = null;
        }
        return new Pair<>(group, errors);
    }

    protected Pair<List<ID>, List<Content>> checkCommandMembers(GroupCommand content, ReliableMessage rMsg) {
        ID group = content.getGroup();
        assert group != null : "group command error: " + content;
        List<ID> members = GroupCommandHelper.getMembers(content);
        List<Content> errors;
        if (/*members == null || */members.isEmpty()) {
            errors = respondReceipt("Command error.", rMsg.getEnvelope(), content, newMap(
                    "template", "Group members empty: ${ID}",
                    "replacements", newMap(
                            "ID", group.toString()
                    )
            ));
            members = null;
        } else {
            errors = null;
        }
        return new Pair<>(members, errors);
    }

    protected Triplet<ID, List<ID>, List<Content>> checkGroupMembers(GroupCommand content, ReliableMessage rMsg) {
        ID group = content.getGroup();
        assert group != null : "group command error: " + content;
        ID owner = getOwner(group);
        List<ID> members = getMembers(group);
        List<Content> errors;
        if (owner == null || members == null || members.isEmpty()) {
            // TODO: query group members?
            errors = respondReceipt("Group empty.", rMsg.getEnvelope(), content, newMap(
                    "template", "Group empty: ${ID}",
                    "replacements", newMap(
                            "ID", group.toString()
                    )
            ));
        } else {
            errors = null;
        }
        return new Triplet<>(owner, members, errors);
    }

    /**
     *  attach 'invite', 'join', 'quit' commands to 'reset' command message for owner/admins to review
     */
    @SuppressWarnings("unchecked")
    protected boolean attachApplication(GroupCommand content, ReliableMessage rMsg) {
        assert content instanceof InviteCommand ||
                content instanceof JoinCommand ||
                content instanceof QuitCommand ||
                content instanceof ResignCommand : "group command error: " + content;
        // TODO: attach 'resign' command to document?
        ID group = content.getGroup();
        assert group != null : "group command error: " + content;
        Pair<ResetCommand, ReliableMessage> pair = getResetCommandMessage(group);
        if (pair == null || pair.first == null || pair.second == null) {
            assert false : "failed to get 'reset' command message for group: " + group;
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
        return saveResetCommandMessage(group, cmd, msg);
    }

    /**
     *  send a reset command with newest members to the receiver
     */
    protected boolean sendResetCommand(ID group, List<ID> members, ID receiver) {
        User user = getFacebook().getCurrentUser();
        if (user == null) {
            assert false : "failed to get current user";
            return false;
        }
        ID me = user.getIdentifier();
        Pair<ResetCommand, ReliableMessage> pair = getResetCommandMessage(group);
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
            //assert !EntityType.BOT.equals(me.getType()) : "a bot should not be admin: " + me;
            // this is the group owner (or administrator), so
            // it has permission to reset group members here.
            pair = createResetCommand(group, members);
            if (pair.second == null) {
                assert false : "failed to create 'reset' command for group: " + group;
                return false;
            }
            // because the owner/administrator can create 'reset' command,
            // so no need to save it here.
        }
        // OK, forward the 'reset' command message
        Content content = ForwardContent.create(pair.second);
        getMessenger().sendContent(me, receiver, content, 1);
        return true;
    }

    /**
     *  create 'reset' command message for anyone in the group
     */
    protected Pair<ResetCommand, ReliableMessage> createResetCommand(ID group, List<ID> members) {
        User user = getFacebook().getCurrentUser();
        assert user != null : "failed to get current user";
        ID me = user.getIdentifier();
        // create broadcast 'reset' group message
        Envelope head = Envelope.create(me, ID.ANYONE, null);
        ResetCommand body = GroupCommand.reset(group, members);
        InstantMessage iMsg = InstantMessage.create(head, body);
        // encrypt & sign
        Messenger messenger = getMessenger();
        ReliableMessage rMsg;
        SecureMessage sMsg = messenger.encryptMessage(iMsg);
        if (sMsg == null) {
            assert false : "failed to encrypt message: " + me + " => " + group;
            rMsg = null;
        } else {
            rMsg = messenger.signMessage(sMsg);
            assert rMsg != null : "failed to sign message: " + me + " => " + group;
        }
        return new Pair<>(body, rMsg);
    }

}
