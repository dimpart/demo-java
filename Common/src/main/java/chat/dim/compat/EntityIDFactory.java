/* license: https://mit-license.org
 *
 *  Ming-Ke-Ming : Decentralized User Identity Authentication
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
package chat.dim.compat;

import chat.dim.mem.SharedAccountCache;
import chat.dim.mkm.IdentifierFactory;
import chat.dim.protocol.Address;
import chat.dim.protocol.ID;

public final class EntityIDFactory extends IdentifierFactory {

    /**
     * Call it when received 'UIApplicationDidReceiveMemoryWarningNotification',
     * this will remove 50% of cached objects
     *
     * @return number of survivors
     */
    public int reduceMemory() {
        return SharedAccountCache.idCache.reduceMemory();
    }

    @Override
    protected ID newID(String identifier, String name, Address address, String terminal) {
        return new EntityID(identifier, name, address, terminal);
    }

    @Override
    protected ID parse(String identifier) {
        if (identifier == null) {
            throw new NullPointerException("ID empty");
        }
        int size = identifier.length();
        if (size < 4 || size > 64) {
            assert false : "ID error: " + identifier;
            return null;
        } else if (size == 15) {
            // "anyone@anywhere"
            if (ID.ANYONE.equalsIgnoreCase(identifier)) {
                return ID.ANYONE;
            }
        } else if (size == 19) {
            // "everyone@everywhere"
            // "stations@everywhere"
            if (ID.EVERYONE.equalsIgnoreCase(identifier)) {
                return ID.EVERYONE;
            }
        } else if (size == 13) {
            // "moky@anywhere"
            if (ID.FOUNDER.equalsIgnoreCase(identifier)) {
                return ID.FOUNDER;
            }
        }
        return super.parse(identifier);
    }
}
