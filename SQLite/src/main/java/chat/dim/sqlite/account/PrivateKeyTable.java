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
import java.util.ArrayList;
import java.util.List;

import chat.dim.crypto.DecryptKey;
import chat.dim.crypto.PrivateKey;
import chat.dim.dbi.PrivateKeyDBI;
import chat.dim.format.JSON;
import chat.dim.protocol.ID;
import chat.dim.sql.SQLConditions;
import chat.dim.sqlite.Database;
import chat.dim.sqlite.ResultSetExtractor;

public class PrivateKeyTable implements PrivateKeyDBI {

    private final Database database;
    private ResultSetExtractor<PrivateKey> extractor;

    public PrivateKeyTable(Database db) {
        database = db;
        extractor = null;
    }

    private void prepare() throws SQLException {
        if (extractor != null) {
            // already created
            return;
        }
        extractor = (resultSet, index) -> {
            String sk = resultSet.getString("key");
            Object key = JSON.decode(sk);
            return PrivateKey.parse(key);
        };
        String[] fields = {
                "id INT PRIMARY KEY AUTOINCREMENT",
                "user VARCHAR(64)",
                "key TEXT",
                "type CHAR(1)",
                "sign BIT",
                "decrypt BIT",
        };
        database.createTable("t_private_key", fields);
    }

    private boolean savePrivateKey(ID user, PrivateKey key, String type, int sign, int decrypt) {
        String pk = JSON.encode(key);

        String[] columns = {"user", "key", "type", "sign", "decrypt"};
        Object[] values = {user.toString(), pk, type, sign, decrypt};
        try {
            prepare();
            database.insert("t_private_key", columns, values);
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public boolean savePrivateKey(PrivateKey key, String type, ID user) {
        if (key instanceof DecryptKey) {
            return savePrivateKey(user, key, type, 1, 1);
        } else {
            return savePrivateKey(user, key, type, 1, 0);
        }
    }

    @Override
    public List<DecryptKey> getPrivateKeysForDecryption(ID user) {
        SQLConditions conditions = new SQLConditions();
        conditions.addCondition(null, "user", "=", user.toString());
        conditions.addCondition(SQLConditions.Relation.AND, "decrypt", "=", 1);
        String[] columns = {"key"};
        List<PrivateKey> results;
        try {
            prepare();
            results = database.select(columns, "t_private_key", conditions,
                    null, null, "type DESC", 0, extractor);
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
        List<DecryptKey> keys = new ArrayList<>();
        for (PrivateKey item : results) {
            if (item instanceof DecryptKey) {
                keys.add((DecryptKey) item);
            }
        }
        return keys;
    }

    @Override
    public PrivateKey getPrivateKeyForSignature(ID user) {
        // TODO: support multi private keys
        return getPrivateKeyForVisaSignature(user);
    }

    @Override
    public PrivateKey getPrivateKeyForVisaSignature(ID user) {
        SQLConditions conditions = new SQLConditions();
        conditions.addCondition(null, "user", "=", user.toString());
        conditions.addCondition(SQLConditions.Relation.AND, "type", "=", "M");
        conditions.addCondition(SQLConditions.Relation.AND, "sign", "=", 1);
        String[] columns = {"key"};
        List<PrivateKey> results;
        try {
            prepare();
            results = database.select(columns, "t_private_key", conditions,
                    null, null, "type DESC", 0, extractor);
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
        return results == null || results.size() == 0 ? null : results.get(0);
    }
}
