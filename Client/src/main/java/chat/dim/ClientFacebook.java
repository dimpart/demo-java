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
import chat.dim.protocol.Address;
import chat.dim.protocol.Document;
import chat.dim.protocol.ID;
import chat.dim.protocol.Meta;

/**
 *  Client Facebook with Address Name Service
 */
public class ClientFacebook extends CommonFacebook {

    public ClientFacebook(AccountDBI db) {
        super(db);
    }

    public String getName(ID identifier) {
        // get name from document
        Document doc = getDocument(identifier, "*");
        if (doc != null) {
            String name = doc.getName();
            if (name != null && name.length() > 0) {
                return name;
            }
        }
        // get name from ID
        return Anonymous.getName(identifier);
    }

    //
    //  Address Name Service
    //
    public static AddressNameServer ans = null;

    static void prepare() {
        if (loaded) {
            return;
        }

        // load plugins
        Register.prepare();

        identifierFactory = ID.getFactory();
        ID.setFactory(new ID.Factory() {

            @Override
            public ID generateID(Meta meta, int type, String terminal) {
                return identifierFactory.generateID(meta, type, terminal);
            }

            @Override
            public ID createID(String name, Address address, String terminal) {
                return identifierFactory.createID(name, address, terminal);
            }

            @Override
            public ID parseID(String identifier) {
                // try ANS record
                ID id = ans.identifier(identifier);
                if (id != null) {
                    return id;
                }
                // parse by original factory
                return identifierFactory.parseID(identifier);
            }
        });

        loaded = true;
    }
    private static boolean loaded = false;

    private static ID.Factory identifierFactory;

}
