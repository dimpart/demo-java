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

import java.util.Date;

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
 *      "mod"  : "history",
 *      "act"  : "query",
 *
 *      "group"     : "{GROUP_ID}",
 *      "last_time" : 0,             // Last group history time for querying
 *  }
 *  </pre></blockquote>
 */
public interface GroupHistory {

    String APP = "chat.dim.group";
    String MOD = "history";

    String ACT_QUERY = "query";

    //
    //  Factory method
    //

    /**
     *  QueryCommand is deprecated, use this action instead.
     *
     * @param group    - group ID
     * @param lastTime - last group history time
     * @return customized content for querying
     */
    static CustomizedContent queryGroupHistory(ID group, Date lastTime) {
        assert group.isGroup() : "group ID error: " + group;
        CustomizedContent content = CustomizedContent.create(APP, MOD, ACT_QUERY);
        content.setGroup(group);
        if (lastTime != null) {
            // Last group history time for querying
            content.setDateTime("last_time", lastTime);
        }
        return content;
    }

}
