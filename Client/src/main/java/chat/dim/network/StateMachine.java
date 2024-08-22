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
package chat.dim.network;

import java.lang.ref.WeakReference;

import chat.dim.fsm.AutoMachine;
import chat.dim.fsm.Context;
import chat.dim.port.Porter;
import chat.dim.protocol.ID;

/**
 *  Session States
 *  ~~~~~~~~~~~~~~
 *
 *      +--------------+                +------------------+
 *      |  0.Default   | .............> |   1.Connecting   |
 *      +--------------+                +------------------+
 *          A       A       ................:       :
 *          :       :       :                       :
 *          :       :       V                       V
 *          :   +--------------+        +------------------+
 *          :   |   5.Error    | <..... |   2.Connected    |
 *          :   +--------------+        +------------------+
 *          :       A       A                   A   :
 *          :       :       :................   :   :
 *          :       :                       :   :   V
 *      +--------------+                +------------------+
 *      |  4.Running   | <............. |  3.Handshaking   |
 *      +--------------+                +------------------+
 *
 */
public class StateMachine extends AutoMachine<StateMachine, StateTransition, SessionState> implements Context {

    private final WeakReference<ClientSession> sessionRef;

    public StateMachine(ClientSession clientSession) {
        super();
        sessionRef = new WeakReference<>(clientSession);
        // init states
        SessionState.Builder builder = createStateBuilder();
        addState(builder.getDefaultState());
        addState(builder.getConnectingState());
        addState(builder.getConnectedState());
        addState(builder.getHandshakingState());
        addState(builder.getRunningState());
        addState(builder.getErrorState());
    }

    protected SessionState.Builder createStateBuilder() {
        return new SessionState.Builder(new StateTransition.Builder());
    }

    @Override
    public StateMachine getContext() {
        return this;
    }

    @Override
    public SessionState getDefaultState() {
        return super.getDefaultState();
    }

    public ClientSession getSession() {
        return sessionRef.get();
    }

    public String getSessionKey() {
        ClientSession session = getSession();
        return session == null ? null : session.getKey();
    }

    public ID getSessionID() {
        ClientSession session = getSession();
        return session == null ? null : session.getIdentifier();
    }

    Porter.Status getStatus() {
        ClientSession session = getSession();
        if (session == null) {
            return Porter.Status.ERROR;
        }
        CommonGate gate = session.getGate();
        //Porter docker = gate.getPorter(session.getRemoteAddress(), null, null);
        Porter docker = gate.getPorter(session.getRemoteAddress(), null);
        return docker == null ? Porter.Status.ERROR : docker.getStatus();
    }
}
