/* license: https://mit-license.org
 *
 *  DIM-SDK : Decentralized Instant Messaging Software Development Kit
 *
 *                                Written in 2023 by Moky <albert.moky@gmail.com>
 *
 * ==============================================================================
 * The MIT License (MIT)
 *
 * Copyright (c) 2023 Albert Moky
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
package chat.dim;

import chat.dim.dbi.AccountDBI;
import chat.dim.group.SharedGroupManager;
import chat.dim.mkm.Entity;
import chat.dim.mkm.Group;
import chat.dim.protocol.ID;

public class ClientArchivist extends CommonArchivist {

    public ClientArchivist(Facebook facebook, AccountDBI db) {
        super(facebook, db);
    }

    @Override
    public Group createGroup(ID group) {
        Group grp = super.createGroup(group);
        if (grp != null) {
            Entity.DataSource delegate = grp.getDataSource();
            if (delegate == null || delegate == getFacebook()) {
                // replace group's data source
                SharedGroupManager manager = SharedGroupManager.getInstance();
                grp.setDataSource(manager);
            }
        }
        return grp;
    }

}
