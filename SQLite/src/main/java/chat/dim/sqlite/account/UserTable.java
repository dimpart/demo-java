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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import chat.dim.dbi.UserDBI;
import chat.dim.protocol.ID;
import chat.dim.sql.SQLConditions;
import chat.dim.sqlite.Database;
import chat.dim.sqlite.ResultSetExtractor;

public class UserTable implements UserDBI {

    private final Database database;
    private ResultSetExtractor<ID> extractor;

    public UserTable(Database db) {
        database = db;
        extractor = null;
    }

    private void prepare() throws SQLException {
        if (extractor != null) {
            // already created
            return;
        }
        extractor = (resultSet, index) -> {
            String user = resultSet.getString("user");
            return ID.parse(user);
        };
        String[] fields = {
                "id INTEGER PRIMARY KEY AUTOINCREMENT",
                "user VARCHAR(64)",
                "chosen BIT",
        };
        database.createTable("t_local_user", fields);
    }


    @Override
    public List<ID> getLocalUsers() {
        String[] columns = {"user"};
        try {
            prepare();
            return database.select(columns, "t_local_user", null,
                    null, null, "chosen DESC", 0, extractor);
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public boolean saveLocalUsers(List<ID> users) {
        List<ID> localUsers = getLocalUsers();
        if (localUsers == null) {
            localUsers = new ArrayList<>();
        }
        // 1. delete users not contain in current users
        for (ID identifier : localUsers) {
            if (users.contains(identifier)) {
                continue;
            }
            SQLConditions conditions = new SQLConditions();
            conditions.addCondition(null, "user", "=", identifier.toString());
            try {
                database.delete("t_local_user", conditions);
            } catch (SQLException e) {
                e.printStackTrace();
                return false;
            }
        }
        // 2. add new users
        for (ID identifier : users) {
            if (localUsers.contains(identifier)) {
                continue;
            }
            String[] columns = {"user", "chosen"};
            Object[] values = {identifier.toString(), 0};
            try {
                database.insert("t_local_user", columns, values);
            } catch (SQLException e) {
                e.printStackTrace();
                return false;
            }
        }
        // 3. check chosen user
        if (users.size() == 0) {
            // all users removed?
            return true;
        }
        ID first = users.get(0);
        if (localUsers.size() == 0 || !localUsers.get(0).equals(first)) {
            // first user changed
            Map<String, Object> values = new HashMap<>();
            values.put("chosen", 0);
            try {
                database.update("t_local_user", values, null);
            } catch (SQLException e) {
                e.printStackTrace();
                return false;
            }
        }
        // update first user to be chosen
        Map<String, Object> values = new HashMap<>();
        values.put("chosen", 1);
        SQLConditions conditions = new SQLConditions();
        conditions.addCondition(null, "user", "=", first.toString());
        try {
            database.update("t_local_user", values, null);
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    @Override
    public List<ID> getContacts(ID user) {
        throw new AssertionError("call ContactTable");
    }

    @Override
    public boolean saveContacts(List<ID> contacts, ID user) {
        throw new AssertionError("call ContactTable");
    }
}
