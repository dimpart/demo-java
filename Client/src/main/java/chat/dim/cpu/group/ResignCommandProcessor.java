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
import chat.dim.protocol.Content;
import chat.dim.protocol.ID;
import chat.dim.protocol.ReliableMessage;
import chat.dim.protocol.group.ResignCommand;
import chat.dim.type.Copier;
import chat.dim.type.Pair;
import chat.dim.type.Triplet;
import chat.dim.utils.Log;

/**
 *  Resign Group Admin Command Processor
 *  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
 *
 *      1. remove the sender from administrators of the group
 *      2. administrator can be hired/fired by owner only
 */
public class ResignCommandProcessor extends GroupCommandProcessor {

    public ResignCommandProcessor(Facebook facebook, Messenger messenger) {
        super(facebook, messenger);
    }

    @Override
    public List<Content> processContent(Content content, ReliableMessage rMsg) {
        assert content instanceof ResignCommand : "resign command error: " + content;
        ResignCommand command = (ResignCommand) content;

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

        // 2. check permission
        if (isOwner) {
            return respondReceipt("Permission denied.", rMsg.getEnvelope(), command, newMap(
                    "template", "Owner cannot resign from group: ${ID}",
                    "replacements", newMap(
                            "ID", group.toString()
                    )
            ));
        }

        // 3. do resign
        if (!isAdmin) {
            // the sender is not an administrator now,
            // shall we notify the sender that the administrators list was updated?
            Log.error("not an admin " + sender + " in group: " + group);
        } else if (!saveGroupHistory(group, command, rMsg)) {
            // here try to append the 'resign' command to local storage as group history
            // it should not failed unless the command is expired
            Log.error("failed to save 'resign' command for group: " + group);
        } else {
            // admin do exist, remove it and update database
            admins = Copier.copyList(admins);
            admins.remove(sender);
            if (saveAdministrators(admins, group)) {
                List<String> removeList = new ArrayList<>();
                removeList.add(sender.toString());
                command.put("removed", removeList);
            } else {
                // DB error?
                assert false : "failed to save administrators for group: " + group;
            }
        }

        // no need to response this group command
        return null;
    }

}
