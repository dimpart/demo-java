/* license: https://mit-license.org
 *
 *  DIM-SDK : Decentralized Instant Messaging Software Development Kit
 *
 *                                Written in 2026 by Moky <albert.moky@gmail.com>
 *
 * ==============================================================================
 * The MIT License (MIT)
 *
 * Copyright (c) 2026 Albert Moky
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
package chat.dim.cpu.app;

import java.util.HashMap;
import java.util.Map;

import chat.dim.protocol.CustomizedContent;
import chat.dim.protocol.ReliableMessage;

public class AppCustomizedFilter implements CustomizedContentFilter {

    private final Map<String, CustomizedContentHandler> handlers;

    private final CustomizedContentHandler defaultHandler;

    public AppCustomizedFilter() {
        super();
        handlers = new HashMap<>();
        defaultHandler = new BaseCustomizedHandler();
    }

    public void setContentHandler(String app, String mod, CustomizedContentHandler handler) {
        handlers.put(app + ":" + mod, handler);
    }

    protected CustomizedContentHandler getContentHandler(String app, String mod) {
        return handlers.get(app + ":" + mod);
    }

    @Override
    public CustomizedContentHandler filterContent(CustomizedContent content, ReliableMessage rMsg) {
        String app = content.getApplication();
        String mod = content.getModule();
        CustomizedContentHandler handler = getContentHandler(app, mod);
        return handler == null ? defaultHandler : handler;
    }

}