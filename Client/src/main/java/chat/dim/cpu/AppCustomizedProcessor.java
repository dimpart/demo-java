/* license: https://mit-license.org
 *
 *  DIM-SDK : Decentralized Instant Messaging Software Development Kit
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
package chat.dim.cpu;

import java.util.HashMap;
import java.util.Map;

import chat.dim.Facebook;
import chat.dim.Messenger;
import chat.dim.cpu.app.CustomizedContentHandler;
import chat.dim.protocol.CustomizedContent;
import chat.dim.protocol.ReliableMessage;

/**
 *  Customized Content Processing Unit
 *  <p>
 *      Handle content for application customized
 *  </p>
 */
public class AppCustomizedProcessor extends CustomizedContentProcessor {

    private final Map<String, CustomizedContentHandler> handlers = new HashMap<>();

    public AppCustomizedProcessor(Facebook facebook, Messenger messenger) {
        super(facebook, messenger);
    }

    public void setHandler(String app, String mod, CustomizedContentHandler handler) {
        handlers.put(app + ":" + mod, handler);
    }

    protected CustomizedContentHandler getHandler(String app, String mod) {
        return handlers.get(app + ":" + mod);
    }

    @Override
    protected CustomizedContentHandler filter(String app, String mod, CustomizedContent content, ReliableMessage rMsg) {
        CustomizedContentHandler handler = getHandler(app, mod);
        if (handler != null) {
            return handler;
        }
        // default handler
        return super.filter(app, mod, content, rMsg);
    }

}
