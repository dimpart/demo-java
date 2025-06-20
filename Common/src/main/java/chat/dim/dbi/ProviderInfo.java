/* license: https://mit-license.org
 *
 *  DIMP : Decentralized Instant Messaging Protocol
 *
 *                                Written in 2023 by Moky <albert.moky@gmail.com>
 *
 * ==============================================================================
 * The MIT License (MIT)
 *
 * Copyright (c) 2023 Albert Moky
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
package chat.dim.dbi;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import chat.dim.TwinsHelper;
import chat.dim.mkm.Identifier;
import chat.dim.protocol.Address;
import chat.dim.protocol.ID;
import chat.dim.type.Converter;

public class ProviderInfo {

    // default service provider
    public static ID GSP = Identifier.create("gsp", Address.EVERYWHERE, null);

    public final ID identifier;
    public int chosen;

    public ProviderInfo(ID identifier, int chosen) {
        super();
        this.identifier = identifier;
        this.chosen = chosen;
    }

    @Override
    public String toString() {
        String clazz = getClass().getName();
        return "<" + clazz + " ID=\"" + identifier.toString() + "\" chosen=" + chosen + " />";
    }

    //
    //  Conveniences
    //

    public static List<ProviderInfo> convert(Iterable<Map<String, Object>> array) {
        List<ProviderInfo> providers = new ArrayList<>();
        ID identifier;
        int chosen;
        for (Map<String, Object> item : array) {
            identifier = ID.parse(item.get("did"));
            if (identifier == null) {
                identifier = ID.parse(item.get("ID"));
            }
            chosen = Converter.getInt(item.get("chosen"), 0);
            if (identifier == null) {
                // SP ID error
                continue;
            }
            providers.add(new ProviderInfo(identifier, chosen));
        }
        return providers;
    }

    public static List<Map<String, Object>> revert(Iterable<ProviderInfo> providers) {
        List<Map<String, Object>> array = new ArrayList<>();
        for (ProviderInfo info : providers) {
            array.add(TwinsHelper.newMap(
                    "ID", info.identifier.toString(),
                    "did", info.identifier.toString(),
                    "chosen", info.chosen
            ));
        }
        return array;
    }
}
