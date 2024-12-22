/* license: https://mit-license.org
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

import chat.dim.format.Base64;
import chat.dim.format.DataCoder;
import chat.dim.plugins.PluginLoader;
import chat.dim.protocol.Address;
import chat.dim.protocol.ID;
import chat.dim.protocol.Meta;

public class CommonPluginLoader extends PluginLoader {

    @Override
    protected void registerBase64Coder() {
        // Base64 coding
        Base64.coder = new DataCoder() {

            @Override
            public String encode(byte[] data) {
                return java.util.Base64.getEncoder().encodeToString(data);
            }

            @Override
            public byte[] decode(String string) {
                string = string.replace(" ", "");
                string = string.replace("\t", "");
                string = string.replace("\r", "");
                string = string.replace("\n", "");
                return java.util.Base64.getDecoder().decode(string);
            }
        };
    }

    /**
     *  ID factory
     */
    @Override
    protected void registerIDFactory() {

        ID.setFactory(new EntityIDFactory());
    }

    /**
     *  Address factory
     */
    @Override
    protected void registerAddressFactory() {

        Address.setFactory(new CompatibleAddressFactory());
    }

    /**
     *  Meta factories
     */
    @Override
    protected void registerMetaFactories() {

        Meta.Factory mkm = new CompatibleMetaFactory(Meta.MKM);
        Meta.Factory btc = new CompatibleMetaFactory(Meta.BTC);
        Meta.Factory eth = new CompatibleMetaFactory(Meta.ETH);

        Meta.setFactory("1", mkm);
        Meta.setFactory("2", btc);
        Meta.setFactory("4", eth);

        Meta.setFactory("mkm", mkm);
        Meta.setFactory("btc", btc);
        Meta.setFactory("eth", eth);

        Meta.setFactory("MKM", mkm);
        Meta.setFactory("BTC", btc);
        Meta.setFactory("ETH", eth);
    }

}
