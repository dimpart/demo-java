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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import chat.dim.mkm.Station;
import chat.dim.protocol.ID;
import chat.dim.type.Converter;

public class StationInfo {

    public ID identifier;
    public String host;
    public int port;
    public ID provider;
    public int chosen;

    public StationInfo(ID identifier, String host, int port, ID provider, int chosen) {
        super();
        if (identifier == null) {
            identifier = Station.ANY;  // 'station@anywhere'
        }
        this.identifier = identifier;
        this.host = host;
        this.port = port;
        this.provider = provider;
        this.chosen = chosen;
    }

    public static List<StationInfo> convert(List<Map<String, Object>> array) {
        List<StationInfo> stations = new ArrayList<>();
        ID identifier;
        String host;
        int port;
        ID provider;
        int chosen;
        for (Map<String, Object> item : array) {
            identifier = ID.parse(item.get("ID"));
            host = Converter.getString(item.get("host"), null);
            port = Converter.getInt(item.get("port"), 0);
            provider = ID.parse(item.get("provider"));
            chosen = Converter.getInt(item.get("chosen"), 0);
            if (host == null || port == 0/* || provider == null*/) {
                // station socket error
                continue;
            }
            stations.add(new StationInfo(identifier, host, port, provider, chosen));
        }
        return stations;
    }

    public static List<Map<String, Object>> revert(List<StationInfo> stations) {
        List<Map<String, Object>> array = new ArrayList<>();
        for (StationInfo info : stations) {
            array.add(new HashMap<String, Object>() {{
                put("ID", info.identifier.toString());
                put("host", info.host);
                put("port", info.port);
                if (info.provider != null) {
                    put("provider", info.provider.toString());
                }
                put("chosen", info.chosen);
            }});
        }
        return array;
    }
}
