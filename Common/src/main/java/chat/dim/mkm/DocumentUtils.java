/* license: https://mit-license.org
 *
 *  Ming-Ke-Ming : Decentralized User Identity Authentication
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
package chat.dim.mkm;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import chat.dim.ext.SharedAccountExtensions;
import chat.dim.protocol.Address;
import chat.dim.protocol.Bulletin;
import chat.dim.protocol.Document;
import chat.dim.protocol.DocumentCommand;
import chat.dim.protocol.ID;
import chat.dim.protocol.Meta;
import chat.dim.protocol.Visa;

public interface DocumentUtils {

    static String getDocumentType(Document doc) {
        return SharedAccountExtensions.helper.getDocumentType(doc.toMap(), null);
    }

    /**
     *  Check whether this time is before old time
     */
    static boolean isBefore(Date oldTime, Date thisTime) {
        if (oldTime == null || thisTime == null) {
            return false;
        }
        return thisTime.before(oldTime);
    }

    /**
     *  Check whether this document's time is before old document's time
     */
    static boolean isExpired(Document thisDoc, Document oldDoc) {
        return isBefore(oldDoc.getTime(), thisDoc.getTime());
    }

    /**
     *  Select last document matched the type
     */
    static Document lastDocument(Iterable<Document> documents, String type) {
        if (documents == null) {
            return null;
        } else if (type == null || type.equals("*")) {
            type = "";
        }
        boolean checkType = !type.isEmpty();

        Document last = null;
        String docType;
        boolean matched;
        for (Document doc : documents) {
            // 1. check type
            if (checkType) {
                docType = getDocumentType(doc);
                matched = docType == null || docType.isEmpty() || docType.equals(type);
                if (!matched) {
                    // type not matched, ignore it
                    continue;
                }
            }
            // 2. check time
            if (last != null && isExpired(doc, last)) {
                // skip old document
                continue;
            }
            // got it
            last = doc;
        }
        return last;
    }

    /**
     *  Select last visa document
     */
    static Visa lastVisa(Iterable<Document> documents) {
        if (documents == null) {
            return null;
        }
        Visa last = null;
        boolean matched;
        for (Document doc : documents) {
            // 1. check type
            matched = doc instanceof Visa;
            if (!matched) {
                // type not matched, ignore it
                continue;
            }
            // 2. check time
            if (last != null && isExpired(doc, last)) {
                // skip old document
                continue;
            }
            // got it
            last = (Visa) doc;
        }
        return last;
    }

    /**
     *  Select last bulletin document
     */
    static Bulletin lastBulletin(Iterable<Document> documents) {
        if (documents == null) {
            return null;
        }
        Bulletin last = null;
        boolean matched;
        for (Document doc : documents) {
            // 1. check type
            matched = doc instanceof Bulletin;
            if (!matched) {
                // type not matched, ignore it
                continue;
            }
            // 2. check time
            if (last != null && isExpired(doc, last)) {
                // skip old document
                continue;
            }
            // got it
            last = (Bulletin) doc;
        }
        return last;
    }

    static DocumentCommand response(ID did, Meta meta, Document document) {
        List<Document> array = new ArrayList<>();
        array.add(document);
        return response(did, meta, array);
    };

    static DocumentCommand response(ID did, Meta meta, List<Document> documents) {
        // check document ID
        Address address = did.getAddress();
        for (Document doc : documents) {
            ID docID = ID.parse(doc.get("did"));
            if (docID == null) {
                assert false : "document ID not found: " + doc;
                continue;
            } else if (docID.equals(did)) {
                // OK
                continue;
            }
            if (docID.getAddress().equals(address)) {
                // TODO: check ID.name
                continue;
            }
            // error
            assert false : "document ID not matched: " + docID + ", " + did;
            return null;
        }
        return DocumentCommand.response(did, meta, documents);
    }

}
