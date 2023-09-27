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

import java.util.List;

import chat.dim.Facebook;
import chat.dim.Messenger;
import chat.dim.cpu.GroupCommandProcessor;
import chat.dim.protocol.Content;
import chat.dim.protocol.GroupCommand;
import chat.dim.protocol.ID;
import chat.dim.protocol.ReliableMessage;
import chat.dim.protocol.group.QueryCommand;
import chat.dim.type.Pair;
import chat.dim.type.Triplet;

/**
 *  Query Group Command Processor
 *  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
 *
 *      1. query for group members-list
 *      2. any existed member or assistant can query group members-list
 */
public class QueryCommandProcessor extends GroupCommandProcessor {

    public QueryCommandProcessor(Facebook facebook, Messenger messenger) {
        super(facebook, messenger);
    }

    @Override
    public List<Content> process(Content content, ReliableMessage rMsg) {
        assert content instanceof QueryCommand : "query command error: " + content;
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
        List<ID> bots = getAssistants(group);
        boolean isMember = members.contains(sender);
        boolean isBot = bots != null && bots.contains(sender);

        // 2. check permission
        boolean canQuery = isMember || isBot;
        if (!canQuery) {
            return respondReceipt("Permission denied.", rMsg.getEnvelope(), command, newMap(
                    "template", "Not allowed to query members of group: ${ID}",
                    "replacements", newMap(
                            "ID", group.toString()
                    )
            ));
        }

        // 3. send the reset command with newest members
        boolean ok = sendResetCommand(group, members, sender);
        assert ok : "failed to send 'reset' command for group: " + group + " => " + sender;

        // no need to response this group command
        return null;
    }

}
