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
package chat.dim;

import chat.dim.compat.CommonPluginLoader;
import chat.dim.protocol.Address;
import chat.dim.protocol.ID;
import chat.dim.protocol.Meta;

public class ClientPluginLoader extends CommonPluginLoader {

    private ID.Factory identifierFactory;

    @Override
    public boolean load() {
        if (!super.load()) {
            // already loaded
            return false;
        }

        replaceIDFactory();

        return true;
    }

    private void replaceIDFactory() {

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
                ID id = ClientFacebook.ans.identifier(identifier);
                if (id != null) {
                    return id;
                }
                // parse by original factory
                return identifierFactory.parseIdentifier(identifier);
            }
        });

    }

}
