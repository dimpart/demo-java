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

import chat.dim.port.Docker;

class TransitionBuilder {

    /**
     *  Default -> Connecting
     *  ~~~~~~~~~~~~~~~~~~~~~
     *  When the session ID was set, and connection is building.
     *
     *  The session key must be empty now, it will be set
     *  after handshake success.
     */
    StateTransition getDefaultConnectingTransition() {
        return new StateTransition(SessionState.CONNECTING) {
            @Override
            public boolean evaluate(StateMachine ctx, long now) {
                if (ctx.getSessionID() == null) {
                    // current user not set yet
                    return false;
                }
                Docker.Status status = ctx.getStatus();
                return status.equals(Docker.Status.PREPARING) || status.equals(Docker.Status.READY);
            }
        };
    }

    /**
     *  Connecting -> Connected
     *  ~~~~~~~~~~~~~~~~~~~~~~~
     *  When connection built.
     *
     *  The session ID must be set, and the session key must be empty now.
     */
    StateTransition getConnectingConnectedTransition() {
        return new StateTransition(SessionState.CONNECTED) {
            @Override
            public boolean evaluate(StateMachine ctx, long now) {
                Docker.Status status = ctx.getStatus();
                return status.equals(Docker.Status.READY);
            }
        };
    }

    /**
     *  Connecting -> Error
     *  ~~~~~~~~~~~~~~~~~~~
     *  When connection lost.
     *
     *  The session ID must be set, and the session key must be empty now.
     */
    StateTransition getConnectingErrorTransition() {
        return new StateTransition(SessionState.ERROR) {
            @Override
            public boolean evaluate(StateMachine ctx, long now) {
                if (isExpired(ctx.getCurrentState(), now)) {
                    // connecting expired, do it again
                    return true;
                }
                Docker.Status status = ctx.getStatus();
                return !(status.equals(Docker.Status.PREPARING) || status.equals(Docker.Status.READY));
            }
        };
    }

    /**
     *  Connected -> Handshaking
     *  ~~~~~~~~~~~~~~~~~~~~~~~~
     *  Do handshaking immediately after connected.
     *
     *  The session ID must be set, and the session key must be empty now.
     */
    StateTransition getConnectedHandshakingTransition() {
        return new StateTransition(SessionState.HANDSHAKING) {
            @Override
            public boolean evaluate(StateMachine ctx, long now) {
                if (ctx.getSessionID() == null) {
                    // FIXME: current user lost?
                    //        state will be changed to 'error'
                    return false;
                }
                Docker.Status status = ctx.getStatus();
                return status.equals(Docker.Status.READY);
            }
        };
    }

    /**
     *  Connected -> Error
     *  ~~~~~~~~~~~~~~~~~~
     *  When connection lost.
     *
     *  The session ID must be set, and the session key must be empty now.
     */
    StateTransition getConnectedErrorTransition() {
        return new StateTransition(SessionState.ERROR) {
            @Override
            public boolean evaluate(StateMachine ctx, long now) {
                if (ctx.getSessionID() == null) {
                    // FIXME: current user lost?
                    return true;
                }
                Docker.Status status = ctx.getStatus();
                return !status.equals(Docker.Status.READY);
            }
        };
    }

    /**
     *  Handshaking -> Running
     *  ~~~~~~~~~~~~~~~~~~~~~~
     *  When session key was set (handshake success).
     *
     *  The session ID must be set.
     */
    StateTransition getHandshakingRunningTransition() {
        return new StateTransition(SessionState.RUNNING) {
            @Override
            public boolean evaluate(StateMachine ctx, long now) {
                if (ctx.getSessionID() == null) {
                    // FIXME: current user lost?
                    //        state will be changed to 'error'
                    return false;
                }
                Docker.Status status = ctx.getStatus();
                if (!status.equals(Docker.Status.READY)) {
                    // connection lost, state will be changed to 'error'
                    return false;
                }
                // when current user changed, the session key will cleared, so
                // if it's set again, it means handshake success
                return ctx.getSessionKey() != null;
            }
        };
    }

    /**
     *  Handshaking -> Connected
     *  ~~~~~~~~~~~~~~~~~~~~~~~~
     *  When handshaking expired.
     *
     *  The session ID must be set, and the session key must be empty now.
     */
    StateTransition getHandshakingConnectedTransition() {
        return new StateTransition(SessionState.CONNECTED) {
            @Override
            public boolean evaluate(StateMachine ctx, long now) {
                if (ctx.getSessionID() == null) {
                    // FIXME: current user lost?
                    //        state will be changed to 'error'
                    return false;
                }
                Docker.Status status = ctx.getStatus();
                if (!status.equals(Docker.Status.READY)) {
                    // connection lost, state will be changed to 'error'
                    return false;
                }
                if (ctx.getSessionKey() != null) {
                    // session key was set, state will be changed to 'running'
                    return false;
                }
                // handshake expired, do it again
                return isExpired(ctx.getCurrentState(), now);
            }
        };
    }

    /**
     *  Handshaking -> Error
     *  ~~~~~~~~~~~~~~~~~~~~
     *  When connection lost.
     *
     *  The session ID must be set, and the session key must be empty now.
     */
    StateTransition getHandshakingErrorTransition() {
        return new StateTransition(SessionState.ERROR) {
            @Override
            public boolean evaluate(StateMachine ctx, long now) {
                if (ctx.getSessionID() == null) {
                    // FIXME: current user lost?
                    //        state will be changed to 'error'
                    return true;
                }
                Docker.Status status = ctx.getStatus();
                return !status.equals(Docker.Status.READY);
            }
        };
    }

    /**
     *  Running -> Default
     *  ~~~~~~~~~~~~~~~~~~
     *  When session id or session key was erased.
     *
     *  If session id was erased, it means user logout, the session key
     *  must be removed at the same time;
     *  If only session key was erased, but the session id kept the same,
     *  it means force the user login again.
     */
    StateTransition getRunningDefaultTransition() {
        return new StateTransition(SessionState.DEFAULT) {
            @Override
            public boolean evaluate(StateMachine ctx, long now) {
                Docker.Status status = ctx.getStatus();
                if (!status.equals(Docker.Status.READY)) {
                    // connection lost, state will be changed to 'error'
                    return false;
                }
                if (ctx.getSessionID() == null) {
                    // user logout / switched?
                    return true;
                }
                // force user login again?
                return ctx.getSessionKey() == null;
            }
        };
    }

    /**
     *  Running -> Error
     *  ~~~~~~~~~~~~~~~~
     *  When connection lost.
     */
    StateTransition getRunningErrorTransition() {
        return new StateTransition(SessionState.ERROR) {
            @Override
            public boolean evaluate(StateMachine ctx, long now) {
                Docker.Status status = ctx.getStatus();
                return !status.equals(Docker.Status.READY);
            }
        };
    }

    /**
     *  Error -> Default
     *  ~~~~~~~~~~~~~~~~
     *  When connection reset.
     */
    StateTransition getErrorDefaultTransition() {
        return new StateTransition(SessionState.DEFAULT) {
            @Override
            public boolean evaluate(StateMachine ctx, long now) {
                Docker.Status status = ctx.getStatus();
                return !status.equals(Docker.Status.ERROR);
            }
        };
    }
}
