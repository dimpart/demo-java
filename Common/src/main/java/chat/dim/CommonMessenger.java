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

import java.util.Arrays;
import java.util.Date;
import java.util.Map;

import chat.dim.compat.CompatibleIncoming;
import chat.dim.compat.CompatibleOutgoing;
import chat.dim.core.CipherKeyDelegate;
import chat.dim.core.Packer;
import chat.dim.core.Processor;
import chat.dim.crypto.SymmetricKey;
import chat.dim.format.JSON;
import chat.dim.format.UTF8;
import chat.dim.mkm.User;
import chat.dim.protocol.Command;
import chat.dim.protocol.Content;
import chat.dim.protocol.Envelope;
import chat.dim.protocol.ID;
import chat.dim.protocol.InstantMessage;
import chat.dim.protocol.ReliableMessage;
import chat.dim.protocol.SecureMessage;
import chat.dim.protocol.Visa;
import chat.dim.type.Converter;
import chat.dim.type.Pair;
import chat.dim.utils.Log;

/**
 *  Common Messenger with Session & Database
 */
public class CommonMessenger extends Messenger implements Transmitter {

    protected final Session session;
    protected final CommonFacebook facebook;
    protected final CipherKeyDelegate database;

    private Packer packer;
    private Processor processor;

    public CommonMessenger(Session session, CommonFacebook facebook, CipherKeyDelegate database) {
        super();
        this.session = session;
        this.facebook = facebook;
        this.database = database;
        this.packer = null;
        this.processor = null;
    }

    public Session getSession() {
        return session;
    }

    @Override
    public CommonFacebook getFacebook() {
        return facebook;
    }

    @Override
    protected CipherKeyDelegate getCipherKeyDelegate() {
        return database;
    }

    @Override
    protected Packer getPacker() {
        return packer;
    }
    public void setPacker(Packer packer) {
        this.packer = packer;
    }

    @Override
    protected Processor getProcessor() {
        return processor;
    }
    public void setProcessor(Processor processor) {
        this.processor = processor;
    }

    @Override
    public byte[] encryptKey(byte[] data, ID receiver, InstantMessage iMsg) {
        try {
            return super.encryptKey(data, receiver, iMsg);
        } catch (Exception e) {
            // FIXME:
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public byte[] serializeKey(SymmetricKey password, InstantMessage iMsg) {
        // TODO: reuse message key

        // 0. check message key
        Object reused = password.get("reused");
        Object digest = password.get("digest");
        if (reused == null && digest == null) {
            // flags not exist, serialize it directly
            return super.serializeKey(password, iMsg);
        }
        // 1. remove before serializing key
        password.remove("reused");
        password.remove("digest");
        // 2. serialize key without flags
        byte[] data = super.serializeKey(password, iMsg);
        // 3. put it back after serialized
        if (Converter.getBoolean(reused, false)) {
            password.put("reused", true);
        }
        if (digest != null) {
            password.put("digest", digest);
        }
        return data;
    }

    @Override
    public byte[] serializeContent(Content content, SymmetricKey password, InstantMessage iMsg) {
        CompatibleOutgoing.fixContent(content);
        return super.serializeContent(content, password, iMsg);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Content deserializeContent(byte[] data, SymmetricKey password, SecureMessage sMsg) {
        //Content content = super.deserializeContent(data, password, sMsg);
        String json = UTF8.decode(data);
        if (json == null) {
            assert false : "content data error: " + Arrays.toString(data);
            return null;
        }
        Object dict = JSON.decode(json);
        if (dict instanceof Map) {
            CompatibleIncoming.fixContent((Map<String, Object>) dict);
        }
        // TODO: translate short keys
        //       'T' -> 'type'
        //       'N' -> 'sn'
        //       'W' -> 'time'
        //       'G' -> 'group'
        return Content.parse(dict);
        // NOTICE: check attachment for File/Image/Audio/Video message content
        //         after deserialize content, this job should be do in subclass
    }

    //
    //  Interfaces for Transmitting Message
    //

    @Override
    public Pair<InstantMessage, ReliableMessage> sendContent(Content content, ID sender, ID receiver, int priority) {
        if (sender == null) {
            User current = facebook.getCurrentUser();
            assert current != null : "current user not set";
            sender = current.getIdentifier();
        }
        Envelope env = Envelope.create(sender, receiver, null);
        InstantMessage iMsg = InstantMessage.create(env, content);
        ReliableMessage rMsg = sendInstantMessage(iMsg, priority);
        return new Pair<>(iMsg, rMsg);
    }

    private boolean attachVisaTime(ID sender, InstantMessage iMsg) {
        if (iMsg.getContent() instanceof Command) {
            // no need to attach times for command
            return false;
        }
        Visa doc = facebook.getVisa(sender);
        if (doc == null) {
            assert false : "failed to get visa document for sender: " + sender.toString();
            return false;
        }
        // attach sender document time
        Date lastDocumentTime = doc.getTime();
        if (lastDocumentTime == null) {
            assert false : "command error: " + doc.toMap();
        } else {
            iMsg.setDateTime("SDT", lastDocumentTime);
        }
        return true;
    }

    @Override
    public ReliableMessage sendInstantMessage(InstantMessage iMsg, int priority) {
        ID sender = iMsg.getSender();
        //
        //  0. check cycled message
        //
        if (iMsg.getReceiver().equals(sender)) {
            Log.warning("drop cycled message: " + iMsg.getContent() + " "
                    + sender + " => " + iMsg.getReceiver() + ", " + iMsg.getGroup());
            return null;
        } else {
            Log.debug("send instant message (type=" + iMsg.getContent().getType() + "): "
                    + sender + " => " + iMsg.getReceiver() + ", " + iMsg.getGroup());
            // attach sender's document times
            // for the receiver to check whether user info synchronized
            boolean ok = attachVisaTime(sender, iMsg);
            assert ok || iMsg.getContent() instanceof Command : "failed to attach document time: " + sender;
        }
        //
        //  1. encrypt message
        //
        SecureMessage sMsg = encryptMessage(iMsg);
        if (sMsg == null) {
            // assert false : "public key not found?";
            return null;
        }
        //
        //  2. sign message
        //
        ReliableMessage rMsg = signMessage(sMsg);
        if (rMsg == null) {
            // TODO: set msg.state = error
            throw new NullPointerException("failed to sign message: " + sMsg);
        }
        //
        //  3. send message
        //
        if (sendReliableMessage(rMsg, priority)) {
            return rMsg;
        }
        // failed
        return null;
    }

    @Override
    public boolean sendReliableMessage(ReliableMessage rMsg, int priority) {
        // 0. check cycled message
        if (rMsg.getSender().equals(rMsg.getReceiver())) {
            Log.warning("drop cycled message: "
                    + rMsg.getSender() + " => " + rMsg.getReceiver() + ", " + rMsg.getGroup());
            return false;
        }
        // 1. serialize message
        byte[] data = serializeMessage(rMsg);
        if (data == null) {
            assert false : "failed to serialize message: " + rMsg;
            return false;
        }
        // 2. call gate keeper to send the message data package
        //    put message package into the waiting queue of current session
        return session.queueMessagePackage(rMsg, data, priority);
    }

}
