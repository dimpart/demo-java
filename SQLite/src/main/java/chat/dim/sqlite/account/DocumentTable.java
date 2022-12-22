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

import chat.dim.dbi.DocumentDBI;
import chat.dim.protocol.Document;
import chat.dim.protocol.ID;
import chat.dim.sql.SQLConditions;
import chat.dim.sqlite.Database;
import chat.dim.sqlite.ResultSetExtractor;

public class DocumentTable implements DocumentDBI {

    private final Database database;
    private ResultSetExtractor<Document> extractor;

    public DocumentTable(Database db) {
        database = db;
        extractor = null;
    }

    private void prepare() throws SQLException {
        if (extractor != null) {
            // already created
            return;
        }
        extractor = (resultSet, index) -> {
            String did = resultSet.getString("did");
            String type = resultSet.getString("type");
            String data = resultSet.getString("data");
            String signature = resultSet.getString("signature");

            Map<String, Object> info = new HashMap<>();
            info.put("ID", did);
            info.put("type", type);
            info.put("data", data);
            info.put("signature", signature);
            return Document.parse(info);
        };
        String[] fields = {
                "id INTEGER PRIMARY KEY AUTOINCREMENT",
                "did VARCHAR(64)",
                "type VARCHAR(8)",
                "data TEXT",
                "signature VARCHAR(88)",
        };
        database.createTable("t_document", fields);
    }

    @Override
    public boolean saveDocument(Document doc) {
        ID identifier = doc.getIdentifier();
        String type = doc.getType();
        String data = (String) doc.get("data");
        String signature = (String) doc.get("signature");

        Document old = getDocument(identifier, type);
        if (old == null) {
            // old record not found, insert it as new record
            String[] columns = {"did", "type", "data", "signature"};
            Object[] values = {identifier.toString(), type, data, signature};
            try {
                database.insert("t_document", columns, values);
            } catch (SQLException e) {
                e.printStackTrace();
                return false;
            }
        } else {
            // old record exists, update it
            SQLConditions conditions = new SQLConditions();
            conditions.addCondition(null, "did", "=", identifier.toString());
            Map<String, Object> values = new HashMap<>();
            values.put("type", type);
            values.put("data", data);
            values.put("signature", signature);
            try {
                database.update("t_document", values, conditions);
            } catch (SQLException e) {
                e.printStackTrace();
                return false;
            }
        }
        return true;
    }

    @Override
    public Document getDocument(ID entity, String type) {
        SQLConditions conditions = new SQLConditions();
        conditions.addCondition(null, "did", "=", entity.toString());
        String[] columns = {"did", "type", "data", "signature"};
        List<Document> results;
        try {
            prepare();
            results = database.select(columns, "t_document", conditions,
                    null, null, "id DESC", 0, extractor);
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
        return results == null || results.size() == 0 ? null : results.get(0);
    }
}
