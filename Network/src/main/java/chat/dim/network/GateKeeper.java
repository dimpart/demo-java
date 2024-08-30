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

import java.io.IOError;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.channels.SocketChannel;
import java.util.Date;

import chat.dim.net.Connection;
import chat.dim.net.Hub;
import chat.dim.pack.DeparturePacker;
import chat.dim.port.Arrival;
import chat.dim.port.Departure;
import chat.dim.port.Porter;
import chat.dim.protocol.ReliableMessage;
import chat.dim.queue.MessageQueue;
import chat.dim.queue.MessageWrapper;
import chat.dim.skywalker.Runner;
import chat.dim.tcp.StreamChannel;
import chat.dim.tcp.StreamHub;
import chat.dim.utils.Log;

public class GateKeeper extends Runner implements Porter.Delegate {

    private final SocketAddress remoteAddress;
    private final CommonGate<StreamHub> gate;
    private final MessageQueue queue;
    private boolean active;
    private Date lastActive;  // last update time

    public GateKeeper(SocketAddress remote, SocketChannel sock) {
        super(Runner.INTERVAL_SLOW);
        remoteAddress = remote;
        gate = createGate(remote, sock);
        queue = new MessageQueue();
        active = false;
        lastActive = null;
    }

    protected CommonGate<StreamHub> createGate(SocketAddress remote, SocketChannel sock) {
        CommonGate streamGate;
        if (sock == null) {
            streamGate = new TCPClientGate(this);
        } else {
            streamGate = new TCPServerGate(this);
        }
        streamGate.setHub(createHub(streamGate, remote, sock));
        return streamGate;
    }

    protected StreamHub createHub(Connection.Delegate delegate, SocketAddress remote, SocketChannel sock) {
        if (sock == null) {
            // client
            assert remote != null : "remote address empty";
            StreamClientHub hub = new StreamClientHub(delegate);
            Connection conn = hub.connect(remote, null);
            assert conn != null : "failed to connect remote: " + remote;
            // TODO: reset send buffer size
            return hub;
        } else {
            // server
            SocketAddress local = null;
            try {
                sock.configureBlocking(false);
                local = sock.getLocalAddress();
                if (remote == null) {
                    remote = sock.getRemoteAddress();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            StreamChannel channel = new StreamChannel(remote, local);
            channel.setSocketChannel(sock);
            StreamServerHub hub = new StreamServerHub(delegate);
            hub.putChannel(channel);
            return hub;
        }
    }

    private static boolean resetSendBufferSize(SocketChannel channel) throws SocketException {
        return resetSendBufferSize(channel.socket());
    }
    private static boolean resetSendBufferSize(Socket socket) throws SocketException {
        int size = socket.getSendBufferSize();
        if (size < SEND_BUFFER_SIZE) {
            socket.setSendBufferSize(SEND_BUFFER_SIZE);
            return true;
        } else {
            return false;
        }
    }
    public static int SEND_BUFFER_SIZE = 64 * 1024;  // 64 KB

    public SocketAddress getRemoteAddress() {
        return remoteAddress;
    }

    public CommonGate<StreamHub> getGate() {
        return gate;
    }

    public boolean isActive() {
        return active;
    }
    public boolean setActive(boolean flag, Date when) {
        if (active == flag) {
            // flag not changed
            return false;
        }
        Date last = lastActive;
        if (when == null) {
            when = new Date();
        } else if (last != null && !when.after(last)) {
            return false;
        }
        active = flag;
        lastActive = when;
        return true;
    }

    private long reconnectTime = 0;

    @Override
    public boolean process() {
        // check docker for remote address
        Porter docker = gate.getPorter(remoteAddress, null);
        if (docker == null) {
            long now = System.currentTimeMillis();
            if (now < reconnectTime) {
                return false;
            }
            docker = gate.fetchPorter(remoteAddress, null);
            if (docker == null) {
                Log.error("gate error: " + remoteAddress);
                reconnectTime = now + 8000;
                return false;
            }
        }
        // try to process income/outgo packages
        try {
            Hub hub = gate.getHub();
            boolean incoming = hub.process();
            boolean outgoing = gate.process();
            if (incoming || outgoing) {
                // processed income/outgo packages
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        if (!isActive()) {
            // inactive, wait a while to check again
            queue.purge();
            return false;
        }
        // get next message
        MessageWrapper wrapper = queue.next();
        if (wrapper == null) {
            // no more task now, purge failed task
            queue.purge();
            return false;
        }
        // if msg in this wrapper is null (means sent successfully),
        // it must have bean cleaned already, so iit should not be empty here
        ReliableMessage msg = wrapper.getMessage();
        if (msg == null) {
            // msg sent?
            return true;
        }
        // try to push
        boolean ok = docker.sendShip(wrapper);
        if (!ok) {
            Log.error("docker error: " + remoteAddress + ", " + docker);
        }
        return true;
    }

    protected Departure dockerPack(byte[] payload, int priority) {
        Porter docker = gate.fetchPorter(remoteAddress, null);
        assert docker instanceof DeparturePacker : "departure packer error: " + docker;
        return ((DeparturePacker) docker).packData(payload, priority);
    }

    protected boolean queueAppend(ReliableMessage msg, Departure ship) {
        return queue.append(msg, ship);
    }

    //
    //  Docker.Delegate
    //

    @Override
    public void onPorterStatusChanged(Porter.Status previous, Porter.Status current, Porter docker) {
        Log.info("docker status changed: " + previous + " => " + current + ", " + docker);
    }

    @Override
    public void onPorterReceived(Arrival ship, Porter docker) {
        Log.debug("docker received a ship: " + ship + ", " + docker);
    }

    @Override
    public void onPorterSent(Departure ship, Porter docker) {
        // TODO: remove sent message from local cache
    }

    @Override
    public void onPorterFailed(IOError error, Departure ship, Porter docker) {
        Log.error("docker failed to send ship: " + ship + ", " + docker);
    }

    @Override
    public void onPorterError(IOError error, Departure ship, Porter docker) {
        Log.error("docker error while sending ship: " + ship + ", " + docker);
    }

}
