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

import java.util.List;
import java.util.Map;

import chat.dim.dbi.MessageDBI;
import chat.dim.mkm.Station;
import chat.dim.mkm.User;
import chat.dim.network.ClientSession;
import chat.dim.protocol.Content;
import chat.dim.protocol.DocumentCommand;
import chat.dim.protocol.Envelope;
import chat.dim.protocol.ForwardContent;
import chat.dim.protocol.GroupCommand;
import chat.dim.protocol.HandshakeCommand;
import chat.dim.protocol.ID;
import chat.dim.protocol.InstantMessage;
import chat.dim.protocol.LoginCommand;
import chat.dim.protocol.Meta;
import chat.dim.protocol.MetaCommand;
import chat.dim.protocol.ReliableMessage;
import chat.dim.protocol.ReportCommand;
import chat.dim.protocol.SecureMessage;
import chat.dim.protocol.Visa;
import chat.dim.type.Pair;
import chat.dim.utils.Log;
import chat.dim.utils.QueryFrequencyChecker;

/**
 *  Client Messenger for Handshake & Broadcast Report
 */
public class ClientMessenger extends CommonMessenger {

    public ClientMessenger(Session session, CommonFacebook facebook, MessageDBI database) {
        super(session, facebook, database);
    }

    @Override
    public ClientSession getSession() {
        return (ClientSession) super.getSession();
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
        if (sessionKey == null) {
            // first handshake
            CommonFacebook facebook = getFacebook();
            User user = facebook.getCurrentUser();
            assert user != null : "current user not found";
            ID me = user.getIdentifier();
            Envelope env = Envelope.create(me, sid, null);
            Content content = HandshakeCommand.start();
            // send first handshake command as broadcast message
            content.setGroup(Station.EVERY);
            // create instant message with meta & visa
            InstantMessage iMsg = InstantMessage.create(env, content);
            iMsg.setMap("meta", user.getMeta());
            iMsg.setMap("visa", user.getVisa());
            sendInstantMessage(iMsg, -1);
        } else {
            // handshake again
            Content content = HandshakeCommand.restart(sessionKey);
            sendContent(null, sid, content, -1);
        }
    }

    /**
     *  Callback for handshake success
     */
    public void handshakeSuccess() {
        // broadcast current documents after handshake success
        broadcastDocument(false);
    }

    /**
     *  Broadcast meta & visa document to all stations
     */
    public void broadcastDocument(boolean updated) {
        CommonFacebook facebook = getFacebook();
        User user = facebook.getCurrentUser();
        assert user != null : "current user not found";
        Visa visa = user.getVisa();
        if (visa == null) {
            assert false : "visa not found: " + user;
            return;
        }
        ID me = user.getIdentifier();
        Meta meta = user.getMeta();
        DocumentCommand command = DocumentCommand.response(me, meta, visa);
        // send to all contacts
        List<ID> contacts = facebook.getContacts(me);
        if (contacts != null) {
            for (ID item : contacts) {
                sendVisa(me, item, command, updated);
            }
        }
        // broadcast to 'everyone@everywhere'
        sendVisa(me, ID.EVERYONE, command, updated);
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
        sendContent(sender, ID.EVERYONE, content, 1);
    }

    /**
     *  Send report command to keep user online
     */
    public void reportOnline(ID sender) {
        Content content = new ReportCommand(ReportCommand.ONLINE);
        sendContent(sender, Station.ANY, content, 1);
    }

    /**
     *  Send report command to let user offline
     */
    public void reportOffline(ID sender) {
        Content content = new ReportCommand(ReportCommand.OFFLINE);
        sendContent(sender, Station.ANY, content, 1);
    }

    private void sendVisa(ID sender, ID receiver, DocumentCommand content, boolean force) {
        QueryFrequencyChecker checker = QueryFrequencyChecker.getInstance();
        if (!checker.isDocumentResponseExpired(receiver, 0, force)) {
            // response not expired yet
            Log.debug("document response not expired yet: " + receiver);
            return;
        }
        Log.info("push visa to: " + receiver);
        sendContent(sender, receiver, content, 1);
    }

    @Override
    protected boolean queryMeta(ID identifier) {
        QueryFrequencyChecker checker = QueryFrequencyChecker.getInstance();
        if (!checker.isMetaQueryExpired(identifier, 0)) {
            // query not expired yet
            Log.debug("meta query not expired yet: " + identifier);
            return false;
        }
        Log.info("querying meta from any station, ID: " + identifier);
        Content content = MetaCommand.query(identifier);
        sendContent(null, Station.ANY, content, 1);
        return true;
    }

