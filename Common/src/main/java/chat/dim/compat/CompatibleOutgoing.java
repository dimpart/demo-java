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

import java.util.List;
import java.util.Map;

import chat.dim.mkm.DocumentUtils;
import chat.dim.protocol.Command;
import chat.dim.protocol.Content;
import chat.dim.protocol.Document;
import chat.dim.protocol.DocumentCommand;
import chat.dim.protocol.FileContent;
import chat.dim.protocol.LoginCommand;
import chat.dim.protocol.MetaCommand;
import chat.dim.protocol.NameCard;
import chat.dim.protocol.ReceiptCommand;
import chat.dim.type.Converter;


// TODO: remove after all server/client upgraded
public abstract class CompatibleOutgoing {

    @SuppressWarnings("unchecked")
    public static void fixContent(Content content) {
        // 0. change 'type' value from string to int
        fixType(content);

        if (content instanceof FileContent) {
            // 1. 'key' <-> 'password'
            Compatible.fixFileContent(content.toMap());
            return;
        }

        if (content instanceof NameCard) {
            // 1. 'ID' <-> 'did'
            Compatible.fixID(content.toMap());
            return;
        }

        if (content instanceof Command) {
            // 1. 'cmd' <-> 'command'
            Compatible.fixCmd(content.toMap());
        }

        if (content instanceof ReceiptCommand) {
            // 2. check for v2.0
            Compatible.fixReceiptCommand(content.toMap());
            return;
        }

        if (content instanceof LoginCommand) {
            // 2. 'ID' <-> 'did'
            Compatible.fixID(content.toMap());
            return;
        }

        if (content instanceof DocumentCommand) {
            // 2. cmd: 'documents' -> 'document'
            fixDocs((DocumentCommand) content);
        }
        if (content instanceof MetaCommand) {
            // 3. 'ID' <-> 'did'
            Compatible.fixID(content.toMap());

            Object meta = content.get("meta");
            if (meta != null) {
                // 4. 'type' <-> 'version'
                Compatible.fixMetaVersion((Map<String, Object>) meta);
            }
        }

    }

    private static void fixType(Map<String, Object> content) {
        Object type = content.get("type");
        if (type instanceof String) {
            Integer num = Converter.getInteger(type, -1);
            if (num != null && num >= 0) {
                content.put("type", num);
            }
        }
    }

    private static void fixDocs(DocumentCommand content) {
        // cmd: 'documents' -> 'document'
        String cmd = content.getCmd();
        if ("documents".equals(cmd)) {
            content.put("command", "document");
            content.put("cmd", "document");
        }
        // 'documents' -> 'document'
        Object array = content.get("documents");
        if (array instanceof List) {
            List<Document> docs = Document.convert((Iterable<?>) array);
            Document last = DocumentUtils.lastDocument(docs, null);
            if (last != null) {
                content.put("document", Compatible.fixDocument(last.toMap()));
            }
            if (docs.size() == 1) {
                content.remove("documents");
            }
        }
    }

}
