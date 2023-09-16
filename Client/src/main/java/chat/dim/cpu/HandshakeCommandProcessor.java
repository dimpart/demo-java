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
package chat.dim.cpu;

import java.util.List;

import chat.dim.ClientMessenger;
import chat.dim.Facebook;
import chat.dim.Messenger;
import chat.dim.mkm.Station;
import chat.dim.network.ClientSession;
import chat.dim.protocol.Content;
import chat.dim.protocol.HandshakeCommand;
import chat.dim.protocol.ID;
import chat.dim.protocol.ReliableMessage;
import chat.dim.utils.Log;

public class HandshakeCommandProcessor extends BaseCommandProcessor {

    public HandshakeCommandProcessor(Facebook facebook, Messenger messenger) {
        super(facebook, messenger);
    }

    @Override
    protected ClientMessenger getMessenger() {
        return (ClientMessenger) super.getMessenger();
    }

    @Override
    public List<Content> process(Content content, ReliableMessage rMsg) {
        assert content instanceof HandshakeCommand : "handshake command error: " + content;
        HandshakeCommand command = (HandshakeCommand) content;
        ClientMessenger messenger = getMessenger();
        ClientSession session = messenger.getSession();
        // update station's default ID ('station@anywhere') to sender (real ID)
        Station station = session.getStation();
        ID oid = station.getIdentifier();
        ID sender = rMsg.getSender();
        if (oid == null || oid.isBroadcast()) {
            station.setIdentifier(sender);
        } else {
            assert oid.equals(sender) : "station ID not match: " + oid + ", " + sender;
        }
        // handle handshake command with title & session key
        String title = command.getTitle();
        String newKey = command.getSessionKey();
        String oldKey = session.getKey();
        assert newKey != null : "new session key should not be empty: " + command;
        if (title.equals("DIM?")) {
            // S -> C: station ask client to handshake again
            if (oldKey == null) {
                // first handshake response with new session key
                messenger.handshake(newKey);
            } else if (oldKey.equals(newKey)) {
                // duplicated handshake response?
                // or session expired and the station ask to handshake again?
                messenger.handshake(newKey);
            } else {
                // connection changed?
                // erase session key to handshake again
                session.setKey(null);
            }
        } else if (title.equals("DIM!")) {
            // S -> C: handshake accepted by station
            if (oldKey == null) {
                // normal handshake response,
                // update session key to change state to 'running'
                session.setKey(newKey);
            } else if (oldKey.equals(newKey)) {
                // duplicated handshake response?
                Log.warning("duplicated handshake response");
                // set it again here to invoke the flutter channel
                session.setKey(newKey);
            } else {
                // FIXME: handshake error
                // erase session key to handshake again
                session.setKey(null);
            }
        } else {
            // C -> S: Hello world!
            Log.warning("Handshake from other user? " + sender + ": " + content);
        }
        return null;
    }
}
