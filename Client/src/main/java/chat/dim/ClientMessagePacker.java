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

import java.util.HashMap;
import java.util.Map;

import chat.dim.crypto.SymmetricKey;
import chat.dim.digest.SHA256;
import chat.dim.format.Base64;
import chat.dim.mkm.User;
import chat.dim.protocol.DocumentCommand;
import chat.dim.protocol.ID;
import chat.dim.protocol.InstantMessage;
import chat.dim.protocol.ReliableMessage;
import chat.dim.protocol.SecureMessage;
import chat.dim.protocol.Visa;
import chat.dim.utils.Log;

public class ClientMessagePacker extends MessagePacker {

    public ClientMessagePacker(Facebook facebook, Messenger messenger) {
        super(facebook, messenger);
    }

    @Override
    protected CommonFacebook getFacebook() {
        Facebook facebook = super.getFacebook();
        assert facebook instanceof CommonFacebook : "facebook error: " + facebook;
        return (CommonFacebook) facebook;
    }

    protected static void attachKeyDigest(ReliableMessage rMsg, Messenger messenger) {
        // check message delegate
        if (rMsg.getDelegate() == null) {
            rMsg.setDelegate(messenger);
        }
        // check msg.key
        if (rMsg.get("key") != null) {
            // getEncryptedKey() != null
            return;
        }
        // check msg.keys
        Map<String, Object> keys = rMsg.getEncryptedKeys();
        if (keys == null) {
            keys = new HashMap<>();
        } else if (keys.containsKey("digest")) {
            // key digest already exists
            return;
        }
        // get key with direction
        SymmetricKey key;
        ID sender = rMsg.getSender();
        ID group = rMsg.getGroup();
        if (group == null) {
            ID receiver = rMsg.getReceiver();
            key = messenger.getCipherKey(sender, receiver, false);
        } else {
            key = messenger.getCipherKey(sender, group, false);
        }
        if (key == null) {
            // broadcast message has no key
            return;
        }
        String digest = getKeyDigest(key);
        if (digest == null) {
            // key error
            return;
        }
        keys.put("digest", digest);
        rMsg.put("keys", keys);
    }

    // get partially key data for digest
    private static String getKeyDigest(SymmetricKey key) {
        byte[] data = key.getData();
        if (data == null || data.length < 6) {
            return null;
        }
        // get digest for the last 6 bytes of key.data
        byte[] part = new byte[6];
        System.arraycopy(data, data.length-6, part, 0, 6);
        byte[] digest = SHA256.digest(part);
        String base64 = Base64.encode(digest);
        base64 = base64.trim();
        int pos = base64.length() - 8;
        return base64.substring(pos);
    }

    @Override
    public byte[] serializeMessage(ReliableMessage rMsg) {
        attachKeyDigest(rMsg, getMessenger());
        return super.serializeMessage(rMsg);
    }

    @Override
    public ReliableMessage deserializeMessage(byte[] data) {
        if (data == null || data.length < 2) {
            // message data error
            return null;
        }
        return super.deserializeMessage(data);
    }

    @Override
    public ReliableMessage signMessage(SecureMessage sMsg) {
        if (sMsg instanceof ReliableMessage) {
            // already signed
            return (ReliableMessage) sMsg;
        }
        return super.signMessage(sMsg);
    }

    /*/
    @Override
    public SecureMessage encryptMessage(InstantMessage iMsg) {
        // make sure visa.key exists before encrypting message
        SecureMessage sMsg = super.encryptMessage(iMsg);
        ID receiver = iMsg.getReceiver();
        if (receiver.isGroup()) {
            // reuse group message keys
            Messenger messenger = getMessenger();
            SymmetricKey key = messenger.getCipherKey(iMsg.getSender(), receiver, false);
            key.put("reused", true);
        }
        // TODO: reuse personal message key?
        return sMsg;
    }
    /*/

    @Override
    public InstantMessage decryptMessage(SecureMessage sMsg) {
        try {
            return super.decryptMessage(sMsg);
        } catch (NullPointerException e) {
            // check exception thrown by DKD: chat.dim.dkd.EncryptedMessage.decrypt()
            String errMsg = e.getMessage();
            if (errMsg != null && errMsg.contains("failed to decrypt key in msg")) {
                Log.error(errMsg);
                // visa.key not updated?
                User user = getFacebook().getCurrentUser();
                Visa visa = user.getVisa();
                if (visa == null || !visa.isValid()) {
                    // FIXME: user visa not found?
                    throw new NullPointerException("user visa error: " + user.getIdentifier());
                }
                DocumentCommand cmd = DocumentCommand.response(user.getIdentifier(), visa);
                CommonMessenger messenger = (CommonMessenger) getMessenger();
                messenger.sendContent(user.getIdentifier(), sMsg.getSender(), cmd, 0);
            } else {
                throw e;
            }
        }
        return null;
    }
}
