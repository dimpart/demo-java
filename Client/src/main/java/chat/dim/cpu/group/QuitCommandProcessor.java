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
package chat.dim.cpu.group;

import java.util.ArrayList;
import java.util.List;

import chat.dim.CommonMessenger;
import chat.dim.Facebook;
import chat.dim.Messenger;
import chat.dim.cpu.GroupCommandProcessor;
import chat.dim.mkm.User;
import chat.dim.protocol.Content;
import chat.dim.protocol.ForwardContent;
import chat.dim.protocol.GroupCommand;
import chat.dim.protocol.ID;
import chat.dim.protocol.ReliableMessage;
import chat.dim.protocol.group.QuitCommand;
import chat.dim.protocol.group.ResetCommand;
import chat.dim.type.Copier;
import chat.dim.type.Pair;
import chat.dim.utils.Log;

/**
 *  Quit Group Command Processor
 *  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~
 *
 *      1. remove the sender from members of the group
 *      2. owner and administrator cannot quit
 */
public class QuitCommandProcessor extends GroupCommandProcessor {

    public QuitCommandProcessor(Facebook facebook, Messenger messenger) {
        super(facebook, messenger);
    }

    @Override
    public List<Content> process(Content content, ReliableMessage rMsg) {
        assert content instanceof QuitCommand : "quit command error: " + content;
        GroupCommand command = (GroupCommand) content;

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
                    "template", "Owner cannot quit from group: ${ID}",
                    "replacements", newMap(
                            "ID", group.toString()
                    )
            ));
        }
        List<ID> admins = getAdministrators(group);
        if (admins != null && admins.contains(sender)) {
            return respondReceipt("Permission denied.", rMsg, group, newMap(
                    "template", "Administrator cannot quit from group: ${ID}",
                    "replacements", newMap(
                            "ID", group.toString()
                    )
            ));
        }

        // 3. do quit
        members = Copier.copyList(members);
        boolean isMember = members.contains(sender);
        if (isMember) {
            // member do exist, remove it and update database
            members.remove(sender);
            if (saveMembers(members, group)) {
                List<String> removeList = new ArrayList<>();
                removeList.add(sender.toString());
                content.put("removed", removeList);
            }
        }

        // 4. update 'reset' command
        User user = getFacebook().getCurrentUser();
        assert user != null : "failed to get current user";
        ID me = user.getIdentifier();
        if (owner.equals(me) || (admins != null && admins.contains(me))) {
            // this is the group owner (or administrator), so
            // it has permission to reset group members here.
            boolean ok = refreshMembers(group, me, members);
            assert ok : "failed to refresh members: " + group;
        } else {
            // add 'quit' application for waiting admin to update
            boolean ok = addApplication(command, rMsg);
            assert ok : "failed to add 'quit' application for group: " + group;
        }
        if (!isMember) {
            return respondReceipt("Permission denied.", rMsg, group, newMap(
                    "template", "Not a member of group: ${ID}",
                    "replacements", newMap(
                            "ID", group.toString()
                    )
            ));
        }

        // no need to response this group command
        return null;
    }

    private boolean refreshMembers(ID group, ID admin, List<ID> members) {
        // 1. create new 'reset' command
        Pair<ResetCommand, ReliableMessage> pair =  createResetCommand(admin, group, members);
        ResetCommand cmd = pair.first;
        ReliableMessage msg = pair.second;
        if (updateResetCommandMessage(group, cmd, msg)) {
            Log.info("updated 'reset' command for group: " + group);
        } else {
            // failed to save 'reset' command message
            return false;
        }
        CommonMessenger messenger = getMessenger();
        Content forward = ForwardContent.create(msg);
        // 2. forward to assistants
        List<ID> bots = getAssistants(group);
        if (bots != null/* && bots.size() > 0*/) {
            for (ID receiver : bots) {
                assert !admin.equals(bots) : "group bot should not be admin: " + admin;
                messenger.sendContent(admin, receiver, forward, 1);
            }
        }
        return true;
    }
}
