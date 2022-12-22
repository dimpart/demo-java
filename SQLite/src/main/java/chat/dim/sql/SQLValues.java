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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import chat.dim.type.Pair;

public final class SQLValues {

    private final List<Pair<String, Object>> valueList = new ArrayList<>();

    public void setValue(String name, Object value) {
        Pair<String, Object> pair;
        int index;
        for (index = valueList.size() - 1; index >= 0; --index) {
            pair = valueList.get(index);
            if (name.equals(pair.first)) {
                break;
            }
        }
        pair = new Pair<>(name, value);
        if (index < 0) {
            valueList.add(pair);
        } else {
            valueList.set(index, pair);
        }
    }

    void appendValues(StringBuilder sb) {
        for (Pair<String, Object> pair : valueList) {
            //appendEscapeString(sb, pair.first);
            sb.append(pair.first);
            sb.append("=");
            appendEscapeValue(sb, pair.second);
            sb.append(",");
        }
        sb.deleteCharAt(sb.length() - 1);  // remove last ','
    }

    static void appendEscapeValue(StringBuilder sb, Object value) {
        // TODO: other types?
        if (value instanceof Number) {
            sb.append(value);
        } else if (value instanceof String) {
            SQLValues.appendEscapeString(sb, (String) value);
        } else {
            SQLValues.appendEscapeString(sb, value.toString());
        }
    }

    private static void appendEscapeString(StringBuilder sb, String sql) {
        sb.append('\'');
        if (sql.indexOf('\'') != -1) {
            int index, count = sql.length();
            char ch;
            for (index = 0; index < count; ++index) {
                ch = sql.charAt(index);
                if (ch == '\'') {
                    sb.append('\'');
                }
                sb.append(ch);
            }
        } else {
            sb.append(sql);
        }
        sb.append('\'');
    }

    static void appendEscapeValueList(StringBuilder sb, Object[] array) {
        for (Object item : array) {
            appendEscapeValue(sb, item);
            sb.append(",");
        }
        sb.deleteCharAt(sb.length() - 1);  // remove last ','
    }

    static void appendStringList(StringBuilder sb, String[] array) {
        for (String item : array) {
            sb.append(item);
            sb.append(",");
        }
        sb.deleteCharAt(sb.length() - 1);  // remove last ','
    }

    static SQLValues from(Map<String, Object> values) {
        SQLValues sqlValues = new SQLValues();
        Iterator<Map.Entry<String, Object>> iterator = values.entrySet().iterator();
        Map.Entry<String, Object> entry;
        while (iterator.hasNext()) {
            entry = iterator.next();
            sqlValues.setValue(entry.getKey(), entry.getValue());
        }
        return sqlValues;
    }
}
