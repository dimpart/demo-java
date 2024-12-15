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
import chat.dim.mkm.User;
import chat.dim.protocol.Content;
import chat.dim.protocol.GroupCommand;
import chat.dim.protocol.ID;
import chat.dim.protocol.ReliableMessage;
import chat.dim.protocol.group.InviteCommand;
import chat.dim.type.Copier;
import chat.dim.type.Pair;
import chat.dim.type.Triplet;
import chat.dim.utils.Log;

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
        Pair<ID, List<Content>> pair = checkCommandExpired(command, rMsg);
        ID group = pair.first;
        if (group == null) {
            // ignore expired command
            return pair.second;
        }
        Pair<List<ID>, List<Content>> pair1 = checkCommandMembers(command, rMsg);
        List<ID> inviteList = pair1.first;
        if (inviteList == null || inviteList.isEmpty()) {
            // command error
            return pair1.second;
        }

        User user = getFacebook().getCurrentUser();
        if (user == null) {
            assert false : "failed to get current user";
            return null;
        }
        ID me = user.getIdentifier();

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
        boolean isMember = members.contains(sender);

        // 2. check permission
        if (!isMember) {
            return respondReceipt("Permission denied.", rMsg.getEnvelope(), command, newMap(
                    "template", "Not allowed to invite member into group: ${ID}",
                    "replacements", newMap(
                            "ID", group.toString()
                    )
            ));
        }
        boolean canReset = isOwner || isAdmin;

        // 3. do invite
        Pair<List<ID>, List<ID>> memPair = calculateInvited(members, inviteList);
        List<ID> newMembers = memPair.first;
        List<ID> addedList = memPair.second;
        if (addedList.isEmpty()) {
            // maybe those users are already become members,
            // but if it can still receive an 'invite' command here,
            // we should respond the sender with the newest membership again.
            if (!canReset && owner.equals(me)) {
                // the sender cannot reset the group, means it's an ordinary member now,
                // and if I am the owner, then send the group history commands
                // to update the sender's memory.
                boolean ok = sendGroupHistories(group, sender);
                assert ok : "failed to send 'reset' command for group: " + group + " => " + sender;
            }
        } else if (!saveGroupHistory(group, command, rMsg)) {
            // here try to append the 'invite' command to local storage as group history
            // it should not failed unless the command is expired
            Log.error("failed to save 'invite' command for group: " + group);
        } else if (!canReset) {
            // the sender cannot reset the group, means it's invited by ordinary member,
            // and the 'invite' command was saved, now waiting for review.
            Log.info("received an 'invite' command, waiting for review");
        } else if (saveMembers(newMembers, group)) {
            // FIXME: this sender has permission to reset the group,
            //        means it must be the owner or an administrator,
            //        usually it should send a 'reset' command instead;
            //        if we received the 'invite' command here, maybe it was confused,
            //        anyway, we just append the new members directly.
            Log.warning("invited by administrator: " + sender + ", group: " + group);
            command.put("added", ID.revert(addedList));
        } else {
            // DB error?
            assert false : "failed to save members for group: " + group;
        }

        // no need to response this group command
        return null;
    }

    protected Pair<List<ID>, List<ID>> calculateInvited(List<ID> members, List<ID> inviteList) {
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
