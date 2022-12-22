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
package chat.dim.database.dos;

import java.io.IOException;
import java.util.Map;

import chat.dim.dbi.DocumentDBI;
import chat.dim.protocol.Document;
import chat.dim.protocol.ID;
import chat.dim.utils.Template;

/**
 *  Document for Entities (User/Group)
 *  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
 *
 *  file path: '.dim/public/{ADDRESS}/document.js'
 */
public class DocumentStorage extends Storage implements DocumentDBI {

    public static String DOC_PATH = "{PUBLIC}/{ADDRESS}/document.js";

    public DocumentStorage(String rootDir, String publicDir, String privateDir) {
        super(rootDir, publicDir, privateDir);
    }

    public void showInfo() {
        String path = Template.replace(DOC_PATH, "PUBLIC", publicDirectory);
        System.out.println("!!!       doc path: " + path);
    }

    private String getDocPath(ID entity) {
        String path = DOC_PATH;
        path = Template.replace(path, "PUBLIC", publicDirectory);
        path = Template.replace(path, "ADDRESS", entity.getAddress().toString());
        return path;
    }

    @Override
    public boolean saveDocument(Document doc) {
        ID identifier = doc.getIdentifier();
        String path = getDocPath(identifier);
        try {
            return saveJSON(doc.toMap(), path) > 0;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public Document getDocument(ID entity, String type) {
        String path = getDocPath(entity);
        try {
            Object info = loadJSON(path);
            if (info == null) {
                return null;
            }
            return parseDocument((Map<String, Object>) info, null, null);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static Document parseDocument(Map<String, Object> dict, ID identifier, String docType) {
        // check document ID
        ID did = ID.parse(dict.get("ID"));
        assert did != null : "document error: " + dict;
        if (identifier == null) {
            identifier = did;
        } else {
            assert identifier.equals(did) : "document ID not match: " + identifier + ", " + did;
        }
        // check document type
        if (docType == null) {
            docType = "*";
        }
        String dt = (String) dict.get("type");
        if (dt != null) {
            docType = dt;
        }
        // check document data
        String data = (String) dict.get("data");
        if (data == null) {
            // compatible with v1.0
            data = (String) dict.get("profile");
        }
        // check document signature
        String signature = (String) dict.get("signature");
        if (data == null || signature == null) {
            throw new AssertionError("document error: " + dict);
        }
        return Document.create(docType, identifier, data, signature);
    }
}
