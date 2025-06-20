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
import chat.dim.protocol.ID;
import chat.dim.protocol.ReliableMessage;
import chat.dim.protocol.group.ResetCommand;
import chat.dim.type.Pair;
import chat.dim.type.Triplet;
import chat.dim.utils.Log;

/**
 *  Reset Group Command Processor
 *  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
 *
 *      1. reset group members
 *      2. only group owner or assistant can reset group members
 *
 *      3. specially, if the group members info lost,
 *         means you may not known who's the group owner immediately (and he may be not online),
 *         so we accept the new members-list temporary, and find out who is the owner,
 *         after that, we will send 'query' to the owner to get the newest members-list.
 */
public class ResetCommandProcessor extends GroupCommandProcessor {

    public ResetCommandProcessor(Facebook facebook, Messenger messenger) {
        super(facebook, messenger);
    }

    @Override
    public List<Content> processContent(Content content, ReliableMessage rMsg) {
        assert content instanceof ResetCommand : "reset command error: " + content;
        ResetCommand command = (ResetCommand) content;

        // 0. check command
        Pair<ID, List<Content>> pair = checkCommandExpired(command, rMsg);
        ID group = pair.first;
        if (group == null) {
            // ignore expired command
            return pair.second;
        }
        Pair<List<ID>, List<Content>> pair1 = checkCommandMembers(command, rMsg);
        List<ID> newMembers = pair1.first;
        if (newMembers == null || newMembers.isEmpty()) {
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
        boolean canReset = isOwner || isAdmin;
        if (!canReset) {
            return respondReceipt("Permission denied.", rMsg.getEnvelope(), command, newMap(
                    "template", "Not allowed to reset members of group: ${gid}",
                    "replacements", newMap(
                            "gid", group.toString()
                    )
            ));
        }
        // 2.1. check owner
        if (!newMembers.get(0).equals(owner)) {
            return respondReceipt("Permission denied.", rMsg.getEnvelope(), command, newMap(
                    "template", "Owner must be the first member of group: ${gid}",
                    "replacements", newMap(
                            "gid", group.toString()
                    )
            ));
        }
        // 2.2. check admins
        boolean expelAdmin = false;
        for (ID item : admins) {
            if (!newMembers.contains(item)) {
                expelAdmin = true;
                break;
            }
        }
        if (expelAdmin) {
            return respondReceipt("Permission denied.", rMsg.getEnvelope(), command, newMap(
                    "template", "Not allowed to expel administrator of group: ${gid}",
                    "replacements", newMap(
                            "gid", group.toString()
                    )
            ));
        }

        // 3. do reset
        Pair<List<ID>, List<ID>> memPair = calculateReset(members, newMembers);
        List<ID> addList = memPair.first;
        List<ID> removeList = memPair.second;
        if (!saveGroupHistory(group, command, rMsg)) {
            // here try to save the 'reset' command to local storage as group history
            // it should not failed unless the command is expired
            Log.error("failed to save 'reset' command for group: " + group);
        } else if (addList.isEmpty() && removeList.isEmpty()) {
            Log.warning("nothing changed");
        } else if (saveMembers(newMembers, group)) {
            Log.info("new members saved in group: " + group);
            if (addList.size() > 0) {
                command.put("added", ID.revert(addList));
            }
            if (removeList.size() > 0) {
                command.put("removed", ID.revert(removeList));
            }
        } else {
            // DB error?
            assert false : "failed to save members in group: " + group;
        }

        // no need to response this group command
        return null;
    }

    protected Pair<List<ID>, List<ID>> calculateReset(List<ID> oldMembers, List<ID> newMembers) {
        List<ID> addList = new ArrayList<>();
        List<ID> removeList = new ArrayList<>();
        // build invited-list
        for (ID item : newMembers) {
            if (oldMembers.contains(item)) {
                continue;
            }
            addList.add(item);
        }
        // build expelled-list
        for (ID item : oldMembers) {
            if (newMembers.contains(item)) {
                continue;
            }
            removeList.add(item);
        }
        return new Pair<>(addList, removeList);
    }

}
