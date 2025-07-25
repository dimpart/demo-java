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

import java.util.Date;

import chat.dim.fsm.BaseTransition;
import chat.dim.log.Log;
import chat.dim.port.Porter;

/**
 *  Transitions
 *  ~~~~~~~~~~~
 *
 *      0.1 - when session ID was set, change state 'default' to 'connecting';
 *
 *      1.2 - when connection built, change state 'connecting' to 'connected';
 *      1.5 - if connection failed, change state 'connecting' to 'error';
 *
 *      2.3 - if no error occurs, change state 'connected' to 'handshaking';
 *      2.5 - if connection lost, change state 'connected' to 'error';
 *
 *      3.2 - if handshaking expired, change state 'handshaking' to 'connected';
 *      3.4 - when session key was set, change state 'handshaking' to 'running';
 *      3.5 - if connection lost, change state 'handshaking' to 'error';
 *
 *      4.0 - when session ID/key erased, change state 'running' to 'default';
 *      4.5 - when connection lost, change state 'running' to 'error';
 *
 *      5.0 - when connection reset, change state 'error' to 'default'.
 */
public abstract class StateTransition extends BaseTransition<StateMachine> {

    StateTransition(SessionState.Order order) {
        super(order.ordinal());
    }

    boolean isExpired(SessionState state, Date now) {
        Date last = state.enterTime;
        return last != null && last.getTime() < (now.getTime() - 30 * 1000);
    }

    /**
     *  Transition Builder
     *  ~~~~~~~~~~~~~~~~~~
     */
    static class Builder {

        /**
         *  Default -> Connecting
         *  ~~~~~~~~~~~~~~~~~~~~~
         *  When the session ID was set, and connection is building.
         *
         *  The session key must be empty now, it will be set
         *  after handshake success.
         */
        StateTransition getDefaultConnectingTransition() {
            return new StateTransition(SessionState.Order.CONNECTING) {
                @Override
                public boolean evaluate(StateMachine ctx, Date now) {
                    // change to 'connecting' when current user set
                    return ctx.getSessionID() != null;
                    /*/
                    Docker.Status status = ctx.getStatus();
                    return status.equals(Docker.Status.PREPARING) || status.equals(Docker.Status.READY);
                    /*/
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
            return new StateTransition(SessionState.Order.CONNECTED) {
                @Override
                public boolean evaluate(StateMachine ctx, Date now) {
                    Porter.Status status = ctx.getStatus();
                    return status.equals(Porter.Status.READY);
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
            return new StateTransition(SessionState.Order.ERROR) {
                @Override
                public boolean evaluate(StateMachine ctx, Date now) {
                    if (isExpired(ctx.getCurrentState(), now)) {
                        // connecting expired, do it again
                        return true;
                    }
                    Porter.Status status = ctx.getStatus();
                    return !(status.equals(Porter.Status.PREPARING) || status.equals(Porter.Status.READY));
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
            return new StateTransition(SessionState.Order.HANDSHAKING) {
                @Override
                public boolean evaluate(StateMachine ctx, Date now) {
                    if (ctx.getSessionID() == null) {
                        // FIXME: current user lost?
                        //        state will be changed to 'error'
                        return false;
                    }
                    Porter.Status status = ctx.getStatus();
                    return status.equals(Porter.Status.READY);
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
            return new StateTransition(SessionState.Order.ERROR) {
                @Override
                public boolean evaluate(StateMachine ctx, Date now) {
                    if (ctx.getSessionID() == null) {
                        // FIXME: current user lost?
                        return true;
                    }
                    Porter.Status status = ctx.getStatus();
                    return !status.equals(Porter.Status.READY);
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
            return new StateTransition(SessionState.Order.RUNNING) {
                @Override
                public boolean evaluate(StateMachine ctx, Date now) {
                    if (ctx.getSessionID() == null) {
                        // FIXME: current user lost?
                        //        state will be changed to 'error'
                        return false;
                    }
                    Porter.Status status = ctx.getStatus();
                    if (!status.equals(Porter.Status.READY)) {
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
            return new StateTransition(SessionState.Order.CONNECTED) {
                @Override
                public boolean evaluate(StateMachine ctx, Date now) {
                    if (ctx.getSessionID() == null) {
                        // FIXME: current user lost?
                        //        state will be changed to 'error'
                        return false;
                    }
                    Porter.Status status = ctx.getStatus();
                    if (!status.equals(Porter.Status.READY)) {
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
            return new StateTransition(SessionState.Order.ERROR) {
                @Override
                public boolean evaluate(StateMachine ctx, Date now) {
                    if (ctx.getSessionID() == null) {
                        // FIXME: current user lost?
                        //        state will be changed to 'error'
                        return true;
                    }
                    Porter.Status status = ctx.getStatus();
                    return !status.equals(Porter.Status.READY);
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
            return new StateTransition(SessionState.Order.DEFAULT) {
                @Override
                public boolean evaluate(StateMachine ctx, Date now) {
                    Porter.Status status = ctx.getStatus();
                    if (!status.equals(Porter.Status.READY)) {
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
            return new StateTransition(SessionState.Order.ERROR) {
                @Override
                public boolean evaluate(StateMachine ctx, Date now) {
                    Porter.Status status = ctx.getStatus();
                    return !status.equals(Porter.Status.READY);
                }
            };
        }

        /**
         *  Error -> Default
         *  ~~~~~~~~~~~~~~~~
         *  When connection reset.
         */
        StateTransition getErrorDefaultTransition() {
            return new StateTransition(SessionState.Order.DEFAULT) {
                @Override
                public boolean evaluate(StateMachine ctx, Date now) {
                    Porter.Status status = ctx.getStatus();
                    Log.debug("docker status: " + status);
                    return !status.equals(Porter.Status.ERROR);
                }
            };
        }
    }
}
