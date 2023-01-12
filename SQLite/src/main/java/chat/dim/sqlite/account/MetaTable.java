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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import chat.dim.dbi.MetaDBI;
import chat.dim.format.JSON;
import chat.dim.protocol.ID;
import chat.dim.protocol.Meta;
import chat.dim.protocol.MetaType;
import chat.dim.sql.SQLConditions;
import chat.dim.sqlite.DataRowExtractor;
import chat.dim.sqlite.DataTableHandler;
import chat.dim.sqlite.DatabaseConnector;

public class MetaTable extends DataTableHandler<Meta> implements MetaDBI {

    private DataRowExtractor<Meta> extractor;

    public MetaTable(DatabaseConnector connector) {
        super(connector);
        // lazy load
        extractor = null;
    }

    @Override
    protected DataRowExtractor<Meta> getDataRowExtractor() {
        return extractor;
    }

    private boolean prepare() {
        if (extractor == null) {
            // create table if not exists
            String[] fields = {
                    "id INTEGER PRIMARY KEY AUTOINCREMENT",
                    "did VARCHAR(64)",
                    "type INTEGER",
                    "pub_key TEXT",
                    "seed VARCHAR(20)",
                    "fingerprint VARCHAR(88)",
            };
            if (!createTable(T_META, fields)) {
                // db error
                return false;
            }
            // prepare data row extractor
            extractor = (resultSet, index) -> {
                int type = resultSet.getInt("type");
                String json = resultSet.getString("pub_key");
                Object key = JSON.decode(json);

                Map<String, Object> info = new HashMap<>();
                info.put("version", type);
                info.put("type", type);
                info.put("key", key);
                if (MetaType.hasSeed(type)) {
                    String seed = resultSet.getString("seed");
                    String fingerprint = resultSet.getString("fingerprint");
                    info.put("seed", seed);
                    info.put("fingerprint", fingerprint);
                }
                return Meta.parse(info);
            };
        }
        return true;
    }
    private static final String[] SELECT_COLUMNS = {"type", "pub_key", "seed", "fingerprint"};
    private static final String[] INSERT_COLUMNS =  {"did", "type", "pub_key", "seed", "fingerprint"};
    private static final String T_META = "t_meta";

    @Override
    public Meta getMeta(ID entity) {
        if (!prepare()) {
            // db error
            return null;
        }
        SQLConditions conditions = new SQLConditions();
        conditions.addCondition(null, "did", "=", entity.toString());
        List<Meta> results = select(T_META, SELECT_COLUMNS, conditions,
                null, null, "id DESC", -1, 0);
        // return first record only
        return results == null || results.size() == 0 ? null : results.get(0);
    }

    @Override
    public boolean saveMeta(Meta meta, ID entity) {
        if (!prepare()) {
            // db error
            return false;
        }
        // make sure old records not exists
        Meta old = getMeta(entity);
        if (old != null) {
            // meta info won't changed, no need to update
            return false;
        }

        int type = meta.getType();
        String json = JSON.encode(meta.getKey());
        String seed;
        String fingerprint;
        if (MetaType.hasSeed(type)) {
            seed = meta.getSeed();
            fingerprint = meta.getString("fingerprint");
        } else {
            seed = "";
            fingerprint = "";
        }

        Object[] values = {entity.toString(), type, json, seed, fingerprint};
        return insert(T_META, INSERT_COLUMNS, values) > 0;
    }
}
