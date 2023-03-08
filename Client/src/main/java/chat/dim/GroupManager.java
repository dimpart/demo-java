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
package chat.dim;

import java.util.ArrayList;
import java.util.List;

import chat.dim.mkm.User;
import chat.dim.port.Departure;
import chat.dim.protocol.Command;
import chat.dim.protocol.Content;
import chat.dim.protocol.Document;
import chat.dim.protocol.DocumentCommand;
import chat.dim.protocol.GroupCommand;
import chat.dim.protocol.ID;
import chat.dim.protocol.InstantMessage;
import chat.dim.protocol.Meta;
import chat.dim.protocol.MetaCommand;
import chat.dim.protocol.ReliableMessage;
import chat.dim.type.Pair;

/**
 *  This is for sending group message, or managing group members
 */
public final class GroupManager {

    private final ClientMessenger messenger;

    private final ID group;

    public GroupManager(ID gid, ClientMessenger transceiver) {
        super();
        group = gid;
        messenger = transceiver;
    }

    /**
     *  Send group message content
     *
     * @param content - message content
     * @return false on no bots found
     */
    public boolean sendContent(Content content) {
        ID gid = content.getGroup();
        if (gid == null) {
            content.setGroup(group);
        } else if (!gid.equals(group)) {
            throw new IllegalArgumentException("group ID not match: " + gid + ", " + group);
        }
        CommonFacebook facebook = messenger.getFacebook();
        List<ID> assistants = facebook.getAssistants(group);
        Pair<InstantMessage, ReliableMessage> result;
        for (ID bot : assistants) {
            // send to any bot
            result = messenger.sendContent(null, bot, content, Departure.Priority.NORMAL.value);
            if (result.second != null) {
                // only send to one bot
                return true;
            }
        }
        return false;
    }

    private void sendCommand(Command content, ID receiver) {
        assert receiver != null : "receiver should not be empty";
        messenger.sendContent(null, receiver, content, Departure.Priority.NORMAL.value);
    }
    private void sendCommand(Command content, List<ID> members) {
        assert members != null : "receivers should not be empty";
        for (ID receiver : members) {
            sendCommand(content, receiver);
        }
    }

    /**
     *  Invite new members to this group
     *  (only existed member/assistant can do this)
     *
     * @param newMembers - new members ID list
     * @return true on success
     */
    public boolean invite(List<ID> newMembers) {
        CommonFacebook facebook = messenger.getFacebook();
        List<ID> bots = facebook.getAssistants(group);

        // TODO: make sure group meta exists
        // TODO: make sure current user is a member

        // 0. build 'meta/document' command
        Meta meta = facebook.getMeta(group);
        if (meta == null) {
            throw new NullPointerException("failed to get meta for group: " + group);
        }
        Document doc = facebook.getDocument(group, "*");
        Command command;
        if (doc == null) {
            // empty document
            command = MetaCommand.response(group, meta);
        } else {
            command = DocumentCommand.response(group, meta, doc);
        }
        // 1. send 'meta/document' command
        sendCommand(command, bots);                // to all assistants

        // 2. update local members and notice all bots & members
        List<ID> members = facebook.getMembers(group);
        if (members == null || members.size() <= 2) { // new group?
            // 2.0. update local storage
            members = addMembers(newMembers);
            // 2.1. send 'meta/document' command
            sendCommand(command, members);         // to all members
            // 2.3. send 'invite' command with all members
            command = GroupCommand.invite(group, members);
            sendCommand(command, bots);            // to group assistants
            sendCommand(command, members);         // to all members
        } else {
            // 2.1. send 'meta/document' command
            //sendGroupCommand(command, members);  // to old members
            sendCommand(command, newMembers);      // to new members
            // 2.2. send 'invite' command with new members only
            command = GroupCommand.invite(group, newMembers);
            sendCommand(command, bots);            // to group assistants
            sendCommand(command, members);         // to old members
            // 3. update local storage
            members = addMembers(newMembers);
            // 2.4. send 'invite' command with all members
            command = GroupCommand.invite(group, members);
            sendCommand(command, newMembers);      // to new members
        }

        return true;
    }
    public boolean invite(ID member) {
        List<ID> array = new ArrayList<>();
        array.add(member);
        return invite(array);
    }

    /**
     *  Expel members from this group
     *  (only group owner/assistant can do this)
     *
     * @param outMembers - existed member ID list
     * @return true on success
     */
    public boolean expel(List<ID> outMembers) {
        CommonFacebook facebook = messenger.getFacebook();
        ID owner = facebook.getOwner(group);
        List<ID> bots = facebook.getAssistants(group);

        // TODO: make sure group meta exists
        // TODO: make sure current user is the owner

        // 0. check permission
        for (ID assistant : bots) {
            if (outMembers.contains(assistant)) {
                throw new RuntimeException("Cannot expel group assistant: " + assistant);
            }
        }
        if (outMembers.contains(owner)) {
            throw new RuntimeException("Cannot expel group owner: " + owner);
        }

        // 1. update local storage
        List<ID> members = removeMembers(outMembers);

        // 2. send 'expel' command
        Command command = GroupCommand.expel(group, outMembers);
        sendCommand(command, bots);        // to assistants
        sendCommand(command, members);     // to new members
        sendCommand(command, outMembers);  // to expelled members

        return true;
    }
    public boolean expel(ID member) {
        List<ID> array = new ArrayList<>();
        array.add(member);
        return expel(array);
    }

    /**
     *  Quit from this group
     *  (only group member can do this)
     *
     * @return true on success
     */
    public boolean quit() {
        CommonFacebook facebook = messenger.getFacebook();
        ID owner = facebook.getOwner(group);
        List<ID> bots = facebook.getAssistants(group);
        List<ID> members = facebook.getMembers(group);
        User user = facebook.getCurrentUser();
        if (user == null) {
            throw new NullPointerException("failed to get current user");
        }
        ID me = user.getIdentifier();

        // 0. check permission
        if (bots.contains(me)) {
            throw new RuntimeException("group assistant cannot quit: " + me);
        } else if (me.equals(owner)) {
            throw new RuntimeException("group owner cannot quit: " + owner);
        }

        // 1. update local storage
        if (members.remove(me)) {
            facebook.saveMembers(members, group);
        //} else {
        //    // not a member now
        //    return false;
        }

        // 2. send 'quit' command
        Command command = GroupCommand.quit(group);
        sendCommand(command, bots);     // to assistants
        sendCommand(command, members);  // to new members

        return true;
    }

    /**
     *  Query group info
     *
     * @return false on error
     */
    public boolean query() {
        return messenger.queryMembers(group);
    }

    //-------- local storage

    private List<ID> addMembers(List<ID> newMembers) {
        CommonFacebook facebook = messenger.getFacebook();
        List<ID> members = facebook.getMembers(group);
        assert members != null : "failed to get members for group: " + group;
        int count = 0;
        for (ID member : newMembers) {
            if (members.contains(member)) {
                continue;
            }
            members.add(member);
            ++count;
        }
        if (count > 0) {
            facebook.saveMembers(members, group);
        }
        return members;
    }
    private List<ID> removeMembers(List<ID> outMembers) {
        CommonFacebook facebook = messenger.getFacebook();
        List<ID> members = facebook.getMembers(group);
        assert members != null : "failed to get members for group: " + group;
        int count = 0;
        for (ID member : outMembers) {
            if (!members.contains(member)) {
                continue;
            }
            members.remove(member);
            ++count;
        }
        if (count > 0) {
            facebook.saveMembers(members, group);
        }
        return members;
    }
}
