/* license: https://mit-license.org
 *
 *  DIMP : Decentralized Instant Messaging Protocol
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
package chat.dim.protocol.group;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import chat.dim.protocol.CustomizedContent;
import chat.dim.protocol.ID;

/**
 *  Group Key Command
 *
 *  <blockquote><pre>
 *  data format: {
 *      "type" : i2s(0xCC),
 *      "sn"   : 123,
 *      "time" : 123.456,
 *
 *      "app"  : "chat.dim.group",
 *      "mod"  : "keys",
 *      "act"  : "query",   // "update", "request", "respond"
 *
 *      "group"  : "{GROUP_ID}",
 *      "from"   : "{SENDER_ID}",
 *      "to"     : ["{MEMBER_ID}", ],  // query for members
 *      "digest" : "{KEY_DIGEST}",     // query with digest
 *      "keys"   : {
 *          "digest"      : "{KEY_DIGEST}",
 *          "{MEMBER_ID}" : "{ENCRYPTED_KEY}",
 *      }
 *  }
 *  </pre></blockquote>
 */
public interface GroupKeys {

    String APP = "chat.dim.group";
    String MOD = "keys";

    ///  Group Key Actions:
    ///
    ///     1. when group bot found new member, or key digest updated,
    ///        send a 'query' command to the message sender for new keys;
    ///
    ///     2. send all keys with digest to the group bot;
    ///
    ///     3. if a member received a group message with new key digest,
    ///        send a 'request' command to the group bot;
    ///
    ///     4. send new key to the group member.
    ///
    String ACT_QUERY   = "query";    // 1. bot -> sender
    String ACT_UPDATE  = "update";   // 2. sender -> bot
    String ACT_REQUEST = "request";  // 3. member -> bot
    String ACT_RESPOND = "respond";  // 4. bot -> member

    //
    //  Factory methods
    //

    static CustomizedContent create(String action, ID group, ID sender, List<ID> members, String digest) {
        assert group.isGroup() : "group ID error: " + group;
        assert sender.isUser() : "user ID error: " + sender;
        // 1. create group command
        CustomizedContent content = CustomizedContent.create(APP, MOD, action);
        content.setGroup(group);
        // 2. direction: sender -> members
        content.setString("from", sender);
        if (members != null) {
            content.put("to", ID.revert(members));
        }
        // 3. key digest
        if (digest != null) {
            content.put("digest", digest);
        }
        // OK
        return content;
    }
    static CustomizedContent create(String action, ID group, ID sender, Map<String, Object> encodedKeys) {
        assert group.isGroup() : "group ID error: " + group;
        assert sender.isUser() : "user ID error: " + sender;
        // 1. create group command
        CustomizedContent content = CustomizedContent.create(APP, MOD, action);
        content.setGroup(group);
        // 2. direction: sender -> members
        content.setString("from", sender);
        // 3. keys and digest
        if (encodedKeys != null) {
            content.put("encodedKeys", encodedKeys);
        }
        // OK
        return content;
    }

    // 1. bot -> sender
    /**
     *  Query group keys from sender
     *
     * @param group       - group ID
     * @param sender      - from
     * @param members     - query for members
     * @param digest      - key digest
     * @return customized content for group keys
     */
    static CustomizedContent queryGroupKeys(ID group, ID sender, List<ID> members, String digest) {
        return create(ACT_QUERY, group, sender, members, digest);
    }

    // 2. sender -> bot
    /**
     *  Update group keys from sender
     *
     * @param group       - group ID
     * @param sender      - from
     * @param encodedKeys - keys with digest
     * @return customized content for group keys
     */
    static CustomizedContent updateGroupKeys(ID group, ID sender, Map<String, Object> encodedKeys) {
        return create(ACT_UPDATE, group, sender, encodedKeys);
    }

    // 3. member -> bot
    /**
     *  Request group key for this member
     *
     * @param group       - group ID
     * @param sender      - from
     * @return customized content for group keys
     */
    static CustomizedContent requestGroupKey(ID group, ID sender, String digest) {
        return create(ACT_REQUEST, group, sender, null, digest);
    }

    static CustomizedContent respondGroupKey(ID group, ID sender, ID member, Object encodedKey, String digest) {
        Map<String, Object> params = new HashMap<>();
        params.put(member.toString(), encodedKey);
        if (digest != null) {
            params.put("digest", digest);
        }
        return create(ACT_RESPOND, group, sender, params);
    }

}