    @Override
    protected boolean queryDocument(ID identifier) {
        QueryFrequencyChecker checker = QueryFrequencyChecker.getInstance();
        if (!checker.isDocumentQueryExpired(identifier, 0)) {
            // query not expired yet
            Log.debug("document query not expired yet: " + identifier);
            return false;
        }
        Log.info("querying document from any station, ID: " + identifier);
        Content content = DocumentCommand.query(identifier);
        sendContent(null, Station.ANY, content, 1);
        return true;
    }

    @Override
    protected boolean queryMembers(ID identifier) {
        QueryFrequencyChecker checker = QueryFrequencyChecker.getInstance();
        if (!checker.isMembersQueryExpired(identifier, 0)) {
            // query not expired yet
            Log.debug("members query not expired yet: " + identifier);
            return false;
        }
        assert identifier.isGroup() : "group ID error: " + identifier;
        Content content = GroupCommand.query(identifier);
        // 1. check group bots
        List<ID> bots = getFacebook().getAssistants(identifier);
        if (bots != null && bots.size() > 0) {
            // querying members from bots
            Log.info("querying members from bots: " + bots + ", group: " + identifier);
            for (ID receiver : bots) {
                sendContent(null, receiver, content, 1);
            }
            return true;
        }
        // 2.. check group owner
        ID owner = getFacebook().getOwner(identifier);
        if (owner != null) {
            // querying members from owner
            Log.info("querying members from owner: " + owner + ", group: " + identifier);
            sendContent(null, owner, content, 1);
            return true;
        }
        Log.warning("group not ready: " + identifier);
        return false;
    }

    @Override
    public ReliableMessage sendInstantMessage(InstantMessage iMsg, int priority) {
        ID receiver = iMsg.getReceiver();
        // NOTICE: because group assistant (bot) cannot be a member of the group, so
        //         if you want to send a group command to any assistant, you must
        //         set the bot ID as 'receiver' and set the group ID in content;
        //         this means you must send it to the bot directly.
        if (receiver.isGroup()) {
            // so this is a group message (not split yet)
            return sendGroupMessage(iMsg, priority);
        }
        // this message is sending to a user/member/bot directly
        return super.sendInstantMessage(iMsg, priority);
    }

    protected ReliableMessage sendGroupMessage(InstantMessage iMsg, int priority) {
        assert iMsg.get("group") == null : "should not happen";
        ID group = iMsg.getReceiver();
        assert group.isGroup() : "group ID error: " + group;
        CommonFacebook facebook = getFacebook();

        // 0. check group bots
        List<ID> bots = facebook.getAssistants(group);
        if (bots == null || bots.isEmpty()) {
            // no 'assistants' found in group's bulletin document?
            // split group messages and send to all members one by one
            int ok = splitGroupMessage(group, iMsg, priority);
            assert ok > 0 : "failed to split messages for group: " + group;
            // TODO:
            return null;
        }

        // group bots designated, let group bot to split the message, so
        // here must expose the group ID; this will cause the client to
        // use a "user-to-group" encrypt key to encrypt the message content,
        // this key will be encrypted by each member's public key, so
        // all members will received a message split by the group bot,
        // but the group bots cannot decrypt it.
        iMsg.setString("group", group);

        // 1. pack messages
        SecureMessage sMsg = encryptMessage(iMsg);
        if (sMsg == null) {
            assert false : "failed to encrypt message for group: " + group;
            return null;
        }
        ReliableMessage rMsg = signMessage(sMsg);
        if (rMsg == null) {
            assert false : "failed to sign message: " + iMsg.getSender() + " => " + group;
            return null;
        }

        // 2. forward the group message to any bot
        ID prime = bots.get(0);
        Content content = ForwardContent.create(rMsg);
        Pair<InstantMessage, ReliableMessage> pair = sendContent(null, prime, content, priority);
        return pair.second;
    }

    /**
     *  split group messages and send to all members one by one
     */
    private int splitGroupMessage(ID group, InstantMessage iMsg, int priority) {
        CommonFacebook facebook = getFacebook();
        // get members
        List<ID> allMembers = facebook.getMembers(group);
        if (allMembers == null/* || allMembers.isEmpty()*/) {
            assert false : "group empty: " + group;
            return -1;
        }
        int success = 0;
        // split messages
        InstantMessage item;
        ReliableMessage res;
        for (ID member : allMembers) {
            Log.info("split group message for member: " + member + ", group: " + group);
            Map<String, Object> info = iMsg.copyMap(false);
            // replace 'receiver' with member ID
            info.put("receiver", member.toString());
            item = InstantMessage.parse(info);
            if (item == null) {
                assert false : "failed to repack message: " + member;
                continue;
            }
            res = super.sendInstantMessage(item, priority);
            if (res == null) {
                assert false : "failed to send message: " + member;
                continue;
            }
            success += 1;
        }
        // done!
        return success;
    }

}
