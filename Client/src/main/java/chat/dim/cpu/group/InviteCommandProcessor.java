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

import chat.dim.Facebook;
import chat.dim.Messenger;
import chat.dim.protocol.Content;
import chat.dim.protocol.GroupCommand;
import chat.dim.protocol.ID;
import chat.dim.protocol.ReliableMessage;
import chat.dim.protocol.group.InviteCommand;
import chat.dim.type.Copier;
import chat.dim.type.Pair;

/**
 *  Invite Group Command Processor
 *  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
 *
 *      1. add new member(s) to the group
 *      2. any member can invite new member
 *      3. invited by ordinary member should be reviewed by owner/administrator
 */
public class InviteCommandProcessor extends ResetCommandProcessor {

    public InviteCommandProcessor(Facebook facebook, Messenger messenger) {
        super(facebook, messenger);
    }

    @Override
    public List<Content> process(Content content, ReliableMessage rMsg) {
        assert content instanceof InviteCommand : "invite command error: " + content;
        GroupCommand command = (GroupCommand) content;

        // 0. check command
        if (isCommandExpired(command)) {
            // ignore expired command
            return null;
        }
        ID group = command.getGroup();
        List<ID> inviteList = getMembers(command);
        if (inviteList.size() == 0) {
            return respondReceipt("Command error.", rMsg, group, newMap(
                    "template", "Invite list is empty: ${ID}",
                    "replacements", newMap(
                            "ID", group.toString()
                    )
            ));
        }

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
        if (!members.contains(sender)) {
            return respondReceipt("Permission denied.", rMsg, group, newMap(
                    "template", "Not allowed to invite member into group: ${ID}",
                    "replacements", newMap(
                            "ID", group.toString()
                    )
            ));
        }
        List<ID> admins = getAdministrators(group);

        // 3. do invite
        Pair<List<ID>, List<ID>> pair = calculateInvited(members, inviteList);
        List<ID> newMembers = pair.first;
        List<ID> addedList = pair.second;
        if (owner.equals(sender) || (admins != null && admins.contains(sender))) {
            // invited by owner or admin, so
            // append them directly.
            if (addedList.size() > 0 && saveMembers(newMembers, group)) {
                command.put("added", ID.revert(addedList));
            }
        } else if (addedList.size() == 0) {
            // maybe the invited users are already become members,
            // but if it can still receive an 'invite' command here,
            // we should respond the sender with the newest membership again.
            boolean ok = sendResetCommand(group, newMembers, sender);
            assert ok : "failed to send 'reset' command for group: " + group + " => " + sender;
        } else {
            // add 'invite' application for waiting review
            boolean ok = addApplication(command, rMsg);
            assert ok : "failed to add 'invite' application for group: " + group;
        }

        // no need to response this group command
        return null;
    }

    private Pair<List<ID>, List<ID>> calculateInvited(List<ID> members, List<ID> inviteList) {
        List<ID> newMembers = Copier.copyList(members);
        List<ID> addedList = new ArrayList<>();
        for (ID item : inviteList) {
            if (newMembers.contains(item)) {
                continue;
            }
            newMembers.add(item);
            addedList.add(item);
        }
        return new Pair<>(newMembers, addedList);
    }
}
