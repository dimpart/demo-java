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

import chat.dim.fsm.BaseState;
import chat.dim.fsm.State;

/**
 *  Session State
 *  ~~~~~~~~~~~~~
 *
 *  Defined for indicating session states
 *
 *      DEFAULT     - initialized
 *      CONNECTING  - connecting to station
 *      CONNECTED   - connected to station
 *      HANDSHAKING - trying to log in
 *      RUNNING     - handshake accepted
 *      ERROR       - network error
 */
public class SessionState extends BaseState<StateMachine, StateTransition> {

    public enum Order {
        DEFAULT,  // = 0
        CONNECTING,
        CONNECTED,
        HANDSHAKING,
        RUNNING,
        ERROR
    }

    private final String name;
    long timestamp;

    SessionState(Order stateOrder) {
        super(stateOrder.ordinal());
        name = stateOrder.name();
        timestamp = 0;
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof SessionState) {
            if (this == other) {
                return true;
            }
            SessionState state = (SessionState) other;
            return state.index == index;
        } else if (other instanceof Order) {
            return ((Order) other).ordinal() == index;
        } else {
            return false;
        }
    }
    public boolean equals(Order other) {
        return other.ordinal() == index;
    }

    @Override
    public void onEnter(State<StateMachine, StateTransition> previous, StateMachine machine, long now) {
        timestamp = now;
    }

    @Override
    public void onExit(State<StateMachine, StateTransition> next, StateMachine machine, long now) {
        timestamp = 0;
    }

    @Override
    public void onPause(StateMachine machine, long now) {
    }

    @Override
    public void onResume(StateMachine machine, long now) {
    }

    /**
     *  Session State Delegate
     *  ~~~~~~~~~~~~~~~~~~~~~~
     *
     *  callback when session state changed
     */
    public interface Delegate extends chat.dim.fsm.Delegate<StateMachine, StateTransition, SessionState> {

    }

    static class Builder {

        StateTransition.Builder stb;

        Builder(StateTransition.Builder transitionBuilder) {
            super();
            stb = transitionBuilder;
        }

        SessionState getDefaultState() {
            SessionState state = new SessionState(SessionState.Order.DEFAULT);
            // Default -> Connecting
            state.addTransition(stb.getDefaultConnectingTransition());
            return state;
        }

        SessionState getConnectingState() {
            SessionState state = new SessionState(SessionState.Order.CONNECTING);
            // Connecting -> Connected
            state.addTransition(stb.getConnectingConnectedTransition());
            // Connecting -> Error
            state.addTransition(stb.getConnectingErrorTransition());
            return state;
        }

        SessionState getConnectedState() {
            SessionState state = new SessionState(SessionState.Order.CONNECTED);
            // Connected -> Handshaking
            state.addTransition(stb.getConnectedHandshakingTransition());
            // Connected -> Error
            state.addTransition(stb.getConnectedErrorTransition());
            return state;
        }

        SessionState getHandshakingState() {
            SessionState state = new SessionState(SessionState.Order.HANDSHAKING);
            // Handshaking -> Running
            state.addTransition(stb.getHandshakingRunningTransition());
            // Handshaking -> Connected
            state.addTransition(stb.getHandshakingConnectedTransition());
            // Handshaking -> Error
            state.addTransition(stb.getHandshakingErrorTransition());
            return state;
        }

        SessionState getRunningState() {
            SessionState state = new SessionState(SessionState.Order.RUNNING);
            // Running -> Default
            state.addTransition(stb.getRunningDefaultTransition());
            // Running -> Error
            state.addTransition(stb.getRunningErrorTransition());
            return state;
        }

        SessionState getErrorState() {
            SessionState state = new SessionState(SessionState.Order.ERROR);
            // Error -> Default
            state.addTransition(stb.getErrorDefaultTransition());
            return state;
        }
    }
}

