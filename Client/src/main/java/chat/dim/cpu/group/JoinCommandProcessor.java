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

import chat.dim.Facebook;
import chat.dim.Messenger;
import chat.dim.cpu.GroupCommandProcessor;
import chat.dim.mkm.User;
import chat.dim.protocol.Content;
import chat.dim.protocol.GroupCommand;
import chat.dim.protocol.ID;
import chat.dim.protocol.ReliableMessage;
import chat.dim.protocol.group.JoinCommand;
import chat.dim.type.Pair;
import chat.dim.type.Triplet;

/**
 *  Join Group Command Processor
 *  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~
 *
 *      1. stranger can join a group
 *      2. only group owner or administrator can review this command
 */
public class JoinCommandProcessor extends GroupCommandProcessor {

    public JoinCommandProcessor(Facebook facebook, Messenger messenger) {
        super(facebook, messenger);
    }

    @Override
    public List<Content> process(Content content, ReliableMessage rMsg) {
        assert content instanceof JoinCommand : "join command error: " + content;
        GroupCommand command = (GroupCommand) content;

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
        boolean isMember = members.contains(sender);
        boolean canReset = isOwner || isAdmin;

        User user = getFacebook().getCurrentUser();
        if (user == null) {
            assert false : "failed to get current user";
            return null;
        }
        ID me = user.getIdentifier();

        // 2. check membership
        if (isMember) {
            // maybe the command sender is already become a member,
            // but if it can still receive a 'join' command here,
            // and I am the owner, here we should respond the sender
            // with the newest membership again.
            if (!canReset && owner.equals(me)) {
                // the sender is an ordinary member, and I am the owner, so
                // send a 'reset' command to update members in the sender's memory
                boolean ok = sendResetCommand(group, members, sender);
                assert ok : "failed to send 'reset' for group: " + group + " => " + sender;
            }
        } else if (owner.equals(me) || admins.contains(me)) {
            // I am the owner, or an administrator, so
            // attach it as a 'join' application and wait for review.
            boolean ok = attachApplication(command, rMsg);
            assert ok : "failed to add 'join' application for group: " + group;
        //} else {
        //    // I am not the administrator, just ignore it
        }

        // no need to response this group command
        return null;
    }

}
