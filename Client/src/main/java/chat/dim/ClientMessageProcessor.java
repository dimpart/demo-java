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

import chat.dim.core.ContentProcessor;
import chat.dim.cpu.ClientContentProcessorCreator;
import chat.dim.mkm.User;
import chat.dim.protocol.Content;
import chat.dim.protocol.EntityType;
import chat.dim.protocol.HandshakeCommand;
import chat.dim.protocol.ID;
import chat.dim.protocol.ReceiptCommand;
import chat.dim.protocol.ReliableMessage;
import chat.dim.protocol.TextContent;

public class ClientMessageProcessor extends MessageProcessor {

    public ClientMessageProcessor(Facebook facebook, Messenger messenger) {
        super(facebook, messenger);
    }

    @Override
    protected CommonMessenger getMessenger() {
        return (CommonMessenger) super.getMessenger();
    }

    private void checkGroupTimes(Content content, ReliableMessage rMsg) {
        ID group = content.getGroup();
        if (group == null) {
            return;
        }
        Facebook facebook = getFacebook();
        ClientArchivist archivist = (ClientArchivist) facebook.getArchivist();
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
            docUpdated = archivist.setLastDocumentTime(group, lastDocumentTime);
        }
        // check group history time
        Date lastHistoryTime = rMsg.getDateTime("GHT", null);
        if (lastHistoryTime != null) {
            if (lastHistoryTime.after(now)) {
                // calibrate the clock
                lastHistoryTime = now;
            }
            memUpdated = archivist.setLastGroupHistoryTime(group, lastHistoryTime);
        }
        // check whether needs update
        if (docUpdated) {
            facebook.getDocuments(group);
        }
        if (memUpdated) {
            archivist.setLastActiveMember(group, rMsg.getSender());
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
        for (Content res : responses) {
            if (res == null) {
                // should not happen
                continue;
            } else if (res instanceof ReceiptCommand) {
                if (EntityType.STATION.equals(sender.getType())) {
                    // no need to respond receipt to station
                    continue;
                } else if (EntityType.BOT.equals(sender.getType())) {
                    // no need to respond receipt to a bot
                    continue;
                }
            } else if (res  instanceof TextContent) {
                if (EntityType.STATION.equals(sender.getType())) {
                    // no need to respond text message to station
                    continue;
                } else if (EntityType.BOT.equals(sender.getType())) {
                    // no need to respond text message to a bot
                    continue;
                }
            }
            // normal response
            messenger.sendContent(receiver, sender, res, 1);
        }
        // DON'T respond to station directly
        return null;
    }

    @Override
    protected ContentProcessor.Creator createCreator() {
        return new ClientContentProcessorCreator(getFacebook(), getMessenger());
    }
}
