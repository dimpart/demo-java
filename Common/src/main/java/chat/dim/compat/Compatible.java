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
package chat.dim.compat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import chat.dim.protocol.ContentType;
import chat.dim.protocol.MetaVersion;
import chat.dim.protocol.ReliableMessage;


// TODO: remove after all server/client upgraded
public abstract class Compatible {

    @SuppressWarnings("unchecked")
    public static void fixMetaAttachment(ReliableMessage rMsg) {
        Object meta = rMsg.get("meta");
        if (meta != null) {
            fixMetaVersion((Map<String, Object>) meta);
        }
    }

    // 'type' <-> 'version'
    static void fixMetaVersion(Map<String, Object> meta) {
        Object type = meta.get("type");
        if (type == null) {
            type = meta.get("version");
        } else if (type instanceof String && !meta.containsKey("algorithm")) {
            // TODO: check number
            if (((String) type).length() > 2) {
                meta.put("algorithm", type);
            }
        }
        int version = MetaVersion.parseInt(type, 0);
        if (version > 0) {
            meta.put("type", version);
            meta.put("version", version);
        }
    }

    @SuppressWarnings("unchecked")
    public static void fixVisaAttachment(ReliableMessage rMsg) {
        Object visa = rMsg.get("visa");
        if (visa != null) {
            fixDocument((Map<String, Object>) visa);
        }
    }

    // 'ID' <-> 'did'
    static Map<String, Object> fixDocument(Map<String, Object> document) {
        Compatible.fixID(document);
        return document;
    }

    // 'cmd' <-> 'command'
    static void fixCmd(Map<String, Object> content) {
        Object cmd = content.get("command");
        if (cmd == null) {
            // 'command' not exists, copy the value from 'cmd'
            cmd = content.get("cmd");
            if (cmd != null) {
                content.put("command", cmd);
            } else {
                assert false : "command error: " + content;
            }
        } else if (content.containsKey("cmd")) {
            assert cmd.equals(content.get("cmd")) : "command error: " + content;
        } else {
            // copy value from 'command' to 'cmd'
            content.put("cmd", cmd);
        }
    }

    // 'ID' <-> 'did'
    static void fixID(Map<String, Object> content) {
        Object did = content.get("did");
        if (did == null) {
            // 'did' not exists, copy the value form 'ID'
            did = content.get("ID");
            if (did != null) {
                content.put("did", did);
            //} else {
            //    assert false : "did not exists: " + content;
            }
        } else if (content.containsKey("ID")) {
            assert did.equals(content.get("ID")) : "did error: " + content;
        } else {
            // copy value from 'did' to 'ID'
            content.put("ID", did);
        }
    }

    static final List<String> FILE_TYPES = new ArrayList<String>() {{
        add(ContentType.FILE);  add("file");
        add(ContentType.IMAGE); add("image");
        add(ContentType.AUDIO); add("audio");
        add(ContentType.VIDEO); add("video");
    }};

    static void fixFileContent(Map<String, Object> content) {
        Object pwd = content.get("key");
        if (pwd != null) {
            // Tarsier version > 1.3.7
            // DIM SDK version > 1.1.0
            content.put("password", pwd);
        } else {
            // Tarsier version <= 1.3.7
            // DIM SDK version <= 1.1.0
            pwd = content.get("password");
            if (pwd != null) {
                content.put("key", pwd);
            }
        }
    }

    @SuppressWarnings("unchecked")
    static void fixReceiptCommand(Map<String, Object> content) {
        // check for v2.0
        Object origin = content.get("origin");
        if (origin == null) {
            // check for v1.0
            Object envelope = content.get("envelope");
            if (envelope == null) {
                // check for older version
                if (!content.containsKey("sender")) {
                    // this receipt contains no envelope info,
                    // no need to fix it.
                    return;
                }
                // older version
                Map<String, Object> env = new HashMap<>();
                env.put("sender",    content.get("sender"));
                env.put("receiver",  content.get("receiver"));
                env.put("time",      content.get("time"));
                env.put("sn",        content.get("sn"));
                env.put("signature", content.get("signature"));
                content.put("origin", env);
                content.put("envelope", env);
            } else {
                // (v1.0)
                // compatible with v2.0
                content.put("origin", envelope);
                // compatible with older version
                copyReceiptValues((Map<String, Object>) envelope, content);
            }
        } else {
            // (v2.0)
            // compatible with v1.0
            content.put("envelope", origin);
            // compatible with older version
            copyReceiptValues((Map<String, Object>) origin, content);
        }
    }
    private static void copyReceiptValues(Map<String, Object> fromOrigin, Map<String, Object> toContent) {
        String name;
        for (Map.Entry<String, Object> entry : fromOrigin.entrySet()) {
            name = entry.getKey();
            if (name == null) {
                // should not happen
                continue;
            } else if (name.equals("type")) {
                continue;
            } else if (name.equals("time")) {
                continue;
            }
            toContent.put(name, entry.getValue());
        }
    }
}
