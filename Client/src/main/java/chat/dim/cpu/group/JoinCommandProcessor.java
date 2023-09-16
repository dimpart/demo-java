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

import java.util.List;

import chat.dim.Facebook;
import chat.dim.Messenger;
import chat.dim.cpu.GroupCommandProcessor;
import chat.dim.protocol.Content;
import chat.dim.protocol.GroupCommand;
import chat.dim.protocol.ID;
import chat.dim.protocol.ReliableMessage;
import chat.dim.protocol.group.JoinCommand;

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

        // 2. check membership
        ID sender = rMsg.getSender();
        if (members.contains(sender)) {
            // maybe the sender is already a member,
            // but if it can still receive a 'join' command here,
            // we should respond the sender with the newest membership again.
            boolean ok = sendResetCommand(group, members, sender);
            assert ok : "failed to send 'reset' command for group: " + group + " => " + sender;
        } else {
            // add 'join' application for waiting review
            boolean ok = addApplication(command, rMsg);
            assert ok : "failed to add 'join' application for group: " + group;
        }

        // no need to response this group command
        return null;
    }

}
