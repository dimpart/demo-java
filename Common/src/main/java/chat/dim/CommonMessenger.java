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
import java.util.List;
import java.util.Map;

import chat.dim.core.FactoryManager;
import chat.dim.crypto.EncryptKey;
import chat.dim.dbi.MessageDBI;
import chat.dim.mkm.Entity;
import chat.dim.mkm.Group;
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
import chat.dim.protocol.Meta;
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
     *  Request for group members with group ID
     *
     * @param identifier - group ID
     * @return false on duplicated
     */
    protected abstract boolean queryMembers(ID identifier);

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
        EncryptKey visaKey = facebook.getPublicKeyForEncryption(user);
        if (visaKey != null) {
            // user is ready
            return visaKey;
        }
        // user not ready, try to query document for it
        if (queryDocument(user)) {
            Log.info("querying document for user: " + user);
        }
        return null;
    }

    // for checking whether group's ready
    protected List<ID> getMembers(ID group) {
        CommonFacebook facebook = getFacebook();
        Meta meta = facebook.getMeta(group);
        if (meta == null/* || meta.getKey() == null*/) {
            // group not ready, try to query meta for it
            if (queryMeta(group)) {
                Log.info("querying meta for group: " + group);
            }
            return null;
        }
        Group grp = facebook.getGroup(group);
        List<ID> members = grp.getMembers();
        if (members == null || members.size() == 0) {
            // group not ready, try to query members for it
            if (queryMembers(group)) {
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

    protected boolean checkReceiver(SecureMessage sMsg) {
        ID receiver = sMsg.getReceiver();
        if (receiver.isBroadcast()) {
            // broadcast message
            return true;
        } else if (receiver.isGroup()) {
            // check for received group message
            List<ID> members = getMembers(receiver);
            return members != null;
        }
        // the facebook will select a user from local users to match this receiver,
        // if no user matched (private key not found), this message will be ignored.
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
        } else if (receiver.isGroup()) {
            // NOTICE: station will never send group message, so
            //         we don't need to check group info here; and
            //         if a client wants to send group message,
            //         that should be sent to a group bot first,
            //         and the bot will separate it for all members.
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
        if (!checkReceiver(rMsg)) {
            // receiver (group) not ready
            Log.warning("receiver not ready: " + rMsg.getReceiver());
            return null;
        }
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
        Log.debug("send instant message (type=" + iMsg.getContent().getType() + "): "
                + iMsg.getSender() + " -> " + iMsg.getReceiver());
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
