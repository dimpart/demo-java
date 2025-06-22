/* license: https://mit-license.org
 *
 *  DIM-SDK : Decentralized Instant Messaging Software Development Kit
 *
 *                                Written in 2025 by Moky <albert.moky@gmail.com>
 *
 * ==============================================================================
 * The MIT License (MIT)
 *
 * Copyright (c) 2025 Albert Moky
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
package chat.dim.compat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import chat.dim.protocol.Command;
import chat.dim.protocol.ContentType;
import chat.dim.protocol.LoginCommand;
import chat.dim.type.Converter;


// TODO: remove after all server/client upgraded
public abstract class CompatibleIncoming {

    @SuppressWarnings("unchecked")
    public static void fixContent(Map<String, Object> content) {
        // get content type
        String type = Converter.getString(content.get("type"), "");

        if (type.equals(ContentType.NAME_CARD) || type.equals("card")) {
            // 1. 'ID' <-> 'did'
            Compatible.fixID(content);
            return;
        }

        if (type.equals(ContentType.COMMAND) || type.equals("command")) {
            // 1. 'cmd' <-> 'command'
            Compatible.fixCmd(content);
        }
        // get command name
        String cmd = Converter.getString(content.get("command"), null);
        if (cmd == null || cmd.isEmpty()) {
            return;
        }

        // if (cmd.equals(Command.RECEIPT)) {}

        if (cmd.equals(LoginCommand.LOGIN)) {
            // 2. 'ID' <-> 'did'
            Compatible.fixID(content);
            return;
        }

        if (cmd.equals(Command.DOCUMENTS) || cmd.equals("document")) {
            // 2. cmd: 'document' -> 'documents'
            fixDocs(content);
        }
        if (cmd.equals(Command.META) || cmd.equals(Command.DOCUMENTS) || cmd.equals("document")) {
            // 3. 'ID' <-> 'did'
            Compatible.fixID(content);

            Object meta = content.get("meta");
            if (meta != null) {
                // 4. 'type' <-> 'version'
                Compatible.fixMetaVersion((Map<String, Object>) meta);
            }
        }

    }

    @SuppressWarnings("unchecked")
    private static void fixDocs(Map<String, Object> content) {
        // cmd: 'document' -> 'documents'
        String cmd = (String) content.get("command");
        if ("document".equals(cmd)) {
            content.put("command", "documents");
        }
        // 'document' -> 'documents'
        Object doc = content.get("document");
        if (doc != null) {
            List<Object> array = new ArrayList<>();
            array.add(Compatible.fixDocument((Map<String, Object>) doc));
            content.put("documents", array);
            content.remove("document");
        }
    }

}
