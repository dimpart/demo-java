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
import java.net.SocketAddress;

import chat.dim.mtp.MTPPacker;
import chat.dim.mtp.MTPStreamArrival;
import chat.dim.mtp.MTPStreamDocker;
import chat.dim.mtp.Package;
import chat.dim.mtp.TransactionID;
import chat.dim.net.Channel;
import chat.dim.net.Connection;
import chat.dim.net.ConnectionState;
import chat.dim.net.Hub;
import chat.dim.port.Arrival;
import chat.dim.port.Docker;
import chat.dim.type.Data;
import chat.dim.utils.Log;

/**
 *  Gate with hub for connection
 */
public abstract class CommonGate extends BaseGate implements Runnable {

    private boolean running;

    public CommonGate(Docker.Delegate delegate) {
        super(delegate);
        running = false;
    }

    public void start() {
        running = true;
    }
    public void stop() {
        running = false;
    }
    public boolean isRunning() {
        return running;
    }

    @Override
    public void run() {
        //running = true;
        while (isRunning()) {
            if (!process()) {
                idle();
            }
        }
        // gate closing
    }

    protected void idle() {
        idle(256);
    }

    public static void idle(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onConnectionStateChanged(ConnectionState previous, ConnectionState current, Connection connection) {
        super.onConnectionStateChanged(previous, current, connection);
        if (current == null || current.equals(ConnectionState.ERROR)) {
            Log.error("connection lost: " + previous + " => " + current + ", " + connection);
        } else if (!(current.equals(ConnectionState.EXPIRED) || current.equals(ConnectionState.MAINTAINING))) {
            Log.info("connection state changed: " + previous + " => " + current + ", " + connection);
        }
    }

    @Override
    public void onConnectionReceived(byte[] data, Connection connection) {
        super.onConnectionReceived(data, connection);
        Log.debug("received " + data.length + " byte(s): " + connection);
    }

    @Override
    public void onConnectionSent(int sent, byte[] data, Connection connection) {
        super.onConnectionSent(sent, data, connection);
        Log.debug("sent " + sent + "/" + data.length + " byte(s): " + connection);
    }

    @Override
    public void onConnectionFailed(IOError error, byte[] data, Connection connection) {
        super.onConnectionFailed(error, data, connection);
        Log.error("failed to send " + data.length + " byte(s): " + error + ", " + connection);
    }

    @Override
    public void onConnectionError(IOError error, Connection connection) {
        super.onConnectionError(error, connection);
        String errMsg = error.getMessage();
        if (errMsg != null && errMsg.startsWith("failed to send: ")) {
            Log.warning("ignore socket error: " + error + ", " + connection);
        }
    }

    public Channel getChannel(SocketAddress remote, SocketAddress local) {
        Hub hub = getHub();
        assert hub != null : "no hub for channel: " + remote + ", " + local;
        return hub.open(remote, local);
    }

    public boolean sendResponse(byte[] payload, Arrival ship, SocketAddress remote, SocketAddress local) {
        assert ship instanceof MTPStreamArrival : "arrival ship error: " + ship;
        MTPStreamArrival arrival = (MTPStreamArrival) ship;
        Docker docker = getDocker(remote, local, null);
        assert docker instanceof MTPStreamDocker : "docker error: " + docker;
        MTPStreamDocker worker = (MTPStreamDocker) docker;
        TransactionID sn = TransactionID.from(new Data(arrival.getSN()));
        Package pack = MTPPacker.createMessage(sn, new Data(payload));
        return worker.sendPackage(pack);
    }
}
