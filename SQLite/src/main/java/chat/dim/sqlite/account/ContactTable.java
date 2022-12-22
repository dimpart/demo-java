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

import chat.dim.dbi.UserDBI;
import chat.dim.protocol.ID;
import chat.dim.sql.SQLConditions;
import chat.dim.sqlite.Database;
import chat.dim.sqlite.ResultSetExtractor;

public class ContactTable implements UserDBI {

    private final Database database;
    private ResultSetExtractor<ID> extractor;

    public ContactTable(Database db) {
        database = db;
        extractor = null;
    }

    private void prepare() throws SQLException {
        if (extractor != null) {
            // already created
            return;
        }
        extractor = (resultSet, index) -> {
            String did = resultSet.getString("contact");
            return ID.parse(did);
        };
        String[] fields = {
                "id INTEGER PRIMARY KEY AUTOINCREMENT",
                "user VARCHAR(64)",
                "contact VARCHAR(64)",
                "alias VARCHAR(32))",
        };
        database.createTable("t_contact", fields);
    }


    @Override
    public List<ID> getLocalUsers() {
        throw new AssertionError("call UserTable");
    }

    @Override
    public boolean saveLocalUsers(List<ID> users) {
        throw new AssertionError("call UserTable");
    }

    @Override
    public List<ID> getContacts(ID user) {
        SQLConditions conditions = new SQLConditions();
        conditions.addCondition(null, "user", "=", user.toString());
        String[] columns = {"contact"/*, "alias"*/};
        try {
            prepare();
            return database.select(columns, "t_contact", conditions, 0, extractor);
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public boolean saveContacts(List<ID> contacts, ID user) {
        List<ID> oldContacts = getContacts(user);
        if (oldContacts == null) {
            oldContacts = new ArrayList<>();
        }
        // 1. delete old records not contain in current contacts
        for (ID identifier : oldContacts) {
            if (contacts.contains(identifier)) {
                continue;
            }
            SQLConditions conditions = new SQLConditions();
            conditions.addCondition(null, "user", "=", user.toString());
            conditions.addCondition(SQLConditions.Relation.AND, "contact", "=", identifier.toString());
            try {
                database.delete("t_contact", conditions);
            } catch (SQLException e) {
                e.printStackTrace();
                return false;
            }
        }
        // 2. add new users
        for (ID identifier : contacts) {
            if (oldContacts.contains(identifier)) {
                continue;
            }
            String[] columns = {"user", "contact"};
            Object[] values = {user.toString(), identifier.toString()};
            try {
                database.insert("t_contact", columns, values);
            } catch (SQLException e) {
                e.printStackTrace();
                return false;
            }
        }
        return true;
    }
}
