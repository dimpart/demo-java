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
package chat.dim.mtp;

import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import chat.dim.net.Connection;
import chat.dim.pack.DeparturePacker;
import chat.dim.port.Arrival;
import chat.dim.port.Departure;
import chat.dim.startrek.DepartureShip;
import chat.dim.type.ByteArray;
import chat.dim.type.Data;
import chat.dim.type.Pair;
import chat.dim.utils.Log;

/**
 *  Docker for MTP packages
 */
public class MTPStreamDocker extends PackageDocker implements DeparturePacker {

    private ByteArray chunks = Data.ZERO;
    private final ReadWriteLock chunksLock = new ReentrantReadWriteLock();
    private boolean packageReceived = false;

    public MTPStreamDocker(Connection conn) {
        super(conn);
    }

    @Override
    protected Package parsePackage(byte[] data) {
        Package pack;
        Lock writeLock = chunksLock.writeLock();
        writeLock.lock();
        try {
            // join the data to the memory cache
            ByteArray buffer = chunks.concat(data);
            chunks = Data.ZERO;
            // try to fetch a package
            Pair<Package, Integer> result = MTPPacker.seekPackage(buffer);
            pack = result.first;
            int offset = result.second;
            packageReceived = pack != null;
            if (offset >= 0) {
                // 'error part' + 'MTP package' + 'remaining data'
                if (pack != null) {
                    offset += pack.getSize();
                }
                if (offset == 0) {
                    chunks = buffer.concat(chunks);
                } else if (offset < buffer.getSize()) {
                    buffer = buffer.slice(offset);
                    chunks = buffer.concat(chunks);
                }
            }
        } finally {
            writeLock.unlock();
        }
        return pack;
    }

    @Override
    public void processReceived(byte[] data) {
        // the cached data maybe contain sticky packages,
        // so we need to process them circularly here
        packageReceived = true;
        while (packageReceived) {
            packageReceived = false;
            super.processReceived(data);
            data = new byte[0];
        }
    }

    @Override
    protected Arrival checkArrival(Arrival income) {
        assert income instanceof MTPStreamArrival : "arrival ship error: " + income;
        MTPStreamArrival ship = (MTPStreamArrival) income;
        Package pack = ship.getPackage();
        if (pack == null) {
            List<Package> fragments = ship.getFragments();
            int count = fragments.size();
            assert count > 0 : "fragments empty: " + ship;
            pack = fragments.get(count - 1);
        }
        Header head = pack.head;
        // check body length
        if (head.bodyLength != pack.body.getSize()) {
            // sticky data?
            Log.warning("[MTP] package not completed: body_len=" + pack.body.getSize() + ", " + pack);
            return ship;
        }
        // check for response
        return super.checkArrival(income);
    }

    @Override
    protected Arrival createArrival(Package pkg) {
        return new MTPStreamArrival(pkg);
    }

    @Override
    protected Departure createDeparture(Package pkg, int priority) {
        if (pkg.isResponse()) {
            // response package needs no response again,
            // so this ship will be removed immediately after sent.
            return new MTPStreamDeparture(pkg, priority, DepartureShip.DISPOSABLE);
        } else {
            // normal package
            return new MTPStreamDeparture(pkg, priority);
        }
    }

    //
    //  Packing
    //


    @Override
    protected Package createCommand(byte[] body) {
        return MTPPacker.createCommand(new Data(body));
    }

    @Override
    protected Package createMessage(byte[] body) {
        return MTPPacker.createMessage(null, new Data(body));
    }

    @Override
    protected Package createCommandResponse(TransactionID sn, byte[] body) {
        return MTPPacker.respondCommand(sn, new Data(body));
    }

    @Override
    protected Package createMessageResponse(TransactionID sn, int pages, int index) {
        return MTPPacker.respondMessage(sn, pages, index, new Data(OK));
    }

    @Override
    public Departure packData(byte[] payload, int priority) {
        Package pack = MTPPacker.createMessage(null, new Data(payload));
        return createDeparture(pack, priority);
    }

    public static boolean check(ByteArray data) {
        Pair<Header, Integer> result = MTPPacker.seekHeader(data);
        return result.first != null;
    }
}
