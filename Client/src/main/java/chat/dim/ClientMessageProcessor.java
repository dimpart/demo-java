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
package chat.dim;

import java.util.Date;
import java.util.List;

import chat.dim.cpu.ClientContentProcessorCreator;
import chat.dim.dkd.ContentProcessor;
import chat.dim.mkm.User;
import chat.dim.protocol.Content;
import chat.dim.protocol.EntityType;
import chat.dim.protocol.HandshakeCommand;
import chat.dim.protocol.ID;
import chat.dim.protocol.ReceiptCommand;
import chat.dim.protocol.ReliableMessage;
import chat.dim.protocol.TextContent;

public class ClientMessageProcessor extends CommonMessageProcessor {

    public ClientMessageProcessor(ClientFacebook facebook, ClientMessenger messenger) {
        super(facebook, messenger);
    }

    @Override
    protected ClientFacebook getFacebook() {
        return (ClientFacebook) super.getFacebook();
    }

    @Override
    protected ClientMessenger getMessenger() {
        return (ClientMessenger) super.getMessenger();
    }

    @Override
    protected ContentProcessor.Creator createCreator(Facebook facebook, Messenger messenger) {
        return new ClientContentProcessorCreator(facebook, messenger);
    }

    private void checkGroupTimes(Content content, ReliableMessage rMsg) {
        ID group = content.getGroup();
        if (group == null) {
            return;
        }
        ClientFacebook facebook = getFacebook();
        EntityChecker checker = facebook.getEntityChecker();
        if (checker == null) {
            assert false : "should not happen";
            return;
        }
        Date now = new Date();
        boolean docUpdated = false;
        boolean memUpdated = false;
        // check group document time
        Date lastDocumentTime = rMsg.getDateTime("GDT", null);
        if (lastDocumentTime != null) {
            if (lastDocumentTime.after(now)) {
                // calibrate the clock
                lastDocumentTime = now;
            }
            docUpdated = checker.setLastDocumentTime(group, lastDocumentTime);
        }
        // check group history time
        Date lastHistoryTime = rMsg.getDateTime("GHT", null);
        if (lastHistoryTime != null) {
            if (lastHistoryTime.after(now)) {
                // calibrate the clock
                lastHistoryTime = now;
            }
            memUpdated = checker.setLastGroupHistoryTime(group, lastHistoryTime);
        }
        // check whether needs update
        if (docUpdated) {
            facebook.getDocuments(group);
        }
        if (memUpdated) {
            checker.setLastActiveMember(group, rMsg.getSender());
            facebook.getMembers(group);
        }
    }

    @Override
    public List<Content> processContent(Content content, ReliableMessage rMsg) {
        List<Content> responses = super.processContent(content, rMsg);

        // check group document & history times from the message
        // to make sure the group info synchronized
        checkGroupTimes(content, rMsg);

        if (responses == null || responses.isEmpty()) {
            // respond nothing
            return responses;
        } else if (responses.get(0) instanceof HandshakeCommand) {
            // urgent command
            return responses;
        }
        ID sender = rMsg.getSender();
        ID receiver = rMsg.getReceiver();
        User user = getFacebook().selectLocalUser(receiver);
        if (user == null) {
            assert false : "receiver error: " + receiver;
            return null;
        }
        receiver = user.getIdentifier();
        CommonMessenger messenger = getMessenger();
        // check responses
        int sty = sender.getType();
        boolean fromBots = EntityType.STATION.equals(sty) && EntityType.BOT.equals(sty);
        for (Content res : responses) {
            if (res == null) {
                // should not happen
                continue;
            } else if (res instanceof ReceiptCommand) {
                if (fromBots) {
                    // no need to respond receipt to station/bot
                    continue;
                }
            } else if (res  instanceof TextContent) {
                if (fromBots) {
                    // no need to respond text message to a station/bot
                    continue;
                }
            }
            // normal response
            messenger.sendContent(res, receiver, sender, 1);
        }
        // DON'T respond to station directly
        return null;
    }

}
