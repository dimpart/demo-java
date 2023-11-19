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

import chat.dim.mkm.Station;
import chat.dim.mkm.User;
import chat.dim.network.ClientSession;
import chat.dim.protocol.Content;
import chat.dim.protocol.Envelope;
import chat.dim.protocol.HandshakeCommand;
import chat.dim.protocol.ID;
import chat.dim.protocol.InstantMessage;
import chat.dim.protocol.LoginCommand;
import chat.dim.protocol.ReportCommand;

/**
 *  Client Messenger for Handshake & Broadcast Report
 */
public class ClientMessenger extends CommonMessenger {

    public ClientMessenger(Session session, CommonFacebook facebook, CipherKeyDelegate database) {
        super(session, facebook, database);
    }

    @Override
    public ClientSession getSession() {
        return (ClientSession) super.getSession();
    }

    protected ClientArchivist getArchivist() {
        CommonFacebook facebook = getFacebook();
        return (ClientArchivist) facebook.getArchivist();
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
        ClientArchivist archivist = getArchivist();
        archivist.broadcastDocument(false);
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

}
