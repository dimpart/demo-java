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
package chat.dim.utils;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import chat.dim.protocol.ID;

/**
 *  Config info from ini file
 */
public class Config {

    private final ConfigParser parser;

    public Config(ConfigParser configParser) {
        super();
        parser = configParser;
    }

    @Override
    public String toString() {
        return parser.toString();
    }

    public void setItems(String section, Map<String, String> items) {
        parser.setItems(section, items);
    }
    public Map<String, String> getItems(String section) {
        return parser.getItems(section);
    }

    public ID getID(String section, String option) {
        String value = parser.getString(section, option);
        return ID.parse(value);
    }
    public String getString(String section, String option) {
        return parser.getString(section, option);
    }
    public int getInt(String section, String option) {
        return parser.getInt(section, option);
    }
    public boolean getBoolean(String section, String option) {
        return parser.getBoolean(section, option);
    }

    //
    //  database
    //
    public String getDatabaseRoot() {
        String path = getString("database", "root");
        if (path == null) {
            return "/var/.dim";
        } else {
            return path;
        }
    }
    public String getDatabasePublic() {
        String path = getString("database", "public");
        if (path == null) {
            return getDatabaseRoot() + "/public";  // public: "/var/.dim/public"
        } else {
            return path;
        }
    }
    public String getDatabasePrivate() {
        String path = getString("database", "private");
        if (path == null) {
            return getDatabaseRoot() + "/private";  // public: "/var/.dim/private"
        } else {
            return path;
        }
    }

    //
    //  station
    //
    public ID getStationID() {
        return getID("station", "id");
    }
    public String getStationHost() {
        String host = getString("station", "host");
        return host == null ? "127.0.0.1" : host;
    }
    public int getStationPort() {
        int port = getInt("section", "port");
        return port > 0 ? port : 9394;
    }

    //
    //  ans
    //
    public ID getANS(String alias) {
        return getID("ans", alias);
    }
    public Map<String, ID> getANSRecords() {
        Map<String, String> records = parser.getItems("ans");
        if (records == null) {
            return new HashMap<>();
        } else {
            return parseANS(records);
        }
    }
    private Map<String, ID> parseANS(Map<String, String> records) {
        Map<String, ID> ans = new HashMap<>();
        Iterator<Map.Entry<String, String>> iterator = records.entrySet().iterator();
        Map.Entry<String, String> entry;
        while (iterator.hasNext()) {
            entry = iterator.next();
            ans.put(entry.getKey(), ID.parse(entry.getValue()));
        }
        return ans;
    }


    //
    //  Factory method
    //

    public static Config load(String iniFileContent) {
        ConfigParser parser = new ConfigParser();
        parser.parse(iniFileContent);
        return new Config(parser);
    }
}
