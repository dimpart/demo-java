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
package chat.dim.sqlite.account;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import chat.dim.dbi.MetaDBI;
import chat.dim.format.JSON;
import chat.dim.protocol.ID;
import chat.dim.protocol.Meta;
import chat.dim.protocol.MetaType;
import chat.dim.sql.SQLConditions;
import chat.dim.sqlite.Database;
import chat.dim.sqlite.ResultSetExtractor;

public class MetaTable implements MetaDBI {

    private final Database database;
    private ResultSetExtractor<Meta> extractor;

    public MetaTable(Database db) {
        database = db;
        extractor = null;
    }

    private void prepare() throws SQLException {
        if (extractor != null) {
            // already created
            return;
        }
        extractor = (resultSet, index) -> {
            int type = resultSet.getInt("type");
            String pk = resultSet.getString("key");
            Object key = JSON.decode(pk);

            Map<String, Object> info = new HashMap<>();
            info.put("version", type);
            info.put("type", type);
            info.put("key", key);
            if (MetaType.hasSeed(type)) {
                String seed = resultSet.getString("seed");
                String ct = resultSet.getString("fingerprint");
                info.put("seed", seed);
                info.put("fingerprint", ct);
            }
            return Meta.parse(info);
        };
        String[] fields = {
                "id INTEGER PRIMARY KEY AUTOINCREMENT",
                "did VARCHAR(64)",
                "type INTEGER",
                "key TEXT",
                "seed VARCHAR(20)",
                "fingerprint VARCHAR(88)",
        };
        database.createTable("t_meta", fields);
    }

    @Override
    public boolean saveMeta(Meta meta, ID entity) {
        // make sure old records not exists
        Meta old = getMeta(entity);
        if (old != null) {
            // meta info won't changed, no need to update
            return false;
        }

        int type = meta.getType();
        String pk = JSON.encode(meta.getKey());
        String seed;
        String ct;
        if (MetaType.hasSeed(type)) {
            seed = meta.getSeed();
            ct = (String) meta.get("fingerprint");
        } else {
            seed = "";
            ct = "";
        }

        String[] columns = {"did", "type", "key", "seed", "fingerprint"};
        Object[] values = {entity.toString(), type, pk, seed, ct};
        try {
            database.insert("t_meta", columns, values);
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public Meta getMeta(ID entity) {
        SQLConditions conditions = new SQLConditions();
        conditions.addCondition(null, "did", "=", entity.toString());
        String[] columns = {"type", "key", "seed", "fingerprint"};
        List<Meta> results;
        try {
            prepare();
            results = database.select(columns, "t_meta", conditions,
                    null, null, "id DESC", 0, extractor);
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
        return results == null || results.size() == 0 ? null : results.get(0);
    }
}
