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

class StateBuilder {

    TransitionBuilder builder;

    StateBuilder(TransitionBuilder transitionBuilder) {
        super();
        builder = transitionBuilder;
    }

    SessionState getDefaultState() {
        SessionState state = new SessionState(SessionState.DEFAULT);
        // Default -> Connecting
        state.addTransition(builder.getDefaultConnectingTransition());
        return state;
    }

    SessionState getConnectingState() {
        SessionState state = new SessionState(SessionState.CONNECTING);
        // Connecting -> Connected
        state.addTransition(builder.getConnectingConnectedTransition());
        // Connecting -> Error
        state.addTransition(builder.getConnectingErrorTransition());
        return state;
    }

    SessionState getConnectedState() {
        SessionState state = new SessionState(SessionState.CONNECTED);
        // Connected -> Handshaking
        state.addTransition(builder.getConnectedHandshakingTransition());
        // Connected -> Error
        state.addTransition(builder.getConnectedErrorTransition());
        return state;
    }

    SessionState getHandshakingState() {
        SessionState state = new SessionState(SessionState.HANDSHAKING);
        // Handshaking -> Running
        state.addTransition(builder.getHandshakingRunningTransition());
        // Handshaking -> Connected
        state.addTransition(builder.getHandshakingConnectedTransition());
        // Handshaking -> Error
        state.addTransition(builder.getHandshakingErrorTransition());
        return state;
    }

    SessionState getRunningState() {
        SessionState state = new SessionState(SessionState.RUNNING);
        // Running -> Default
        state.addTransition(builder.getRunningDefaultTransition());
        // Running -> Error
        state.addTransition(builder.getRunningErrorTransition());
        return state;
    }

    SessionState getErrorState() {
        SessionState state = new SessionState(SessionState.ERROR);
        // Error -> Default
        state.addTransition(builder.getErrorDefaultTransition());
        return state;
    }
}
