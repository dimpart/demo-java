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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import chat.dim.Messenger;
import chat.dim.protocol.Content;
import chat.dim.protocol.Envelope;
import chat.dim.protocol.ID;
import chat.dim.protocol.InstantMessage;
import chat.dim.protocol.ReliableMessage;
import chat.dim.protocol.SecureMessage;
import chat.dim.utils.Log;

public class GroupPacker extends TripletsHelper {

    public GroupPacker(GroupDelegate dataSource) {
        super(dataSource);
    }

    /**
     *  Pack as broadcast message
     */
    public ReliableMessage packMessage(Content content, ID sender) {
        Envelope envelope = Envelope.create(sender, ID.ANYONE, null);
        InstantMessage iMsg = InstantMessage.create(envelope, content);
        iMsg.setString("group", content.getGroup());  // expose group ID
        return encryptAndSignMessage(iMsg);
    }

    public ReliableMessage encryptAndSignMessage(InstantMessage iMsg) {
        Messenger messenger = getMessenger();
        // encrypt for receiver
        SecureMessage sMsg = messenger.encryptMessage(iMsg);
        if (sMsg == null) {
            assert false : "failed to encrypt message: " + iMsg.getSender() + " => " + iMsg.getReceiver() + ", " + iMsg.getGroup();
            return null;
        }
        // sign for sender
        ReliableMessage rMsg = messenger.signMessage(sMsg);
        if (rMsg == null) {
            assert false : "failed to sign message: " + iMsg.getSender() + " => " + iMsg.getReceiver() + ", " + iMsg.getGroup();
            return null;
        }
        // OK
        return rMsg;
    }

    public List<InstantMessage> splitMessage(InstantMessage iMsg, List<ID> allMembers) {
        List<InstantMessage> messages = new ArrayList<>();
        ID sender = iMsg.getSender();

        Map<String, Object> info;
        InstantMessage item;
        for (ID receiver : allMembers) {
            if (sender.equals(receiver)) {
                Log.info("skip cycled message: " + receiver + ", " + iMsg.getGroup());
                continue;
            } else {
                Log.info("split group message for member: " + receiver);
            }
            info = iMsg.copyMap(false);
            // Copy the content to avoid conflicts caused by modifications
            // by different processes.
            // Notice: there is no need to use deep copying here.
            info.put("content", iMsg.getContent().copyMap(false));
            // replace 'receiver' with member ID
            info.put("receiver", receiver.toString());
            item = InstantMessage.parse(info);
            if (item == null) {
                assert false : "failed to repack message: " + receiver;
                continue;
            }
            messages.add(item);
        }

        return messages;
    }

    public List<ReliableMessage> splitMessage(ReliableMessage rMsg, List<ID> allMembers) {
        List<ReliableMessage> messages = new ArrayList<>();
        ID sender = rMsg.getSender();

        assert !rMsg.containsKey("key") : "should not happen";
        Map<String, Object> keys = rMsg.getEncryptedKeys();
        if (keys == null) {
            keys = new HashMap<>();
            // TODO: get key digest
        }
        Object keyData;  // Base-64

        Map<String, Object> info;
        ReliableMessage item;
        for (ID receiver : allMembers) {
            if (sender.equals(receiver)) {
                Log.info("skip cycled message: " + receiver + ", " + rMsg.getGroup());
                continue;
            } else {
                Log.info("split group message for member: " + receiver);
            }
            info = rMsg.copyMap(false);
            // replace 'receiver' with member ID
            info.put("receiver", receiver.toString());
            // fetch encrypted key data
            info.remove("keys");
            keyData = keys.get(receiver.toString());
            if (keyData != null) {
                info.put("key", keyData);
            }
            item = ReliableMessage.parse(info);
            if (item == null) {
                assert false : "failed to repack message: " + info;
                continue;
            }
            messages.add(item);
        }

        return messages;
    }

}
