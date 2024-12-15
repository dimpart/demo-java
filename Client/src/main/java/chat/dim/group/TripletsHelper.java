/* license: https://mit-license.org
 *
 *  DIM-SDK : Decentralized Instant Messaging Software Development Kit
 *
 *                                Written in 2024 by Moky <albert.moky@gmail.com>
 *
 * ==============================================================================
 * The MIT License (MIT)
 *
 * Copyright (c) 2024 Albert Moky
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
package chat.dim.group;

import chat.dim.CommonArchivist;
import chat.dim.CommonFacebook;
import chat.dim.CommonMessenger;
import chat.dim.dbi.AccountDBI;


abstract class TripletsHelper {
    protected TripletsHelper(GroupDelegate dataSource) {
        assert dataSource != null : "Group delegate should not empty";
        delegate = dataSource;
    }

    protected final GroupDelegate delegate;

    protected CommonFacebook getFacebook() {
        return delegate.getFacebook();
    }

    protected CommonMessenger getMessenger() {
        return delegate.getMessenger();
    }

    protected CommonArchivist getArchivist() {
        CommonFacebook facebook = getFacebook();
        return facebook == null ? null : facebook.getArchivist();
    }

    protected AccountDBI getDatabase() {
        CommonArchivist archivist = getArchivist();
        return archivist == null ? null : archivist.getDatabase();
    }

}
