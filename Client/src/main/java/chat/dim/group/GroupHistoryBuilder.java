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
package chat.dim.group;

import java.util.ArrayList;
import java.util.List;

import chat.dim.CommonMessenger;
import chat.dim.log.Log;
import chat.dim.mkm.DocumentUtils;
import chat.dim.mkm.User;
import chat.dim.protocol.Bulletin;
import chat.dim.protocol.Command;
import chat.dim.protocol.Content;
import chat.dim.protocol.Document;
import chat.dim.protocol.DocumentCommand;
import chat.dim.protocol.Envelope;
import chat.dim.protocol.GroupCommand;
import chat.dim.protocol.ID;
import chat.dim.protocol.InstantMessage;
import chat.dim.protocol.Meta;
import chat.dim.protocol.ReliableMessage;
import chat.dim.protocol.SecureMessage;
import chat.dim.protocol.group.ResetCommand;
import chat.dim.protocol.group.ResignCommand;
import chat.dim.type.Pair;

public class GroupHistoryBuilder extends TripletsHelper {

    protected final GroupCommandHelper helper;

    public GroupHistoryBuilder(GroupDelegate dataSource) {
        super(dataSource);
        helper = createHelper();
    }

    // override for customized helper
    protected GroupCommandHelper createHelper() {
        return new GroupCommandHelper(delegate);
    }

    /**
     *  Build command list for group history
     *      0. document command
     *      1. reset group command
     *      2. other group commands
     *
     * @param group - group ID
     * @return command list
     */
    public List<ReliableMessage> buildGroupHistories(ID group) {
        List<ReliableMessage> messages = new ArrayList<>();

        Document doc;
        ResetCommand reset;
        ReliableMessage rMsg;

        //
        //  0. build 'document' command
        //
        Pair<Document, ReliableMessage> docPair = buildDocumentCommand(group);
        doc = docPair.first;
        rMsg = docPair.second;
        if (doc == null || rMsg == null) {
            Log.warning("failed to build 'document' command for group: " + group);
            return messages;
        } else {
            messages.add(rMsg);
        }

        //
        //  1. append 'reset' command
        //
        Pair<ResetCommand, ReliableMessage> resPair = helper.getResetCommandMessage(group);
        reset = resPair.first;
        rMsg = resPair.second;
        if (reset == null || rMsg == null) {
            Log.warning("failed to get 'reset' command for group: " + group);
            return messages;
        } else {
            messages.add(rMsg);
        }

        //
        //  2. append other group commands
        //
        List<Pair<GroupCommand, ReliableMessage>> history = helper.getGroupHistories(group);
        for (Pair<GroupCommand, ReliableMessage> item : history) {
            if (item.first instanceof ResetCommand) {
                // 'reset' command already add to the front
                // assert messages.size() == 2 : "group history error: " + group + ", " + history.size();
                Log.info("skip 'reset' command for group: " + group);
                continue;
            } else if (item.first instanceof ResignCommand) {
                // 'resign' command, comparing it with document time
                if (DocumentUtils.isBefore(doc.getTime(), item.first.getTime())) {
                    Log.warning("expired '" + item.first.getCmd() + "' command in group: "
                            + group + ", sender: " + item.second.getSender());
                    continue;
                }
            } else {
                // 'invite', 'join', 'quit', comparing with 'reset' time
                if (DocumentUtils.isBefore(reset.getTime(), item.first.getTime())) {
                    Log.warning("expired '" + item.first.getCmd() + "' command in group: "
                            + group + ", sender: " + item.second.getSender());
                    continue;
                }
            }
            messages.add(item.second);
        }

        // OK
        return messages;
    }

    /**
     *  Create broadcast 'document' command
     */
    public Pair<Document, ReliableMessage> buildDocumentCommand(ID group) {
        User user = getFacebook().getCurrentUser();
        Bulletin doc = delegate.getBulletin(group);
        if (user == null || doc == null) {
            assert user != null : "failed to get current user";
            Log.error("document not found for group: " + group);
            return null;
        }
        ID me = user.getIdentifier();
        Meta meta = delegate.getMeta(group);
        Command command = DocumentCommand.response(group, meta, doc);
        ReliableMessage rMsg = packBroadcastMessage(me, command);
        return new Pair<>(doc, rMsg);
    }

    /**
     *  Create broadcast 'reset' group command with newest member list
     */
    public Pair<ResetCommand, ReliableMessage> buildResetCommand(ID group, List<ID> members) {
        User user = getFacebook().getCurrentUser();
        ID owner = delegate.getOwner(group);
        if (user == null || owner == null) {
            assert user != null : "failed to get current user";
            Log.error("owner not found for group: " + group);
            return null;
        }
        ID me = user.getIdentifier();
        if (!owner.equals(me)) {
            List<ID> admins = delegate.getAdministrators(group);
            if (admins == null || !admins.contains(me)) {
                Log.warning("not permit to build 'reset' command for group: " + group + ", " + me);
                return null;
            }
        }

        // check members
        if (members == null) {
            members = delegate.getMembers(group);
            if (members == null) {
                Log.error("failed to get members for group: " + group);
                return null;
            }
        }
        if (members.isEmpty()) {
            assert false : "group members not found: " + group;
            return null;
        }

        ResetCommand command = GroupCommand.reset(group, members);
        ReliableMessage rMsg = packBroadcastMessage(me, command);
        return new Pair<>(command, rMsg);
    }

    private ReliableMessage packBroadcastMessage(ID sender, Content content) {
        Envelope envelope = Envelope.create(sender, ID.ANYONE, null);
        InstantMessage iMsg = InstantMessage.create(envelope, content);
        CommonMessenger messenger = getMessenger();
        SecureMessage sMsg = messenger.encryptMessage(iMsg);
        if (sMsg == null) {
            assert false : "failed to encrypt message: " + envelope;
            return null;
        }
        ReliableMessage rMsg = messenger.signMessage(sMsg);
        assert rMsg != null : "failed to sign message: " + envelope;
        return rMsg;
    }

}
