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

import chat.dim.crypto.SymmetricKey;
import chat.dim.dbi.MessageDBI;
import chat.dim.mkm.Entity;
import chat.dim.mkm.User;
import chat.dim.protocol.Content;
import chat.dim.protocol.Envelope;
import chat.dim.protocol.ID;
import chat.dim.protocol.InstantMessage;
import chat.dim.protocol.ReliableMessage;
import chat.dim.protocol.SecureMessage;
import chat.dim.type.Converter;
import chat.dim.type.Pair;
import chat.dim.utils.Log;

/**
 *  Common Messenger with Session & Database
 */
public abstract class CommonMessenger extends Messenger implements Transmitter {

    private final Session session;
    private final CommonFacebook facebook;
    private final MessageDBI database;
    private Packer packer;
    private Processor processor;

    public CommonMessenger(Session session, CommonFacebook facebook, MessageDBI database) {
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
    protected Entity.Delegate getEntityDelegate() {
        return facebook;
    }

    public CommonFacebook getFacebook() {
        return facebook;
    }

    public MessageDBI getDatabase() {
        return database;
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

    /**
     *  Request for meta with entity ID
     *
     * @param identifier - entity ID
     * @return false on duplicated
     */
    protected abstract boolean queryMeta(ID identifier);

    /**
     *  Request for meta & visa document with entity ID
     *
     * @param identifier - entity ID
     * @return false on duplicated
     */
    protected abstract boolean queryDocument(ID identifier);

    /**
     *  Request for group members with group ID
     *
     * @param identifier - group ID
     * @return false on duplicated
     */
    protected abstract boolean queryMembers(ID identifier);

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

    //
    //  Interfaces for Transmitting Message
    //

    @Override
    public Pair<InstantMessage, ReliableMessage> sendContent(ID sender, ID receiver, Content content, int priority) {
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

    @Override
    public ReliableMessage sendInstantMessage(InstantMessage iMsg, int priority) {
        // 0. check cycled message
        if (iMsg.getSender().equals(iMsg.getReceiver())) {
            Log.warning("drop cycled message: " + iMsg.getContent() + " "
                    + iMsg.getSender() + " => " + iMsg.getReceiver() + ", " + iMsg.getGroup());
            return null;
        } else {
            Log.debug("send instant message (type=" + iMsg.getContent().getType() + "): "
                    + iMsg.getSender() + " => " + iMsg.getReceiver() + ", " + iMsg.getGroup());
        }
        // 1. encrypt message
        SecureMessage sMsg = encryptMessage(iMsg);
        if (sMsg == null) {
            // assert false : "public key not found?";
            return null;
        }
        // 2. sign message
        ReliableMessage rMsg = signMessage(sMsg);
        if (rMsg == null) {
            // TODO: set msg.state = error
            throw new NullPointerException("failed to sign message: " + sMsg);
        }
        // 3. send message
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
        assert data != null : "failed to serialize message: " + rMsg;
        // 2. call gate keeper to send the message data package
        //    put message package into the waiting queue of current session
        return session.queueMessagePackage(rMsg, data, priority);
    }

}
