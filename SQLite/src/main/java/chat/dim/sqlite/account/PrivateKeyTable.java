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

import java.util.List;

import chat.dim.dbi.PrivateKeyDBI;
import chat.dim.format.JSON;
import chat.dim.protocol.DecryptKey;
import chat.dim.protocol.ID;
import chat.dim.protocol.PrivateKey;
import chat.dim.sql.SQLConditions;
import chat.dim.sqlite.DataRowExtractor;
import chat.dim.sqlite.DataTableHandler;
import chat.dim.sqlite.DatabaseConnector;

public class PrivateKeyTable extends DataTableHandler<PrivateKey> implements PrivateKeyDBI {

    private DataRowExtractor<PrivateKey> extractor;

    public PrivateKeyTable(DatabaseConnector connector) {
        super(connector);
        // lazy load
        extractor = null;
    }

    @Override
    protected DataRowExtractor<PrivateKey> getDataRowExtractor() {
        return extractor;
    }

    private boolean prepare() {
        if (extractor == null) {
            // create table if not exists
            String[] fields = {
                    "id INTEGER PRIMARY KEY AUTOINCREMENT",
                    "user VARCHAR(64)",
                    "pri_key TEXT",
                    "type CHAR(1)",
                    "sign BIT",
                    "decrypt BIT",
            };
            if (!createTable(T_PRIVATE_KEY, fields)) {
                // db error
                return false;
            }
            // prepare data row extractor
            extractor = (resultSet, index) -> {
                String json = resultSet.getString("pri_key");
                Object key = JSON.decode(json);
                return PrivateKey.parse(key);
            };
        }
        return true;
    }
    private static final String[] SELECT_COLUMNS = {"pri_key"};
    private static final String[] INSERT_COLUMNS = {"user", "pri_key", "type", "sign", "decrypt"};
    private static final String T_PRIVATE_KEY = "t_private_key";

    @Override
    public List<DecryptKey> getPrivateKeysForDecryption(ID user) {
        if (!prepare()) {
            // db error
            return null;
        }
        SQLConditions conditions = new SQLConditions();
        conditions.addCondition(null, "user", "=", user.toString());
        conditions.addCondition(SQLConditions.Relation.AND, "decrypt", "=", 1);
        // WHERE user='$user' AND decrypt=1 ORDER BY type DESC LIMIT 3
        List<PrivateKey> results = select(T_PRIVATE_KEY, SELECT_COLUMNS, conditions,
                null, null, "type DESC", 3, 0);
        if (results == null) {
            return null;
        }
        return PrivateKeyDBI.convertDecryptKeys(results);
    }

    @Override
    public PrivateKey getPrivateKeyForSignature(ID user) {
        // TODO: support multi private keys
        return getPrivateKeyForVisaSignature(user);
    }

    @Override
    public PrivateKey getPrivateKeyForVisaSignature(ID user) {
        if (!prepare()) {
            // db error
            return null;
        }
        SQLConditions conditions = new SQLConditions();
        conditions.addCondition(null, "user", "=", user.toString());
        conditions.addCondition(SQLConditions.Relation.AND, "type", "=", "M");
        conditions.addCondition(SQLConditions.Relation.AND, "sign", "=", 1);
        // WHERE user='$user' AND type='M' AND decrypt=1 ORDER BY id DESC  LIMIT 1
        List<PrivateKey> results = select(T_PRIVATE_KEY, SELECT_COLUMNS, conditions,
                null, null, "id DESC", 1, 0);
        // return first record only
        return results == null || results.size() == 0 ? null : results.get(0);
    }

    private boolean savePrivateKey(ID user, PrivateKey key, String type, int sign, int decrypt) {
        if (!prepare()) {
            // db error
            return false;
        }
        String json = JSON.encode(key);

        Object[] values = {user.toString(), json, type, sign, decrypt};
        return insert(T_PRIVATE_KEY, INSERT_COLUMNS, values) > 0;
    }

    @Override
    public boolean savePrivateKey(PrivateKey key, String type, ID user) {
        if (key instanceof DecryptKey) {
            return savePrivateKey(user, key, type, 1, 1);
        } else {
            return savePrivateKey(user, key, type, 1, 0);
        }
    }
}
