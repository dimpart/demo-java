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

import chat.dim.core.FactoryManager;
import chat.dim.crypto.EncryptKey;
import chat.dim.dbi.MessageDBI;
import chat.dim.mkm.Entity;
import chat.dim.mkm.User;
import chat.dim.protocol.AnsCommand;
import chat.dim.protocol.BlockCommand;
import chat.dim.protocol.Command;
import chat.dim.protocol.Content;
import chat.dim.protocol.Envelope;
import chat.dim.protocol.HandshakeCommand;
import chat.dim.protocol.ID;
import chat.dim.protocol.InstantMessage;
import chat.dim.protocol.LoginCommand;
import chat.dim.protocol.MuteCommand;
import chat.dim.protocol.ReceiptCommand;
import chat.dim.protocol.ReliableMessage;
import chat.dim.protocol.ReportCommand;
import chat.dim.protocol.SecureMessage;
import chat.dim.protocol.Visa;
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
        }
        EncryptKey visaKey = facebook.getPublicKeyForEncryption(sender);
        if (visaKey == null) {
            // sender not ready, try to query document for it
            if (queryDocument(sender)) {
                Log.info("querying document for sender: " + sender);
            }
            Map<String, String> error = new HashMap<>();
            error.put("message", "verify key not found");
            error.put("user", sender.toString());
            rMsg.put("error", error);
            return false;
        }
        // sender is OK
        return true;
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
        } else if (receiver.isUser()) {
            // check user's meta & document
            EncryptKey visaKey = facebook.getPublicKeyForEncryption(receiver);
            if (visaKey == null) {
                // receiver not ready, try to query document for it
                if (queryDocument(receiver)) {
                    Log.info("querying document for receiver: " + receiver);
                }
                Map<String, String> error = new HashMap<>();
                error.put("message", "encrypt key not found");
                error.put("user", receiver.toString());
                iMsg.put("error", error);
                return false;
            }
            // receiver is OK
            return true;
        }
        // NOTICE: group message won't send to a station,
        //         so we DON'T need to check group info here.
        //         group message will be sent to the group assistant bot first,
        //         and the bot will separate the message for all members.
        return false;
    }

    /*/
    @Override
    public byte[] serializeKey(SymmetricKey password, InstantMessage iMsg) {
        // try to reuse message key
        Object reused = password.get("reused");
        if (reused != null) {
            ID receiver = iMsg.getReceiver();
            if (receiver.isGroup()) {
                // reuse key for grouped message
                return null;
            }
            // remove before serialize key
            password.remove("reused");
        }
        byte[] data = super.serializeKey(password, iMsg);
        if (reused != null) {
            // put it back
            password.put("reused", reused);
        }
        return data;
    }
    /*/

    @Override
    public SecureMessage encryptMessage(InstantMessage iMsg) {
        if (!checkReceiver(iMsg)) {
            // receiver not ready
            Log.warning("receiver not ready: " + iMsg.getReceiver());
            return null;
        }
        return super.encryptMessage(iMsg);
    }

    @Override
    public SecureMessage verifyMessage(ReliableMessage rMsg) {
        if (!checkSender(rMsg)) {
            // sender not ready
            Log.warning("sender not ready: " + rMsg.getSender());
            return null;
        }
        return super.verifyMessage(rMsg);
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
        Log.debug("sending message: " + iMsg.getReceiver() + ", " + iMsg.getContent());
        // send message (secured + certified) to target station
        SecureMessage sMsg = encryptMessage(iMsg);
        if (sMsg == null) {
            // public key not found?
            return null;
        }
        ReliableMessage rMsg = signMessage(sMsg);
        if (rMsg == null) {
            // TODO: set msg.state = error
            throw new NullPointerException("failed to sign message: " + sMsg);
        }
        if (sendReliableMessage(rMsg, priority)) {
            return rMsg;
        }
        // failed
        return null;
    }

    @Override
    public boolean sendReliableMessage(ReliableMessage rMsg, int priority) {
        // 1. serialize message
        byte[] data = serializeMessage(rMsg);
        assert data != null : "failed to serialize message: " + rMsg;
        // 2. call gate keeper to send the message data package
        //    put message package into the waiting queue of current session
        return session.queueMessagePackage(rMsg, data, priority);
    }

    /**
     *  Register All Message/Content/Command Factories
     */
    public static void registerAllFactories() {
        //
        //  Register core factories
        //
        FactoryManager man = FactoryManager.getInstance();
        man.registerAllFactories();

        // Handshake
        Command.setFactory(HandshakeCommand.HANDSHAKE, HandshakeCommand::new);
        // Receipt
        Command.setFactory(ReceiptCommand.RECEIPT, ReceiptCommand::new);
        // Login
        Command.setFactory(LoginCommand.LOGIN, LoginCommand::new);
        // Report
        Command.setFactory(ReportCommand.REPORT, ReportCommand::new);
        // Mute
        Command.setFactory(MuteCommand.MUTE, MuteCommand::new);
        // Block
        Command.setFactory(BlockCommand.BLOCK, BlockCommand::new);
        // ANS
        Command.setFactory(AnsCommand.ANS, AnsCommand::new);
    }
}
