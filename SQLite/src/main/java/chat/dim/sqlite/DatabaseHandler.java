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

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class DatabaseHandler<T> {

    private final DatabaseConnector connector;
    private Statement statement;
    private ResultSet resultSet;

    public DatabaseHandler(DatabaseConnector sqliteConnector) {
        super();
        connector = sqliteConnector;
        statement = null;
        resultSet = null;
    }

    @Override
    protected void finalize() throws Throwable {
        destroy();
        super.finalize();
    }

    public void destroy() throws SQLException {
        Statement stat = statement;
        if (stat != null) {
            statement = null;
            stat.close();
        }
        ResultSet result = resultSet;
        if (result != null) {
            resultSet = null;
            result.close();
        }
    }

    public Connection getConnection() throws SQLException {
        return connector.getConnection();
    }
    public Statement getStatement() throws SQLException {
        Statement stat = statement;
        if (stat == null) {
            stat = getConnection().createStatement();
            statement = stat;
        }
        return stat;
    }

    /**
     *  Query (SELECT)
     *
     * @param sql       - SQL
     * @param extractor - result extractor
     * @return rows
     * @throws SQLException on DB error
     */
    public List<T> executeQuery(String sql, DataRowExtractor<T> extractor) throws SQLException {
        List<T> rows = new ArrayList<>();
        try {
            resultSet = getStatement().executeQuery(sql);
            while (resultSet.next()) {
                rows.add(extractor.extractRow(resultSet, resultSet.getRow()));
            }
        } finally {
            destroy();
        }
        return rows;
    }

    /**
     *  Update (INSERT, UPDATE, DELETE)
     *
     * @param sql - SQL
     * @return result
     * @throws SQLException on DB error
     */
    public int executeUpdate(String sql) throws SQLException {
        try {
            return getStatement().executeUpdate(sql);
        } finally {
            destroy();
        }
    }
    public void executeUpdate(String... sqlList) throws SQLException {
        try {
            for (String sql : sqlList) {
                getStatement().executeUpdate(sql);
            }
        } finally {
            destroy();
        }
    }

    static {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }
}
