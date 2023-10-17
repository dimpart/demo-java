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

import java.util.List;

import chat.dim.dbi.AccountDBI;
import chat.dim.protocol.Address;
import chat.dim.protocol.Bulletin;
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

    @Override
    public boolean saveDocument(Document doc) {
        boolean ok = super.saveDocument(doc);
        if (ok && doc instanceof Bulletin) {
            ID group = doc.getIdentifier();
            assert group.isGroup() : "group ID error: " + group;
            List<ID> admins = getAdministrators((Bulletin) doc);
            if (admins != null && admins.size() > 0) {
                ok = saveAdministrators(admins, group);
            }
        }
        return ok;
    }

    private static List<ID> getAdministrators(Bulletin doc) {
        Object array = doc.getProperty("administrators");
        if (array instanceof List) {
            return ID.convert((List<?>) array);
        }
        // admins not found
        return null;
    }

    public boolean saveMembers(List<ID> members, ID group) {
        return getDatabase().saveMembers(members, group);
    }
    public boolean saveAssistants(List<ID> bots, ID group) {
        return getDatabase().saveAssistants(bots, group);
    }
    public boolean saveAdministrators(List<ID> admins, ID group) {
        return getDatabase().saveAdministrators(admins, group);
    }
    public List<ID> getAdministrators(ID group) {
        List<ID> admins = getDatabase().getAdministrators(group);
        if (admins == null || admins.isEmpty()) {
            Document doc = getDocument(group, "*");
            if (doc instanceof Bulletin) {
                admins = getAdministrators((Bulletin) doc);
            }
        }
        return admins;
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
            public ID generateIdentifier(Meta meta, int type, String terminal) {
                return identifierFactory.generateIdentifier(meta, type, terminal);
            }

            @Override
            public ID createIdentifier(String name, Address address, String terminal) {
                return identifierFactory.createIdentifier(name, address, terminal);
            }

            @Override
            public ID parseIdentifier(String identifier) {
                // try ANS record
                ID id = ans.identifier(identifier);
                if (id != null) {
                    return id;
                }
                // parse by original factory
                return identifierFactory.parseIdentifier(identifier);
            }
        });

        loaded = true;
    }
    private static boolean loaded = false;

    private static ID.Factory identifierFactory;

}
