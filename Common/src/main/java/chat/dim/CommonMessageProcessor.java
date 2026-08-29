/* license: https://mit-license.org
 *
 *  DIM-SDK : Decentralized Instant Messaging Software Development Kit
 *
 *                                Written in 2024 by Moky <albert.moky@gmail.com>
 *
 * ==============================================================================
 * The MIT License (MIT)
 *
 * Copyright (c) 2024 Albert Moky
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
package chat.dim;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import chat.dim.dkd.ContentProcessor;
import chat.dim.dkd.ContentProcessorFactory;
import chat.dim.mkm.User;
import chat.dim.protocol.ArrayContent;
import chat.dim.protocol.Command;
import chat.dim.protocol.Content;
import chat.dim.protocol.Envelope;
import chat.dim.protocol.ID;
import chat.dim.protocol.InstantMessage;
import chat.dim.protocol.ReliableMessage;
import chat.dim.protocol.group.GroupCommand;


public abstract class CommonMessageProcessor extends MessageProcessor {

    public CommonMessageProcessor(CommonFacebook facebook, CommonMessenger messenger) {
        super(facebook, messenger);
    }

    protected EntityChecker getEntityChecker() {
        Facebook facebook = getFacebook();
        if (facebook instanceof CommonFacebook) {
            return ((CommonFacebook) facebook).getEntityChecker();
        }
        assert facebook == null : "facebook error: " + facebook;
        return null;
    }

    @Override
    protected ContentProcessor.Factory createFactory(Facebook facebook, Messenger messenger) {
        ContentProcessor.Creator creator = createCreator(facebook, messenger);
        return new ContentProcessorFactory(creator) {

            @Override
            public ContentProcessor getContentProcessor(Content content) {
                ContentProcessor cpu;
                String msgType = content.getType();
                if (content instanceof Command) {
                    String cmd = ((Command) content).getCmd();
                    // assert cmd != null && !cmd.isEmpty() : "command name error: " + cmd;
                    cpu = getCommandProcessor(msgType, cmd);
                    if (cpu != null) {
                        return cpu;
                    } else if (content instanceof GroupCommand/* || content.containsKey("group")*/) {
                        // assert !name.equals("group") : "command name error: " + content;
                        cpu = getCommandProcessor(msgType, "group");
                        if (cpu != null) {
                            return cpu;
                        }
                    }
                }
                // content processor
                return getContentProcessor(msgType);
            }
        };
    }
    protected abstract ContentProcessor.Creator createCreator(Facebook facebook, Messenger messenger);

    @Override
    public List<InstantMessage> processInstantMessage(InstantMessage iMsg, ReliableMessage rMsg) {
        Messenger messenger = getMessenger();
        assert messenger != null : "twins not ready";
        // 1. process content
        List<Content> responses = messenger.processContent(iMsg.getContent(), rMsg);
        if (responses == null || responses.isEmpty()) {
            // nothing to respond
            return null;
        }
        // 2. select a local user to build message
        ID sender = iMsg.getSender();
        ID receiver = iMsg.getReceiver();
        User user = selectLocalUser(receiver);
        if (user == null) {
            assert false : "receiver error: " + receiver;
            return null;
        }
        // 3. pack all responses in one message
        Envelope env = Envelope.create(user.getIdentifier(), sender, null);
        Content body;
        if (responses.size() == 1) {
            body = responses.get(0);
        } else {
            body = ArrayContent.create(responses);
        }
        iMsg = InstantMessage.create(env, body);
        List<InstantMessage> messages = new ArrayList<>();
        messages.add(iMsg);
        return messages;
    }

    @Override
    public List<Content> processContent(Content content, ReliableMessage rMsg) {
        List<Content> responses = super.processContent(content, rMsg);

        // check sender's document times from the message
        // to make sure the user info synchronized
        checkVisaTime(content, rMsg);

        return responses;
    }

    private boolean checkVisaTime(Content content, ReliableMessage rMsg) {
        EntityChecker checker = getEntityChecker();
        if (checker == null) {
            assert false : "should not happen";
            return false;
        }
        boolean docUpdated = false;
        // check sender document time
        Date lastDocumentTime = rMsg.getDateTime("SDT", null);
        if (lastDocumentTime != null) {
            Date now = new Date();
            if (lastDocumentTime.getTime() > now.getTime()) {
                // calibrate the clock
                lastDocumentTime = now;
            }
            ID sender = rMsg.getSender();
            docUpdated = checker.setLastDocumentTime(sender, lastDocumentTime);
            // check whether it needs update now
            if (docUpdated) {
                // checking for new visa
                Facebook facebook = getFacebook();
                facebook.getDocuments(sender);
            }
        }
        return docUpdated;
    }

}
