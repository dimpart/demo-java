/* license: https://mit-license.org
 *
 *  DIMP : Decentralized Instant Messaging Protocol
 *
 *                                Written in 2022 by Moky <albert.moky@gmail.com>
 *
 * ==============================================================================
 * The MIT License (MIT)
 *
 * Copyright (c) 2022 Albert Moky
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
package chat.dim;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import chat.dim.mkm.User;
import chat.dim.protocol.Content;
import chat.dim.protocol.DocumentCommand;
import chat.dim.protocol.ID;
import chat.dim.protocol.InstantMessage;
import chat.dim.protocol.ReliableMessage;
import chat.dim.protocol.SecureMessage;
import chat.dim.protocol.TextContent;
import chat.dim.protocol.Visa;
import chat.dim.utils.Log;
import chat.dim.utils.QueryFrequencyChecker;

public class ClientMessagePacker extends CommonPacker {

    public ClientMessagePacker(Facebook facebook, Messenger messenger) {
        super(facebook, messenger);
    }

    @Override
    protected ClientFacebook getFacebook() {
        Facebook facebook = super.getFacebook();
        assert facebook instanceof ClientFacebook : "facebook error: " + facebook;
        return (ClientFacebook) facebook;
    }

    @Override
    public InstantMessage decryptMessage(SecureMessage sMsg) {
        InstantMessage iMsg = super.decryptMessage(sMsg);
        if (iMsg == null) {
            // failed to decrypt message, visa.key changed?
            // 1. push new visa document to this message sender
            pushVisa(sMsg.getSender());
            // 2. build 'failed' message
            iMsg = getFailedMessage(sMsg);
        }
        return iMsg;
    }

    protected void pushVisa(ID contact) {
        QueryFrequencyChecker checker = QueryFrequencyChecker.getInstance();
        if (!checker.isDocumentResponseExpired(contact, 0, false)) {
            // response not expired yet
            Log.debug("visa push not expired yet: " + contact);
            return;
        }
        Log.info("push visa to: " + contact);
        User user = getFacebook().getCurrentUser();
        Visa visa = user.getVisa();
        if (visa == null || !visa.isValid()) {
            // FIXME: user visa not found?
            assert false : "user visa error: " + user;
            return;
        }
        Content command = DocumentCommand.response(user.getIdentifier(), visa);
        CommonMessenger messenger = (CommonMessenger) getMessenger();
        messenger.sendContent(user.getIdentifier(), contact, command, 1);
    }

    protected InstantMessage getFailedMessage(SecureMessage sMsg) {
        ID sender = sMsg.getSender();
        ID group = sMsg.getGroup();
        String name = getFacebook().getName(sender);
        // create text content
        Content content = TextContent.create("Failed to decrypt message from " + name);
        content.setGroup(group);
        // pack instant message
        Map<String, Object> info = sMsg.copyMap(false);
        info.remove("data");
        info.put("content", content.toMap());
        return InstantMessage.parse(info);
    }

    @Override
    protected boolean checkReceiver(InstantMessage iMsg) {
        ID receiver = iMsg.getReceiver();
        if (receiver.isBroadcast()) {
            // broadcast message
            return true;
        } else if (receiver.isUser()) {
            // check user's meta & document
            return super.checkReceiver(iMsg);
        }
        //
        //  check group's meta & members
        //
        List<ID> members = getMembers(receiver);
        if (members == null || members.isEmpty()) {
            // group not ready, suspend message for waiting meta/members
            Map<String, String> error = new HashMap<>();
            error.put("message", "group not ready");
            error.put("group", receiver.toString());
            suspendMessage(iMsg, error);  // iMsg.put("error", error);
            return false;
        }
        //
        //  check group members' visa key
        //
        List<ID> waiting = new ArrayList<>();
        for (ID item : members) {
            if (getVisaKey(item) == null) {
                // member not ready
                waiting.add(item);
            }
        }
        if (waiting.isEmpty()) {
            // all members' visa keys exist
            return true;
        }
        // members not ready, suspend message for waiting document
        Map<String, Object> error = new HashMap<>();
        error.put("message", "members not ready");
        error.put("group", receiver.toString());
        error.put("members", ID.revert(waiting));
        suspendMessage(iMsg, error);  // iMsg.put("error", error);
        return false;
    }

    @Override
    protected void suspendMessage(ReliableMessage rMsg, Map<String, ?> info) {
        // TODO:
    }

    @Override
    protected void suspendMessage(InstantMessage iMsg, Map<String, ?> info) {
        // TODO:
    }

}
