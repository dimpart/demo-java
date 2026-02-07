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
package chat.dim.cpu.app;

import java.util.List;
import java.util.Map;

import chat.dim.Messenger;
import chat.dim.protocol.Content;
import chat.dim.protocol.ContentType;
import chat.dim.protocol.CustomizedContent;
import chat.dim.protocol.ReliableMessage;
import chat.dim.protocol.group.GroupHistory;
import chat.dim.protocol.group.QueryCommand;

/*  Command Transform:

    +===============================+===============================+
    |      Customized Content       |      Group Query Command      |
    +-------------------------------+-------------------------------+
    |   "type" : i2s(0xCC)          |   "type" : i2s(0x88)          |
    |   "sn"   : 123                |   "sn"   : 123                |
    |   "time" : 123.456            |   "time" : 123.456            |
    |   "app"  : "chat.dim.group"   |                               |
    |   "mod"  : "history"          |                               |
    |   "act"  : "query"            |                               |
    |                               |   "command"   : "query"       |
    |   "group"     : "{GROUP_ID}"  |   "group"     : "{GROUP_ID}"  |
    |   "last_time" : 0             |   "last_time" : 0             |
    +===============================+===============================+
 */
public final class GroupHistoryHandler extends BaseCustomizedHandler {

    @Override
    public List<Content> handleContent(CustomizedContent content, ReliableMessage rMsg,
                                       Messenger messenger) {
        if (content.getGroup() == null) {
            assert false : "group command error: " + content + ", sender: " + rMsg.getSender();
            return respondReceipt("Group command error.", rMsg.getEnvelope(), content, null);
        }
        String act = content.getAction();
        if (GroupHistory.ACT_QUERY.equals(act)) {
            assert GroupHistory.APP.equals(content.getApplication());
            assert GroupHistory.MOD.equals(content.getModule());
            return transformQueryCommand(content, rMsg, messenger);
        }
        assert false : "unknown action: " + act + ", " + content + ", sender: " + rMsg.getSender();
        return super.handleContent(content, rMsg, messenger);
    }

    private List<Content> transformQueryCommand(CustomizedContent content, ReliableMessage rMsg,
                                                Messenger messenger) {
        Map<String, Object> info = content.copyMap(false);
        info.put("type", ContentType.COMMAND);
        info.put("command", QueryCommand.QUERY);
        Content query = Content.parse(info);
        if (query instanceof QueryCommand) {
            return messenger.processContent(query, rMsg);
        }
        assert false : "query command error: " + query + ", " + content + ", sender: " + rMsg.getSender();
        return respondReceipt("Query command error.", rMsg.getEnvelope(), content, null);
    }

}
