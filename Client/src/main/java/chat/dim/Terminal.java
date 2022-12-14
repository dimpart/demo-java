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

import java.util.Date;

import chat.dim.fsm.Delegate;
import chat.dim.network.ClientSession;
import chat.dim.network.SessionState;
import chat.dim.network.StateMachine;
import chat.dim.network.StateTransition;
import chat.dim.protocol.EntityType;
import chat.dim.protocol.ID;
import chat.dim.skywalker.Runner;

public class Terminal extends Runner implements Delegate<StateMachine, StateTransition, SessionState> {

    private final ClientMessenger messenger;
    private final StateMachine fsm;
    private long lastTime;

    public Terminal(ClientMessenger transceiver) {
        super();
        messenger = transceiver;
        // session state
        fsm = new StateMachine(transceiver.getSession());
        fsm.setDelegate(this);
        // default online time
        lastTime = new Date().getTime();
    }

    public ClientMessenger getMessenger() {
        return messenger;
    }

    public ClientSession getSession() {
        return messenger.getSession();
    }

    public SessionState getState() {
        return fsm.getCurrentState();
    }

    public boolean isAlive() {
        // if more than 10 minutes no online command sent
        // means this terminal is dead
        Date now = new Date();
        return now.getTime() < (lastTime + 600 * 1000);
    }

    public void start() {
        Thread thread = new Thread(this);
        thread.setDaemon(true);
        thread.start();
    }

    @Override
    public void setup() {
        super.setup();
        getSession().start();
        fsm.start();
    }

    @Override
    public void finish() {
        fsm.stop();
        getSession().stop();
        super.finish();
    }

    @Override
    protected void idle() {
        idle(60 * 1000);
    }

    @Override
    public boolean process() {
        long now = new Date().getTime();
        if (now < (lastTime + 300 * 1000)) {
            // last sent within 5 minutes
            return false;
        }
        // check session state
        ClientMessenger messenger = getMessenger();
        ClientSession session = messenger.getSession();
        ID uid = session.getIdentifier();
        if (uid == null || !getState().equals(SessionState.RUNNING)) {
            // handshake not accepted
            return false;
        }
        // report every 5 minutes to keep user online
        if (EntityType.STATION.equals(uid.getType())) {
            // a station won't login to another station, if here is a station,
            // it must be a station bridge for roaming messages, we just send
            // report command to the target station to keep session online.
            messenger.reportOnline(uid);
        } else {
            // send login command to everyone to provide more information.
            // this command can keep the user online too.
            messenger.broadcastLogin(uid);
        }
        // update last online time
        lastTime = now;
        return false;
    }

    //
    //  FSM Delegate
    //

    @Override
    public void enterState(SessionState next, StateMachine ctx) {
        // called before state changed
    }

    @Override
    public void exitState(SessionState previous, StateMachine ctx) {
        // called after state changed
        ClientMessenger messenger = getMessenger();
        SessionState current = ctx.getCurrentState();
        if (current == null) {
            return;
        }
        if (current.equals(SessionState.HANDSHAKING)) {
            // start handshake
            messenger.handshake(null);
        } else if (current.equals(SessionState.RUNNING)) {
            // broadcast current meta & visa document to all stations
            messenger.handshakeSuccess();
            // update last online time
            lastTime = new Date().getTime();
        }
    }

    @Override
    public void pauseState(SessionState current, StateMachine ctx) {

    }

    @Override
    public void resumeState(SessionState current, StateMachine ctx) {
        // TODO: clear session key for re-login?
    }
}
