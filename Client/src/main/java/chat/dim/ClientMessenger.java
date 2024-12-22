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
import java.util.List;

import chat.dim.core.CipherKeyDelegate;
import chat.dim.mkm.Station;
import chat.dim.mkm.User;
import chat.dim.msg.MessageHelper;
import chat.dim.network.ClientSession;
import chat.dim.protocol.Command;
import chat.dim.protocol.Content;
import chat.dim.protocol.ContentType;
import chat.dim.protocol.EntityType;
import chat.dim.protocol.Envelope;
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
import chat.dim.utils.Log;

/**
 *  Client Messenger for Handshake & Broadcast Report
 */
public abstract class ClientMessenger extends CommonMessenger {

    public ClientMessenger(Session session, CommonFacebook facebook, CipherKeyDelegate database) {
        super(session, facebook, database);
    }

    @Override
    public ClientSession getSession() {
        return (ClientSession) super.getSession();
    }

    @Override
    public List<ReliableMessage> processReliableMessage(ReliableMessage rMsg) {
        List<ReliableMessage> responses = super.processReliableMessage(rMsg);
        if (responses == null || responses.isEmpty()) {
            if (needsReceipt(rMsg)) {
                ReliableMessage res = buildReceipt(rMsg.getEnvelope());
                if (res != null) {
                    responses = new ArrayList<>();
                    responses.add(res);
                }
            }
        }
        return responses;
    }

    protected ReliableMessage buildReceipt(Envelope originalEnvelope) {
        User user = facebook.getCurrentUser();
        if (user == null) {
            assert false : "failed to get current user";
            return null;
        }
        ID me = user.getIdentifier();
        ID to = originalEnvelope.getSender();
        Content res = ReceiptCommand.create("Message received.", originalEnvelope, null);
        Envelope env = Envelope.create(me, to, null);
        InstantMessage iMsg = InstantMessage.create(env, res);
        SecureMessage sMsg = encryptMessage(iMsg);
        if (sMsg == null) {
            assert false : "failed to encrypt message: " + me + " -> " + to;
            return null;
        }
        ReliableMessage rMsg = signMessage(sMsg);
        if (rMsg == null) {
            assert false : "failed to sign message: " + me + " -> " + to;
            return null;
        }
        return rMsg;
    }

    protected boolean needsReceipt(ReliableMessage rMsg) {
        if (ContentType.COMMAND.equals(rMsg.getType())) {
            // filter for looping message (receipt for receipt)
            return false;
        }
        ID sender = rMsg.getSender();
        /*/
        ID receiver = rMsg.getReceiver();
        if (EntityType.STATION.equals(receiver.getType()) || EntityType.BOT.equals(receiver.getType())) {
            if (EntityType.STATION.equals(sender.getType()) || EntityType.BOT.equals(sender.getType())) {
                // message between bots
                return false;
            }
        }
        /*/
        boolean allow = EntityType.USER.equals(sender.getType()); // || EntityType.USER.equals(receiver.getType());
        if (!allow) {
            // message between bots
            return false;
        }
        /*/
        User user = facebook.getCurrentUser();
        if (!user.getIdentifier().equals(receiver)) {
            // forward message
            return true;
        }
        /*/
        // TODO: other condition?
        return true;
    }

    @Override
    public ReliableMessage sendInstantMessage(InstantMessage iMsg, int priority) {
        ClientSession session = getSession();
        if (session == null || !session.isReady()) {
            // not login yet
            Content content = iMsg.getContent();
            Command command;
            if (content instanceof Command) {
                command = (Command) content;
            } else {
                Log.warning("not handshake yet, suspend message: " + content);
                // TODO: suspend instant message
                return null;
            }
            if (HandshakeCommand.HANDSHAKE.equals(command.getCommandName())) {
                // NOTICE: only handshake message can go out now
                iMsg.put("pass", "handshaking");
            } else {
                Log.warning("not handshake yet, drop message: " + content);
                // TODO: suspend instant message
                return null;
            }
        }
        return super.sendInstantMessage(iMsg, priority);
    }

