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

    public static final String DEFAULT     = "default";
    public static final String CONNECTING  = "connecting";
    public static final String CONNECTED   = "connected";
    public static final String HANDSHAKING = "handshaking";
    public static final String RUNNING     = "running";
    public static final String ERROR       = "error";

    public final String name;
    long timestamp;

    SessionState(String stateName) {
        super();
        name = stateName;
        timestamp = 0;
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        } else if (other instanceof SessionState) {
            return ((SessionState) other).name.equals(name);
        } else if (other instanceof String) {
            return other.equals(name);
        } else {
            return false;
        }
    }
    public boolean equals(String other) {
        return name.equals(other);
    }

    @Override
    public void onEnter(State<StateMachine, StateTransition> previous,
                        StateMachine machine) {
        timestamp = System.currentTimeMillis();
    }

    @Override
    public void onExit(State<StateMachine, StateTransition> next,
                       StateMachine machine) {
        timestamp = 0;
    }

    @Override
    public void onPause(StateMachine machine) {
    }

    @Override
    public void onResume(StateMachine machine) {
    }
}

