/* license: https://mit-license.org
 *
 *  DIM-SDK : Decentralized Instant Messaging Software Development Kit
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

import chat.dim.core.TwinsHelper;
import chat.dim.crypto.SymmetricKey;
import chat.dim.mkm.User;
import chat.dim.protocol.Content;
import chat.dim.protocol.ContentType;
import chat.dim.protocol.FileContent;
import chat.dim.protocol.ID;
import chat.dim.protocol.InstantMessage;
import chat.dim.protocol.ReliableMessage;
import chat.dim.protocol.SecureMessage;
import chat.dim.protocol.TextContent;
import chat.dim.protocol.Visa;
import chat.dim.utils.Log;

public abstract class ClientMessagePacker extends CommonMessagePacker {

    public ClientMessagePacker(ClientFacebook facebook, ClientMessenger messenger) {
        super(facebook, messenger);
    }

    @Override
    protected ClientFacebook getFacebook() {
        return (ClientFacebook) super.getFacebook();
    }

    @Override
    protected ClientMessenger getMessenger() {
        return (ClientMessenger) super.getMessenger();
    }

    // for checking whether group's ready
    protected List<ID> getMembers(ID group) {
        Facebook facebook = getFacebook();
        if (facebook == null) {
            assert false : "failed to get facebook";
            return null;
        }
        return facebook.getMembers(group);
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
        // perhaps some members have already disappeared,
        // although the packer will query document when the member's visa key is not found,
        // but the station will never respond with the right document,
        // so we must return true here to let the messaging continue;
        // when the member's visa is responded, we should send the suspended message again.
        return waiting.size() < members.size();
    }

    protected boolean checkGroup(ReliableMessage sMsg) {
        ID receiver = sMsg.getReceiver();
        // check group
        ID group = ID.parse(sMsg.get("group"));
        if (group == null && receiver.isGroup()) {
            /// Transform:
            ///     (B) => (J)
            ///     (D) => (G)
            group = receiver;
        }
        if (group == null || group.isBroadcast()) {
            /// A, C - personal message (or hidden group message)
            //      the packer will call the facebook to select a user from local
            //      for this receiver, if no user matched (private key not found),
            //      this message will be ignored;
            /// E, F, G - broadcast group message
            //      broadcast message is not encrypted, so it can be read by anyone.
            return true;
        }
        /// H, J, K - group message
        //      check for received group message
        List<ID> members = getMembers(group);
        if (members != null && members.size() > 0) {
            // group is ready
            return true;
        }
        // group not ready, suspend message for waiting members
        Map<String, String> error = new HashMap<>();
        error.put("message", "group not ready");
        error.put("group", group.toString());
        suspendMessage(sMsg, error);  // rMsg.put("error", error);
        return false;
    }

    @Override
    public SecureMessage verifyMessage(ReliableMessage rMsg) {
        // check receiver/group with local user
        if (!checkGroup(rMsg)) {
            // receiver (group) not ready
            Log.warning("receiver not ready: " + rMsg.getReceiver());
            return null;
        }
        return super.verifyMessage(rMsg);
    }

    @Override
    public ReliableMessage deserializeMessage(byte[] data) {
        ReliableMessage msg = super.deserializeMessage(data);
        if (msg != null && checkDuplicated(msg)) {
            msg = null;
        }
        return msg;
    }

    protected boolean checkDuplicated(ReliableMessage rMsg) {
        Checkpoint cp = Checkpoint.getInstance();
        boolean duplicated = cp.checkDuplicatedMessage(rMsg);
        if (duplicated) {
            String sig = cp.getSig(rMsg);
            Log.warning("drop duplicated message (" + sig + "):"
                    + rMsg.getSender() + " -> " + rMsg.getReceiver());
        }
        return duplicated;
    }

    @Override
    public InstantMessage decryptMessage(SecureMessage sMsg) {
        InstantMessage iMsg;
        try {
            iMsg = super.decryptMessage(sMsg);
        } catch (Exception e) {
            String errMsg = e.toString();
            if (errMsg.contains("failed to decrypt message key")) {
                // Exception from 'SecureMessagePacker::decrypt(sMsg, receiver)'
                Log.warning("decrypt message error: " + e);
                // visa.key changed?
                // push my newest visa to the sender
                iMsg = null;
            } else if (errMsg.contains("receiver error")) {
                // Exception from 'MessagePacker::decryptMessage(sMsg)'
                Log.error("decrypt message error: " + e);
                // not for you?
                // just ignore it
                return null;
            } else {
                throw e;
            }
        }
        if (iMsg == null) {
            // failed to decrypt message, visa.key changed?
            // 1. push new visa document to this message sender
            boolean ok = pushVisa(sMsg.getSender());
            assert ok : "failed to push visa: " + sMsg.getSender();
            // 2. build 'failed' message
            iMsg = getFailedMessage(sMsg);
        } else {
            Content content = iMsg.getContent();
            if (content instanceof FileContent) {
                FileContent file = (FileContent) content;
                if (file.getPassword() == null && file.getURL() != null) {
                    // now received file content with remote data,
                    // which must be encrypted before upload to CDN;
                    // so keep the password here for decrypting after downloaded.
                    Messenger messenger = getMessenger();
                    SymmetricKey key = messenger.getDecryptKey(sMsg);
                    assert key != null : "failed to get msg key: " + sMsg.getSender()
                            + " => " + sMsg.getReceiver() + ", " + sMsg.get("group");
                    // keep password to decrypt data after downloaded
                    file.setPassword(key);
                }
            }
        }
        return iMsg;
    }

    protected boolean pushVisa(ID contact) {
        // visa.key not updated?
        ClientFacebook facebook = getFacebook();
        if (facebook == null) {
            assert false : "failed to get facebook";
            return false;
        }
        User user = facebook.getCurrentUser();
        if (user == null) {
            assert false : "failed to get current user";
            return false;
        }
        Visa visa = user.getVisa();
        if (visa == null || !visa.isValid()) {
            // FIXME: user visa not found?
            throw new NullPointerException("user visa error: " + user);
        }
        EntityChecker checker = facebook.getEntityChecker();
        if (checker == null) {
            assert false : "failed to get entity checker";
            return false;
        }
        return checker.sendVisa(visa, contact, false);
    }

    protected InstantMessage getFailedMessage(SecureMessage sMsg) {
        ID sender = sMsg.getSender();
        ID group = sMsg.getGroup();
        int type = sMsg.getType();
        if (ContentType.COMMAND.equals(type) || ContentType.HISTORY.equals(type)) {
            Log.warning("ignore message unable to decrypt: " + type + ", from " + sender);
            return null;
        }
        // create text content
        Content content = TextContent.create("Failed to decrypt message.");
        content.putAll(TwinsHelper.newMap(
                "template", "Failed to decrypt message (type=${type}) from \"${sender}\".",
                "replacements", TwinsHelper.newMap(
                        "type", type,
                        "sender", sender.toString(),
                        "group", group == null ? null : group.toString()
                )
        ));
        if (group != null) {
            content.setGroup(group);
        }
        // pack instant message
        Map<String, Object> info = sMsg.copyMap(false);
        info.remove("data");
        info.put("content", content.toMap());
        return InstantMessage.parse(info);
    }

}
