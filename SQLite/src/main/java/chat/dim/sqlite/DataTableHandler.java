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
package chat.dim.sqlite;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import chat.dim.sql.SQLBuilder;
import chat.dim.sql.SQLConditions;

public class DataTableHandler extends DatabaseHandler {

    public DataTableHandler(DatabaseConnector sqliteConnector) {
        super(sqliteConnector);
    }

    public boolean createTable(String table,
                               String[] fields) {
        // CREATE TABLE table (field type, ...);
        String sql = SQLBuilder.buildCreateTable(table, fields);
        try {
            executeUpdate(sql);
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public int insert(String table,
                      String[] columns,
                      Object[] values) {
        // INSERT INTO table (columns) VALUES (values);
        String sql = SQLBuilder.buildInsert(table, columns, values);
        try {
            return executeUpdate(sql);
        } catch (SQLException e) {
            e.printStackTrace();
            return -1;
        }
    }

    public <T> List<T> select(String[] columns,
                              String table,
                              SQLConditions conditions,
                              int limit,
                              ResultSetExtractor<T> extractor) {
        // SELECT DISTINCT columns FROM tables WHERE conditions ...
        String sql = SQLBuilder.buildSelect(columns, table, conditions, null, null, null, limit);
        try {
            return executeQuery(sql, extractor);
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    public <T> List<T> select(String[] columns,
                              String table,
                              SQLConditions conditions,
                              String groupBy,
                              String having,
                              String orderBy,
                              int limit,
                              ResultSetExtractor<T> extractor) {
        // SELECT DISTINCT columns FROM tables WHERE conditions ...
        String sql = SQLBuilder.buildSelect(columns, table, conditions, groupBy, having, orderBy, limit);
        try {
            return executeQuery(sql, extractor);
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    public int update(String table,
                      Map<String, Object> values,
                      SQLConditions conditions) {
        // UPDATE table SET name=value WHERE conditions
        String sql = SQLBuilder.buildUpdate(table, values, conditions);
        try {
            return executeUpdate(sql);
        } catch (SQLException e) {
            e.printStackTrace();
            return -1;
        }
    }

    public int delete(String table,
                      SQLConditions conditions) {
        // DELETE FROM table WHERE conditions
        String sql = SQLBuilder.buildDelete(table, conditions);
        try {
            return executeUpdate(sql);
        } catch (SQLException e) {
            e.printStackTrace();
            return -1;
        }
    }
}
