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

import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Locale;

import chat.dim.dbi.SessionDBI;
import chat.dim.mkm.Station;
import chat.dim.mkm.User;
import chat.dim.network.ClientSession;
import chat.dim.network.SessionState;
import chat.dim.network.StateMachine;
import chat.dim.port.Docker;
import chat.dim.protocol.EntityType;
import chat.dim.protocol.ID;
import chat.dim.skywalker.Runner;
import chat.dim.utils.Log;

public abstract class Terminal extends Runner implements SessionState.Delegate {

    public final CommonFacebook facebook;
    public final SessionDBI database;

    private ClientMessenger messenger;
    private long lastTime;

    public Terminal(CommonFacebook barrack, SessionDBI sdb) {
        super();
        facebook = barrack;
        database = sdb;
        messenger = null;
        // last online time
        lastTime = 0;
    }

    // "zh-CN"
    public String getLanguage() {
        return Locale.getDefault().getLanguage();
    }

    // "DIM"
    public abstract String getDisplayName();

    // "1.0.1"
    public abstract String getVersionName();

    // "4.0"
    public abstract String getSystemVersion();

    // "HMS"
    public abstract String getSystemModel();

    // "hammerhead"
    public abstract String getSystemDevice();

    // "HUAWEI"
    public abstract String getDeviceBrand();

    // "hammerhead"
    public abstract String getDeviceBoard();

    // "HUAWEI"
    public abstract String getDeviceManufacturer();

    /**
     *  format: "DIMP/1.0 (Linux; U; Android 4.1; zh-CN) DIMCoreKit/1.0 (Terminal, like WeChat) DIM-by-GSP/1.0.1"
     */
    public String getUserAgent() {
        String model = getSystemModel();
        String device = getSystemDevice();
        String sysVersion = getSystemVersion();
        String lang = getLanguage();

        String appName = getDisplayName();
        String appVersion = getVersionName();

        return String.format("DIMP/1.0 (%s; U; %s %s; %s)" +
                        " DIMCoreKit/1.0 (Terminal, like WeChat) %s-by-MOKY/%s",
                model, device, sysVersion, lang, appName, appVersion);
    }

    public ClientMessenger getMessenger() {
        return messenger;
    }

    public ClientSession getSession() {
        ClientMessenger transceiver = messenger;
        if (transceiver == null) {
            return null;
        }
        return transceiver.getSession();
    }

    public ClientMessenger connect(String host, int port) {
        ClientMessenger old = messenger;
        if (old != null) {
            ClientSession session = old.getSession();
            if (session.isActive()) {
                // current session is active
                Station station = session.getStation();
                if (station.getHost().equals(host) && station.getPort() == port) {
                    // same target
                    return old;
                }
            }
            session.stop();
        }
        Log.info("connecting to " + host + ":" + port + " ...");
        // create new messenger with session
        Station station = createStation(host, port);
        ClientSession session = createSession(station);
        messenger = createMessenger(session, facebook);
        // create packer, processor for messenger
        // they have weak references to facebook & messenger
        messenger.setPacker(createPacker(facebook, messenger));
        messenger.setProcessor(createProcessor(facebook, messenger));
        // set weak reference to messenger
        session.setMessenger(messenger);
        return messenger;
    }
    protected Station createStation(String host, int port) {
        Station station = new Station(host, port);
        station.setDataSource(facebook);
        return station;
    }
    protected ClientSession createSession(Station station) {
        ClientSession session = new ClientSession(station, database);
        // set current user for handshaking
        User user = facebook.getCurrentUser();
        if (user != null) {
            session.setIdentifier(user.getIdentifier());
        }
        session.start(this);
        return session;
    }
    protected Packer createPacker(CommonFacebook facebook, ClientMessenger messenger) {
        return new ClientMessagePacker(facebook, messenger);
    }
    protected Processor createProcessor(CommonFacebook facebook, ClientMessenger messenger) {
        return new ClientMessageProcessor(facebook, messenger);
    }
    protected abstract ClientMessenger createMessenger(ClientSession session, CommonFacebook facebook);

    public boolean login(ID current) {
        ClientSession session = getSession();
        if (session == null) {
            return false;
        } else {
            session.setIdentifier(current);
            return true;
        }
    }

