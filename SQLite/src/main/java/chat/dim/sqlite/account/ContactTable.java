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

import chat.dim.dbi.ContactDBI;
import chat.dim.protocol.ID;
import chat.dim.sql.SQLConditions;
import chat.dim.sqlite.DataRowExtractor;
import chat.dim.sqlite.DataTableHandler;
import chat.dim.sqlite.DatabaseConnector;

public class ContactTable extends DataTableHandler<ID> implements ContactDBI {

    private DataRowExtractor<ID> extractor;

    public ContactTable(DatabaseConnector connector) {
        super(connector);
        // lazy load
        extractor = null;
    }

    @Override
    protected DataRowExtractor<ID> getDataRowExtractor() {
        return extractor;
    }

    private boolean prepare() {
        if (extractor == null) {
            // create table if not exists
            String[] fields = {
                    "id INTEGER PRIMARY KEY AUTOINCREMENT",
                    "user VARCHAR(64)",
                    "contact VARCHAR(64)",
                    "alias VARCHAR(32))",
            };
            if (!createTable(T_CONTACT, fields)) {
                // db error
                return false;
            }
            // prepare data row extractor
            extractor = (resultSet, index) -> {
                String did = resultSet.getString("contact");
                return ID.parse(did);
            };
        }
        return true;
    }
    private static final String[] SELECT_COLUMNS = {"contact"/*, "alias"*/};
    private static final String[] INSERT_COLUMNS = {"user", "contact"/*, "alias"*/};
    private static final String T_CONTACT = "t_contact";

    @Override
    public List<ID> getContacts(ID user) {
        if (!prepare()) {
            // db error
            return null;
        }
        SQLConditions conditions = new SQLConditions();
        conditions.addCondition(null, "user", "=", user.toString());
        return select(T_CONTACT, SELECT_COLUMNS, conditions);
    }

    @Override
    public boolean saveContacts(List<ID> contacts, ID user) {
        List<ID> oldContacts = getContacts(user);
        if (oldContacts == null) {
            // db error
            return false;
        }
        // 1. delete old records not contain in current contacts
        for (ID identifier : oldContacts) {
            if (contacts.contains(identifier)) {
                continue;
            }
            SQLConditions conditions = new SQLConditions();
            conditions.addCondition(null, "user", "=", user.toString());
            conditions.addCondition(SQLConditions.Relation.AND, "contact", "=", identifier.toString());
            if (delete(T_CONTACT, conditions) < 0) {
                // db error
                return false;
            }
        }
        // 2. add new users
        for (ID identifier : contacts) {
            if (oldContacts.contains(identifier)) {
                continue;
            }
            Object[] values = {user.toString(), identifier.toString()};
            if (insert(T_CONTACT, INSERT_COLUMNS, values) < 0) {
                // db error
                return false;
            }
        }
        return true;
    }
}
