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
package chat.dim.cpu;

import java.util.List;

import chat.dim.Facebook;
import chat.dim.Messenger;
import chat.dim.group.GroupCommandHelper;
import chat.dim.log.Log;
import chat.dim.protocol.Content;
import chat.dim.protocol.ForwardContent;
import chat.dim.protocol.ID;
import chat.dim.protocol.InstantMessage;
import chat.dim.protocol.ReliableMessage;
import chat.dim.protocol.group.GroupCommand;
import chat.dim.type.Pair;
import chat.dim.type.Triplet;

public class GroupCommandProcessor extends HistoryCommandProcessor {

    public GroupCommandProcessor(Facebook facebook, Messenger messenger) {
        super(facebook, messenger);
    }

    protected ID getOwner(ID group) {
        return delegate.getOwner(group);
    }
    protected List<ID> getAssistants(ID group) {
        return delegate.getAssistants(group);
    }

    protected List<ID> getAdministrators(ID group) {
        return delegate.getAdministrators(group);
    }
    protected boolean saveAdministrators(List<ID> members, ID group) {
        return delegate.saveAdministrators(members, group);
    }

    protected List<ID> getMembers(ID group) {
        return delegate.getMembers(group);
    }
    protected boolean saveMembers(List<ID> members, ID group) {
        return delegate.saveMembers(members, group);
    }

    protected boolean saveGroupHistory(ID group, GroupCommand content, ReliableMessage rMsg) {
        return helper.saveGroupHistory(content, rMsg, group);
    }

    @Override
    public List<Content> processContent(Content content, ReliableMessage rMsg) {
        assert content instanceof GroupCommand : "group command error: " + content;
        GroupCommand command = (GroupCommand) content;
        return respondReceipt("Command not support.", rMsg.getEnvelope(), command, newMap(
                "template", "Group command (name: ${cmd}) not support yet!",
                "replacements", newMap(
                        "cmd", command.getCmd()
                )
        ));
    }

    protected Pair<ID, List<Content>> checkCommandExpired(GroupCommand content, ReliableMessage rMsg) {
        ID group = content.getGroup();
        if (group == null) {
            assert false : "group command error: " + content;
            return null;
        }
        List<Content> errors;
        boolean expired = helper.isCommandExpired(content);
        if (expired) {
            errors = respondReceipt("Command expired.", rMsg.getEnvelope(), content, newMap(
                    "template", "Group command expired: ${cmd}, group: ${gid}.",
                    "replacements", newMap(
                            "cmd", content.getCmd(),
                            "gid", group.toString()
                    )
            ));
            group = null;
        } else {
            // group ID must not empty here
            errors = null;
        }
        return new Pair<>(group, errors);
    }

    protected Pair<List<ID>, List<Content>> checkCommandMembers(GroupCommand content, ReliableMessage rMsg) {
        ID group = content.getGroup();
        if (group == null) {
            assert false : "group command error: " + content;
            return null;
        }
        List<Content> errors;
        List<ID> members = GroupCommandHelper.getCommandMembers(content);
        if (/*members == null || */members.isEmpty()) {
            errors = respondReceipt("Command error.", rMsg.getEnvelope(), content, newMap(
                    "template", "Group members empty: ${gid}",
                    "replacements", newMap(
                            "gid", group.toString()
                    )
            ));
        } else {
            // normally
            errors = null;
        }
        return new Pair<>(members, errors);
    }

    protected Triplet<ID, List<ID>, List<Content>> checkGroupMembers(GroupCommand content, ReliableMessage rMsg) {
        ID group = content.getGroup();
        if (group == null) {
            assert false : "group command error: " + content;
            return null;
        }
        List<Content> errors;
        ID owner = getOwner(group);
        List<ID> members = getMembers(group);
        if (owner == null || members == null || members.isEmpty()) {
            // TODO: query group members?
            errors = respondReceipt("Group empty.", rMsg.getEnvelope(), content, newMap(
                    "template", "Group empty: ${gid}",
                    "replacements", newMap(
                            "gid", group.toString()
                    )
            ));
        } else {
            // group is ready
            errors = null;
        }
        return new Triplet<>(owner, members, errors);
    }

    /**
     *  Send a command list with newest members to the receiver
     */
    protected boolean sendGroupHistories(ID group, ID receiver) {
        List<ReliableMessage> messages = builder.buildGroupHistories(group);
        if (messages == null || messages.isEmpty()) {
            Log.warning("failed to build history for group: " + group);
            return false;
        }
        Content content = ForwardContent.create(messages);
        Pair<InstantMessage, ReliableMessage> result;
        result = getMessenger().sendContent(content, null, receiver, 1);
        return result.second != null;
    }

}
