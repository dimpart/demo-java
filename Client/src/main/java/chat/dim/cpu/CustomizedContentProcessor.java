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

import java.util.List;
import java.util.Map;

import chat.dim.Facebook;
import chat.dim.Messenger;
import chat.dim.TwinsHelper;
import chat.dim.protocol.Content;
import chat.dim.protocol.ContentType;
import chat.dim.protocol.CustomizedContent;
import chat.dim.protocol.GroupCommand;
import chat.dim.protocol.ID;
import chat.dim.protocol.ReliableMessage;
import chat.dim.protocol.group.GroupHistory;
import chat.dim.protocol.group.QueryCommand;

/**
 *  Customized Content Processing Unit
 *  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
 */
public class CustomizedContentProcessor extends BaseContentProcessor implements CustomizedContentHandler {

    public CustomizedContentProcessor(Facebook facebook, Messenger messenger) {
        super(facebook, messenger);
        groupHistoryHandler = new GroupHistoryHandler(facebook, messenger);
    }

    private final GroupHistoryHandler groupHistoryHandler;

    @Override
    public List<Content> processContent(Content content, ReliableMessage rMsg) {
        assert content instanceof CustomizedContent : "customized content error: " + content;
        CustomizedContent customized = (CustomizedContent) content;
        // 1. check app id
        String app = customized.getApplication();
        List<Content> res = filter(app, customized, rMsg);
        if (res != null) {
            // app id not found
            return res;
        }
        // 2. get handler with module name
        String mod = customized.getModule();
        CustomizedContentHandler handler = fetch(mod, customized, rMsg);
        if (handler == null) {
            // module not support
            return null;
        }
        // 3. do the job
        String act = customized.getAction();
        ID sender = rMsg.getSender();
        return handler.handleAction(act, sender, customized, rMsg);
    }

    // override for your application
    protected List<Content> filter(String app, CustomizedContent content, ReliableMessage rMsg) {
        if (GroupHistory.APP.equals(app)) {
            // app id matched,
            // return no errors
            return null;
        }
        return respondReceipt("Content not support.", rMsg.getEnvelope(), content, newMap(
                "template", "Customized content (app: ${app}) not support yet!",
                "replacements", newMap(
                        "app", app
                )
        ));
    }

    // override for your modules
    protected CustomizedContentHandler fetch(String mod, CustomizedContent content, ReliableMessage rMsg) {
        if (GroupHistory.MOD.equals(mod)) {
            String app = content.getApplication();
            if (GroupHistory.APP.equals(app)) {
                return groupHistoryHandler;
            }
            assert false : "unknown app: " + app + ", content: " + content + ", sender: " + rMsg.getSender();
            //return null;
        }
        // if the application has too many modules, I suggest you to
        // use different handler to do the jobs for each module.
        return this;
    }

    // override for customized actions
    @Override
    public List<Content> handleAction(String act, ID sender, CustomizedContent content, ReliableMessage rMsg) {
        String app = content.getApplication();
        String mod = content.getModule();
        return respondReceipt("Content not support.", rMsg.getEnvelope(), content, newMap(
                "template", "Customized content (app: ${app}, mod: ${mod}, act: ${act}) not support yet!",
                "replacements", newMap(
                        "app", app,
                        "mod", mod,
                        "act", act
                )
        ));
    }
}


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
final class GroupHistoryHandler extends TwinsHelper implements CustomizedContentHandler {

    public GroupHistoryHandler(Facebook facebook, Messenger messenger) {
        super(facebook, messenger);
    }

    @Override
    public List<Content> handleAction(String act, ID sender, CustomizedContent content, ReliableMessage rMsg) {
        Messenger messenger = getMessenger();
        if (messenger == null) {
            assert false : "messenger lost";
        } else if (GroupHistory.ACT_QUERY.equals(act)) {
            assert GroupHistory.APP.equals(content.getApplication());
            assert GroupHistory.MOD.equals(content.getModule());
            assert content.getGroup() != null : "group command error: " + content + ", sender: " + sender;
        } else {
            assert false : "unknown action: " + act + ", " + content + ", sender: " + sender;
            return null;
        }
        Map<String, Object> info = content.copyMap(false);
        info.put("type", ContentType.COMMAND);
        info.put("command", GroupCommand.QUERY);
        Content query = Content.parse(info);
        if (query instanceof QueryCommand) {
            return messenger.processContent(query, rMsg);
        }
        assert false : "query command error: " + query + ", " + content + ", sender: " + sender;
        return null;
    }
}
