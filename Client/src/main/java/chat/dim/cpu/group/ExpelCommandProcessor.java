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
import chat.dim.cpu.GroupCommandProcessor;
import chat.dim.protocol.Content;
import chat.dim.protocol.GroupCommand;
import chat.dim.protocol.ID;
import chat.dim.protocol.ReliableMessage;
import chat.dim.protocol.group.ExpelCommand;
import chat.dim.type.Pair;
import chat.dim.type.Triplet;

/**
 *  Expel Group Command Processor
 *  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
 *
 *      1. remove group member(s)
 *      2. only group owner or administrator can expel member
 */
public class ExpelCommandProcessor extends GroupCommandProcessor {

    public ExpelCommandProcessor(Facebook facebook, Messenger messenger) {
        super(facebook, messenger);
    }

    @Override
    public List<Content> process(Content content, ReliableMessage rMsg) {
        assert content instanceof ExpelCommand : "expel command error: " + content;
        GroupCommand command = (GroupCommand) content;

        // 0. check command
        Pair<ID, List<Content>> pair = checkCommandExpired(command, rMsg);
        ID group = pair.first;
        if (group == null) {
            // ignore expired command
            return pair.second;
        }
        Pair<List<ID>, List<Content>> pair1 = checkCommandMembers(command, rMsg);
        List<ID> expelList = pair1.first;
        if (expelList == null || expelList.isEmpty()) {
            // command error
            return pair1.second;
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
        boolean canExpel = isOwner || isAdmin;
        if (!canExpel) {
            return respondReceipt("Permission denied.", rMsg.getEnvelope(), command, newMap(
                    "template", "Not allowed to expel member from group: ${ID}",
                    "replacements", newMap(
                            "ID", group.toString()
                    )
            ));
        }
        // 2.1. check owner
        if (expelList.contains(owner)) {
            return respondReceipt("Permission denied.", rMsg.getEnvelope(), command, newMap(
                    "template", "Not allowed to expel owner of group: ${ID}",
                    "replacements", newMap(
                            "ID", group.toString()
                    )
            ));
        }
        // 2.2. check admins
        boolean expelAdmin = false;
        for (ID item : admins) {
            if (expelList.contains(item)) {
                expelAdmin = true;
                break;
            }
        }
        if (expelAdmin) {
            return respondReceipt("Permission denied.", rMsg.getEnvelope(), command, newMap(
                    "template", "Not allowed to expel administrator of group: ${ID}",
                    "replacements", newMap(
                            "ID", group.toString()
                    )
            ));
        }

        // 3. do expel
        Pair<List<ID>, List<ID>> memPair = calculateExpelled(members, expelList);
        List<ID> newMembers = memPair.first;
        List<ID> removeList = memPair.second;
        if (removeList.size() > 0 && saveMembers(newMembers, group)) {
            command.put("removed", ID.revert(removeList));
        }

        // no need to response this group command
        return null;
    }

    protected Pair<List<ID>, List<ID>> calculateExpelled(List<ID> members, List<ID> expelList) {
        List<ID> newMembers = new ArrayList<>();
        List<ID> removeList = new ArrayList<>();
        for (ID item : members) {
            if (expelList.contains(item)) {
                removeList.add(item);
            } else {
                newMembers.add(item);
            }
        }
        return new Pair<>(newMembers, removeList);
    }
}
