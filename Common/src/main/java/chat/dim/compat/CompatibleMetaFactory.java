/* license: https://mit-license.org
 *
 *  Ming-Ke-Ming : Decentralized User Identity Authentication
 *
 *                                Written in 2020 by Moky <albert.moky@gmail.com>
 *
 * ==============================================================================
 * The MIT License (MIT)
 *
 * Copyright (c) 2020 Albert Moky
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

import java.util.Map;

import chat.dim.ext.SharedAccountExtensions;
import chat.dim.mkm.BTCMeta;
import chat.dim.mkm.BaseMetaFactory;
import chat.dim.mkm.DefaultMeta;
import chat.dim.mkm.ETHMeta;
import chat.dim.protocol.Meta;

public final class CompatibleMetaFactory extends BaseMetaFactory {

    public CompatibleMetaFactory(String algorithm) {
        super(algorithm);
    }

    @Override
    public Meta parseMeta(Map<String, Object> info) {
        // check 'type', 'key', 'seed', 'fingerprint'
        if (info.get("type") == null || info.get("key") == null) {
            // meta.type should not be empty
            // meta.key should not be empty
            assert false : "meta error: " + info;
            return null;
        } else if (info.get("seed") == null) {
            if (info.get("fingerprint") != null) {
                assert false : "meta error: " + info;
                return null;
            }
        } else if (info.get("fingerprint") == null) {
            assert false : "meta error: " + info;
            return null;
        }

        // create meta for type
        Meta out;
        String type = SharedAccountExtensions.helper.getMetaType(info, "");
        switch (type) {

            case "MKM":
            case "mkm":
            case "1":
                out = new DefaultMeta(info);
                break;

            case "BTC":
            case "btc":
            case "2":
                out = new BTCMeta(info);
                break;

            case "ETH":
            case "eth":
            case "4":
                out = new ETHMeta(info);
                break;

            default:
                throw new IllegalArgumentException("unknown meta type: " + type);
        }
        return out.isValid() ? out : null;
    }
}