    @Override
    public boolean sendReliableMessage(ReliableMessage rMsg, int priority) {
        Object passport = rMsg.remove("pass");
        ClientSession session = getSession();
        if (session != null && session.isReady()) {
            // OK, any message can go out
            assert passport == null : "should not happen";
        } else if ("handshaking".equals(passport)) {
            Log.info("not login yet, let the handshake message go out only");
        } else {
            Log.error("not handshake yet, suspend message: " + rMsg.getReceiver());
            // TODO: suspend reliable message
            return false;
        }
        return super.sendReliableMessage(rMsg, priority);
    }

    /**
     *  Send handshake command to current station
     *
     * @param sessionKey - respond session key
     */
    public void handshake(String sessionKey) {
        ClientSession session = getSession();
        Station station = session.getStation();
        ID sid = station.getIdentifier();
        if (sessionKey == null || sessionKey.isEmpty()) {
            // first handshake
            User user = facebook.getCurrentUser();
            assert user != null : "current user not found";
            ID me = user.getIdentifier();
            Envelope env = Envelope.create(me, sid, null);
            Content content = HandshakeCommand.start();
            // send first handshake command as broadcast message
            content.setGroup(Station.EVERY);
            // update visa before first handshake
            updateVisa();
            Meta meta = user.getMeta();
            Visa visa = user.getVisa();
            // create instant message with meta & visa
            InstantMessage iMsg = InstantMessage.create(env, content);
            MessageHelper.setMeta(meta, iMsg);
            MessageHelper.setVisa(visa, iMsg);
            sendInstantMessage(iMsg, -1);
        } else {
            // handshake again
            Content content = HandshakeCommand.restart(sessionKey);
            sendContent(content, null, sid, -1);
        }
    }

    protected void updateVisa() {
        // TODO: update visa for first handshake
        Log.warning("TODO: update visa for first handshake");
    }

    /**
     *  Callback for handshake success
     */
    public void handshakeSuccess() {
        // change the flag of current session
        ClientSession session = getSession();
        Log.info("handshake success, change session accepted: " + session.isAccepted() + " -> true");
        session.setAccepted(true);
        // broadcast current documents after handshake success
        broadcastDocument(false);
        // TODO: let a service bot to do this job
    }

    /**
     *  Broadcast meta & visa document to all stations
     */
    public void broadcastDocument(boolean updated) {
        User user = facebook.getCurrentUser();
        assert user != null : "current user not found";
        Visa visa = user.getVisa();
        if (visa == null) {
            assert false : "visa not found: " + user;
            return;
        }
        ID me = user.getIdentifier();
        EntityChecker checker = facebook.getEntityChecker();
        //
        //  send to all contacts
        //
        List<ID> contacts = facebook.getContacts(me);
        if (contacts == null) {
            Log.warning("contacts not found: " + me);
        } else for (ID item : contacts) {
            checker.sendVisa(visa, item, updated);
        }
        //
        //  broadcast to 'everyone@everywhere'
        //
        checker.sendVisa(visa, ID.EVERYONE, updated);
    }

    /**
     *  Send login command to keep roaming
     */
    public void broadcastLogin(ID sender, String userAgent) {
        ClientSession session = getSession();
        Station station = session.getStation();
        // create login command
        LoginCommand content = new LoginCommand(sender);
        content.setAgent(userAgent);
        content.setStation(station);
        // broadcast to 'everyone@everywhere'
        sendContent(content, sender, ID.EVERYONE, 1);
    }

    /**
     *  Send report command to keep user online
     */
    public void reportOnline(ID sender) {
        Content content = new ReportCommand(ReportCommand.ONLINE);
        sendContent(content, sender, Station.ANY, 1);
    }

    /**
     *  Send report command to let user offline
     */
    public void reportOffline(ID sender) {
        Content content = new ReportCommand(ReportCommand.OFFLINE);
        sendContent(content, sender, Station.ANY, 1);
    }

}
