/* license: https://mit-license.org
 *
 *  DIMP : Decentralized Instant Messaging Protocol
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
package chat.dim;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import chat.dim.crypto.EncryptKey;
import chat.dim.mkm.Group;
import chat.dim.protocol.ID;
import chat.dim.protocol.InstantMessage;
import chat.dim.protocol.Meta;
import chat.dim.protocol.ReliableMessage;
import chat.dim.protocol.SecureMessage;
import chat.dim.protocol.Visa;
import chat.dim.utils.Log;

public abstract class CommonPacker extends MessagePacker {

    public CommonPacker(Facebook facebook, Messenger messenger) {
        super(facebook, messenger);
    }

    /**
     *  Add income message in a queue for waiting sender's visa
     *
     * @param rMsg - incoming message
     * @param info - error info
     */
    protected abstract void suspendMessage(ReliableMessage rMsg, Map<String, ?> info);

    /**
     *  Add outgo message in a queue for waiting receiver's visa
     *
     * @param iMsg - outgo message
     * @param info - error info
     */
    protected abstract void suspendMessage(InstantMessage iMsg, Map<String, ?> info);

    // for checking whether user's ready
    protected EncryptKey getVisaKey(ID user) {
        EncryptKey visaKey = getFacebook().getPublicKeyForEncryption(user);
        if (visaKey != null) {
            // user is ready
            return visaKey;
        }
        // user not ready, try to query document for it
        CommonMessenger messenger = (CommonMessenger) getMessenger();
        if (messenger.queryDocument(user)) {
            Log.info("querying document for user: " + user);
        }
        return null;
    }

    // for checking whether group's ready
    protected List<ID> getMembers(ID group) {
        Facebook facebook = getFacebook();
        CommonMessenger messenger = (CommonMessenger) getMessenger();
        Meta meta = facebook.getMeta(group);
        if (meta == null/* || meta.getKey() == null*/) {
            // group not ready, try to query meta for it
            if (messenger.queryMeta(group)) {
                Log.info("querying meta for group: " + group);
            }
            return null;
        }
        Group grp = facebook.getGroup(group);
        List<ID> members = grp.getMembers();
        if (members == null || members.size() == 0) {
            // group not ready, try to query members for it
            if (messenger.queryMembers(group)) {
                Log.info("querying members for group: " + group);
            }
            return null;
        }
        // group is ready
        return members;
    }

    /**
     *  Check sender before verifying received message
     *
     * @param rMsg - network message
     * @return false on verify key not found
     */
    protected boolean checkSender(ReliableMessage rMsg) {
        ID sender = rMsg.getSender();
        assert sender.isUser() : "sender error: " + sender;
        // check sender's meta & document
        Visa visa = rMsg.getVisa();
        if (visa != null) {
            // first handshake?
            assert visa.getIdentifier().equals(sender) : "visa ID not match: " + sender;
            //assert Meta.matches(sender, rMsg.getMeta()) : "meta error: " + rMsg;
            return true;
        } else if (getVisaKey(sender) != null) {
            // sender is OK
            return true;
        }
        // sender not ready, suspend message for waiting document
        Map<String, String> error = new HashMap<>();
        error.put("message", "verify key not found");
        error.put("user", sender.toString());
        suspendMessage(rMsg, error);  // rMsg.put("error", error);
        return false;
    }

    protected boolean checkReceiver(ReliableMessage rMsg) {
        ID receiver = rMsg.getReceiver();
        if (receiver.isBroadcast()) {
            // broadcast message
            return true;
        } else if (receiver.isUser()) {
            // the facebook will select a user from local users to match this receiver,
            // if no user matched (private key not found), this message will be ignored.
            return true;
        }
        // check for received group message
        List<ID> members = getMembers(receiver);
        if (members != null) {
            return true;
        }
        // group not ready, suspend message for waiting members
        Map<String, String> error = new HashMap<>();
        error.put("message", "group not ready");
        error.put("group", receiver.toString());
        suspendMessage(rMsg, error);  // rMsg.put("error", error);
        return false;
    }

    /**
     *  Check receiver before encrypting message
     *
     * @param iMsg - plain message
     * @return false on encrypt key not found
     */
    protected boolean checkReceiver(InstantMessage iMsg) {
        ID receiver = iMsg.getReceiver();
        if (receiver.isBroadcast()) {
            // broadcast message
            return true;
        } else if (receiver.isGroup()) {
            // NOTICE: station will never send group message, so
            //         we don't need to check group info here; and
            //         if a client wants to send group message,
            //         that should be sent to a group bot first,
            //         and the bot will split it for all members.
            return false;
        } else if (getVisaKey(receiver) != null) {
            // receiver is OK
            return true;
        }
        // receiver not ready, suspend message for waiting document
        Map<String, String> error = new HashMap<>();
        error.put("message", "encrypt key not found");
        error.put("user", receiver.toString());
        suspendMessage(iMsg, error);  // iMsg.put("error", error);
        return false;
    }

    @Override
    public SecureMessage verifyMessage(ReliableMessage rMsg) {
        if (!checkSender(rMsg)) {
            // sender not ready
            Log.warning("sender not ready: " + rMsg.getSender());
            return null;
        }
        if (!checkReceiver(rMsg)) {
            // receiver (group) not ready
            Log.warning("receiver not ready: " + rMsg.getReceiver());
            return null;
        }
        return super.verifyMessage(rMsg);
    }

    @Override
    public SecureMessage encryptMessage(InstantMessage iMsg) {
        if (!checkReceiver(iMsg)) {
            // receiver not ready
            Log.warning("receiver not ready: " + iMsg.getReceiver());
            return null;
        }
        return super.encryptMessage(iMsg);
    }

}
