/* license: https://mit-license.org
 *
 *  Ming-Ke-Ming : Decentralized User Identity Authentication
 *
 *                                Written in 2024 by Moky <albert.moky@gmail.com>
 *
 * ==============================================================================
 * The MIT License (MIT)
 *
 * Copyright (c) 2024 Albert Moky
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

import chat.dim.core.Barrack;
import chat.dim.mkm.BTCAddress;
import chat.dim.mkm.BaseAddressFactory;
import chat.dim.mkm.ETHAddress;
import chat.dim.protocol.Address;

public class CompatibleAddressFactory extends BaseAddressFactory {

    /**
     * Call it when received 'UIApplicationDidReceiveMemoryWarningNotification',
     * this will remove 50% of cached objects
     *
     * @return number of survivors
     */
    public int reduceMemory() {
        int finger = 0;
        finger = Barrack.thanos(addresses, finger);
        return finger >> 1;
    }

    @Override
    protected Address parse(String address) {
        if (address == null) {
            //throw new NullPointerException("address empty");
            assert false : "address empty";
            return null;
        }
        int len = address.length();
        if (len == 0) {
            assert false : "address empty";
            return null;
        } else if (len == 8) {
            // "anywhere"
            if (Address.ANYWHERE.equalsIgnoreCase(address)) {
                return Address.ANYWHERE;
            }
        } else if (len == 10) {
            // "everywhere"
            if (Address.EVERYWHERE.equalsIgnoreCase(address)) {
                return Address.EVERYWHERE;
            }
        }
        Address res;
        if (26 <= len && len <= 35) {
            res = BTCAddress.parse(address);
        } else if (len == 42) {
            res = ETHAddress.parse(address);
        } else {
            //throw new AssertionError("invalid address: " + address);
            res = null;
        }
        //
        //  TODO: parse for other types of address
        //
        if (res == null && 4 <= len && len <= 64) {
            res = new UnknownAddress(address);
        }
        assert res != null : "invalid address: " + address;
        return res;
    }

}
