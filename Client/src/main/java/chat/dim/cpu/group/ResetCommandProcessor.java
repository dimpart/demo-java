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
    public List<Content> process(Content content, ReliableMessage rMsg) {
        assert content instanceof ResetCommand : "reset command error: " + content;
        ResetCommand command = (ResetCommand) content;

        // 0. check command
        if (isCommandExpired(command)) {
            // ignore expired command
            return null;
        }
        ID group = command.getGroup();
        List<ID> newMembers = getMembers(command);
        if (newMembers.size() == 0) {
            return respondReceipt("Command error.", rMsg, group, newMap(
                    "template", "New member list is empty: ${ID}",
                    "replacements", newMap(
                            "ID", group.toString()
                    )
            ));
        }

        // 1. check group
        ID owner = getOwner(group);
        List<ID> members = getMembers(group);
        if (owner == null/* || members == null || members.size() == 0*/) {
            // TODO: query group bulletin document?
            return respondReceipt("Group empty.", rMsg, group, newMap(
                    "template", "Group empty: ${ID}",
                    "replacements", newMap(
                            "ID", group.toString()
                    )
            ));
        }

        // 2. check permission
        ID sender = rMsg.getSender();
        List<ID> admins = getAdministrators(group);
        boolean isAdmin = owner.equals(sender) || (admins != null && admins.contains(sender));
        if (!isAdmin) {
            return respondReceipt("Permission denied.", rMsg, group, newMap(
                    "template", "Not allowed to reset members of group: ${ID}",
                    "replacements", newMap(
                            "ID", group.toString()
                    )
            ));
        }
        // 2.1. check owner
        if (!newMembers.get(0).equals(owner)) {
            return respondReceipt("Permission denied.", rMsg, group, newMap(
                    "template", "Owner must be the first member of group: ${ID}",
                    "replacements", newMap(
                            "ID", group.toString()
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
            return respondReceipt("Permission denied.", rMsg, group, newMap(
                    "template", "Not allowed to expel administrator of group: ${ID}",
                    "replacements", newMap(
                            "ID", group.toString()
                    )
            ));
        }

        // 3. try to save 'reset' command
        if (updateResetCommandMessage(group, command, rMsg)) {
            Log.info("updated 'reset' command for group: " + group);
        } else {
            // newer 'reset' command exists, drop this command
            return null;
        }

        // 4. do reset
        Pair<List<ID>, List<ID>> pair = resetMembers(group, members, newMembers);
        List<ID> addList = pair.first;
        List<ID> removeList = pair.second;
        if (addList.size() > 0) {
            command.put("added", ID.revert(addList));
        }
        if (removeList.size() > 0) {
            command.put("removed", ID.revert(removeList));
        }

        // no need to response this group command
        return null;
    }

    private Pair<List<ID>, List<ID>> resetMembers(ID group, List<ID> oldMembers, List<ID> newMembers) {
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
        if (addList.size() > 0 || removeList.size() > 0) {
            if (!saveMembers(newMembers, group)) {
                assert false : "failed to save members in group: " + group;
                addList.clear();
                removeList.clear();
            }
        }
        return new Pair<>(addList, removeList);
    }
}
