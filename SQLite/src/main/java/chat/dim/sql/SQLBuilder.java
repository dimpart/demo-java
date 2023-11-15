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
package chat.dim.sql;

import java.util.Map;

public final class SQLBuilder {

    public static final String CREATE = "CREATE";
    public static final String ALTER = "ALTER";

    public static final String INSERT = "INSERT";
    public static final String SELECT = "SELECT";
    public static final String UPDATE = "UPDATE";
    public static final String DELETE = "DELETE";

    private final StringBuilder sb = new StringBuilder(128);

    public SQLBuilder(String sql) {
        append(sql);
    }

    @Override
    public String toString() {
        return sb.toString();
    }

    private void append(String str) {
        sb.append(str);
    }

    private void appendStringList(String[] array) {
        SQLValues.appendStringList(sb, array);
    }

    private void appendEscapeValueList(Object[] array) {
        SQLValues.appendEscapeValueList(sb, array);
    }

    private void appendValues(SQLValues values) {
        values.appendValues(sb);
    }

    //  SELECT *       ...
    //  SELECT columns ...
    private void appendColumns(String[] columns) {
        if (columns == null || columns.length == 0) {
            append(" *");
        } else {
            append(" ");
            appendStringList(columns);
        }
    }

    private void appendClause(String name, String clause) {
        if (clause == null || clause.length() == 0) {
            return;
        }
        append(name);
        append(clause);
    }

    private void appendWhere(SQLConditions conditions) {
        if (conditions == null) {
            return;
        }
        append(" WHERE ");
        conditions.appendEscapeValue(sb);
    }

    //
    //  CREATE TABLE IF NOT EXISTS table (field type, ...);
    //
    public static String buildCreateTable(String table, String[] fields) {
        SQLBuilder builder = new SQLBuilder(CREATE);
        builder.append(" TABLE IF NOT EXISTS ");
        builder.append(table);
        builder.append("(");
        builder.appendStringList(fields);
        builder.append(")");
        return builder.toString();
    }

    //
    //  CREATE INDEX IF NOT EXISTS name ON table (fields);
    //
    public static String buildCreateIndex(String name, String table, String[] fields) {
        SQLBuilder builder = new SQLBuilder(CREATE);
        builder.append(" INDEX IF NOT EXISTS ");
        builder.append(name);
        builder.append(" ON ");
        builder.append(table);
        builder.append("(");
        builder.appendStringList(fields);
        builder.append(")");
        return builder.toString();
    }

    //
    //  ALTER TABLE table ADD COLUMN IF NOT EXISTS name type;
    //
    public static String buildAddColumn(String table, String name, String type) {
        SQLBuilder builder = new SQLBuilder(ALTER);
        builder.append(" TABLE ");
        builder.append(table);
        // builder.append(" ADD COLUMN IF NOT EXISTS ");
        builder.append(" ADD COLUMN ");
        builder.append(name);
        builder.append(" ");
        builder.append(type);
        return builder.toString();
    }

    //
    //  DROP TABLE IF EXISTS table;
    //

    //
    //  INSERT INTO table (columns) VALUES (values);
    //
    public static String buildInsert(String table, String[] columns, Object[] values) {
        SQLBuilder builder = new SQLBuilder(INSERT);
        builder.append(" INTO ");
        builder.append(table);
        builder.append("(");
        builder.appendStringList(columns);
        builder.append(") VALUES (");
        builder.appendEscapeValueList(values);
        builder.append(")");
        return builder.toString();
    }

    //
    //  SELECT DISTINCT columns FROM tables WHERE conditions
    //          GROUP BY ...
    //          HAVING ...
    //          ORDER BY ...
    //          LIMIT count OFFSET start;
    //
    public static String buildSelect(boolean distinct, String[] columns,
                                     String table, SQLConditions conditions,
                                     String groupBy, String having, String orderBy,
                                     int limit, int offset) {
        SQLBuilder builder = new SQLBuilder(SELECT);
        if (distinct) {
            builder.append(" DISTINCT");
        }
        builder.appendColumns(columns);
        builder.append(" FROM ");
        builder.append(table);
        builder.appendWhere(conditions);
        builder.appendClause(" GROUP BY ", groupBy);
        builder.appendClause(" HAVING ", having);
        builder.appendClause(" ORDER BY ", orderBy);
        if (limit > 0) {
            builder.appendClause(" LIMIT ", String.valueOf(limit));
            builder.appendClause(" OFFSET ", String.valueOf(offset));
        }
        return builder.toString();
    }

    //
    //  UPDATE table SET name=value WHERE conditions
    //
    public static String buildUpdate(String table, Map<String, Object> values, SQLConditions conditions) {
        SQLBuilder builder = new SQLBuilder(UPDATE);
        builder.append(" ");
        builder.append(table);
        builder.append(" SET ");
        builder.appendValues(SQLValues.from(values));
        builder.appendWhere(conditions);
        return builder.toString();
    }

    //
    //  DELETE FROM table WHERE conditions
    //
    public static String buildDelete(String table, SQLConditions conditions) {
        SQLBuilder builder = new SQLBuilder(DELETE);
        builder.append(" FROM ");
        builder.append(table);
        builder.appendWhere(conditions);
        return builder.toString();
    }
}
