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

import chat.dim.core.CipherKeyDelegate;
import chat.dim.core.Packer;
import chat.dim.core.Processor;
import chat.dim.core.Session;
import chat.dim.core.Transmitter;
import chat.dim.crypto.EncryptKey;
import chat.dim.crypto.VerifyKey;
import chat.dim.dbi.MessageDBI;
import chat.dim.mkm.Entity;
import chat.dim.mkm.User;
import chat.dim.protocol.Command;
import chat.dim.protocol.Content;
import chat.dim.protocol.Envelope;
import chat.dim.protocol.GroupCommand;
import chat.dim.protocol.HandshakeCommand;
import chat.dim.protocol.ID;
import chat.dim.protocol.InstantMessage;
import chat.dim.protocol.LoginCommand;
import chat.dim.protocol.Meta;
import chat.dim.protocol.ReceiptCommand;
import chat.dim.protocol.ReliableMessage;
import chat.dim.protocol.ReportCommand;
import chat.dim.protocol.SecureMessage;
import chat.dim.protocol.Visa;
import chat.dim.protocol.group.QueryCommand;
import chat.dim.type.Pair;
import chat.dim.utils.QueryFrequencyChecker;

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
    protected boolean queryMembers(ID identifier) {
        QueryFrequencyChecker checker = QueryFrequencyChecker.getInstance();
        if (!checker.isMembersQueryExpired(identifier, 0)) {
            // query not expired yet
            return false;
        }
        assert identifier.isGroup() : "group ID error: " + identifier;
        List<ID> assistants = facebook.getAssistants(identifier);
        // request to group bots
        if (assistants == null || assistants.size() == 0) {
            // group assistants not found
            return false;
        }
        // querying members
        QueryCommand cmd = GroupCommand.query(identifier);
        for (ID bot : assistants) {
            sendContent(null, bot, cmd, 1);
        }
        return true;
    }

    /**
     *  Check whether msg.sender is ready
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
        List<VerifyKey> verifyKeys = facebook.getPublicKeysForVerification(sender);
        if (verifyKeys != null && verifyKeys.size() > 0) {
            // sender is OK
            return true;
        }
        queryDocument(sender);
        Map<String, String> error = new HashMap<>();
        error.put("message", "verify key not found");
        error.put("user", sender.toString());
        rMsg.put("error", error);
        return false;
    }

    /**
     *  Check whether msg.receiver is ready
     *
     * @param iMsg - plain message
     * @return false on encrypt key not found
     */
    protected boolean checkReceiver(InstantMessage iMsg) {
        ID receiver = iMsg.getReceiver();
        if (receiver.isBroadcast()) {
            // broadcast message
            return true;
        }
        if (receiver.isUser()) {
            // check user's meta & document
            EncryptKey visaKey = facebook.getPublicKeyForEncryption(receiver);
            if (visaKey == null) {
                queryDocument(receiver);
                Map<String, String> error = new HashMap<>();
                error.put("message", "encrypt key not found");
                error.put("user", receiver.toString());
                iMsg.put("error", error);
                return false;
            }
        } else {
            // check group's meta
            Meta meta = facebook.getMeta(receiver);
            if (meta == null) {
                queryMeta(receiver);
                Map<String, String> error = new HashMap<>();
                error.put("message", "group meta not found");
                error.put("user", receiver.toString());
                iMsg.put("error", error);
                return false;
            }
            // check group members
            List<ID> members = facebook.getMembers(receiver);
            if (members == null || members.size() == 0) {
                queryMembers(receiver);
                Map<String, String> error = new HashMap<>();
                error.put("message", "members not found");
                error.put("user", receiver.toString());
                iMsg.put("error", error);
                return false;
            }
            List<ID> waiting = new ArrayList<>();
            for (ID item : members) {
                if (facebook.getPublicKeyForEncryption(item) == null) {
                    queryDocument(item);
                    waiting.add(item);
                }
            }
            if (waiting.size() > 0) {
                Map<String, Object> error = new HashMap<>();
                error.put("message", "encrypt keys not found");
                error.put("group", receiver.toString());
                error.put("members", ID.revert(waiting));
                iMsg.put("error", error);
                return false;
            }
        }
        // receiver is OK
        return true;
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
            return null;
        }
        return super.encryptMessage(iMsg);
    }

    @Override
    public SecureMessage verifyMessage(ReliableMessage rMsg) {
        if (!checkSender(rMsg)) {
            // sender not ready
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
        registerCoreFactories();

        // Handshake
        Command.setFactory(HandshakeCommand.HANDSHAKE, HandshakeCommand::new);
        // Receipt
        Command.setFactory(ReceiptCommand.RECEIPT, ReceiptCommand::new);
        // Login
        Command.setFactory(LoginCommand.LOGIN, LoginCommand::new);
        // Report
        Command.setFactory(ReportCommand.REPORT, ReportCommand::new);
    }
}