    public void enterBackground() {
        ClientMessenger transceiver = messenger;
        if (transceiver == null) {
            // not connect
            return;
        }
        // check signed in user
        ClientSession session = transceiver.getSession();
        ID uid = session.getIdentifier();
        if (uid != null) {
            // already signed in, check session state
            SessionState state = session.getState();
            if (state.equals(SessionState.Order.RUNNING)) {
                // report client state
                transceiver.reportOffline(uid);
                idle(512);
            }
        }
        // pause the session
        session.pause();
    }
    public void enterForeground() {
        ClientMessenger transceiver = messenger;
        if (transceiver == null) {
            // not connect
            return;
        }
        ClientSession session = transceiver.getSession();
        // resume the session
        session.resume();
        // check signed in user
        ID uid = session.getIdentifier();
        if (uid != null) {
            // already signed in, wait a while to check session state
            idle(512);
            SessionState state = session.getState();
            if (state.equals(SessionState.Order.RUNNING)) {
                // report client state
                transceiver.reportOnline(uid);
            }
        }
    }

    public void start() {
        Thread thread = new Thread(this);
        thread.setDaemon(true);
        thread.start();
    }

    @Override
    public void finish() {
        // stop session in messenger
        Messenger transceiver = messenger;
        if (transceiver != null) {
            ClientSession session = getSession();
            session.stop();
            messenger = null;
        }
        super.finish();
    }

    @Override
    protected void idle() {
        idle(16 * 1000);
    }

    @Override
    public boolean process() {
        // check timeout
        long now = System.currentTimeMillis();
        if (!isExpired(lastTime, now)) {
            // not expired yet
            return false;
        }
        // check session state
        ClientMessenger messenger = getMessenger();
        if (messenger == null) {
            // not connect
            return false;
        }
        ClientSession session = messenger.getSession();
        ID uid = session.getIdentifier();
        SessionState state = session.getState();
        if (uid == null || !state.equals(SessionState.Order.RUNNING)) {
            // handshake not accepted
            return false;
        }
        // report every 5 minutes to keep user online
        try {
            keepOnline(uid, messenger);
        } catch (Exception e) {
            e.printStackTrace();
        }
        // update last online time
        lastTime = now;
        return false;
    }

    protected boolean isExpired(long last, long now) {
        // keep online every 5 minutes
        return now < (last + 300 * 1000);
    }

    protected void keepOnline(ID uid, ClientMessenger messenger) {
        if (EntityType.STATION.equals(uid.getType())) {
            // a station won't login to another station, if here is a station,
            // it must be a station bridge for roaming messages, we just send
            // report command to the target station to keep session online.
            messenger.reportOnline(uid);
        } else {
            // send login command to everyone to provide more information.
            // this command can keep the user online too.
            messenger.broadcastLogin(uid, getUserAgent());
        }
    }

    //
    //  FSM Delegate
    //

    @Override
    public void enterState(SessionState next, StateMachine ctx, long now) {
        // called before state changed
    }

    @Override
    public void exitState(SessionState previous, StateMachine ctx, long now) {
        // called after state changed
        ClientMessenger messenger = getMessenger();
        SessionState current = ctx.getCurrentState();
        if (current == null) {
            return;
        }
        if (current.equals(SessionState.Order.DEFAULT) ||
                current.equals(SessionState.Order.CONNECTING)) {
            // check current user
            ID user = ctx.getSessionID();
            if (user == null) {
                Log.warning("current user not set");
                return;
            }
            Log.info("connect for user: " + user);
            ClientSession session = getSession();
            if (session == null) {
                Log.warning("session not found");
                return;
            }
            SocketAddress remote = session.getRemoteAddress();
            Docker docker = session.getGate().getDocker(remote, null, new ArrayList<>());
            if (docker == null) {
                Log.error("failed to connect: " + remote);
            } else {
                Log.info("connected to: " + remote);
            }
        } else if (current.equals(SessionState.Order.HANDSHAKING)) {
            // start handshake
            messenger.handshake(null);
        } else if (current.equals(SessionState.Order.RUNNING)) {
            // broadcast current meta & visa document to all stations
            messenger.handshakeSuccess();
            // update last online time
            lastTime = now;
        }
    }

    @Override
    public void pauseState(SessionState current, StateMachine ctx, long now) {

    }

    @Override
    public void resumeState(SessionState current, StateMachine ctx, long now) {
        // TODO: clear session key for re-login?
    }
}
