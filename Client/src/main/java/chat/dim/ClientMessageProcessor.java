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
package chat.dim;

import java.util.ArrayList;
import java.util.List;

import chat.dim.cpu.ClientContentProcessorCreator;
import chat.dim.cpu.ContentProcessor;
import chat.dim.mkm.User;
import chat.dim.protocol.Content;
import chat.dim.protocol.EntityType;
import chat.dim.protocol.HandshakeCommand;
import chat.dim.protocol.ID;
import chat.dim.protocol.ReceiptCommand;
import chat.dim.protocol.ReliableMessage;
import chat.dim.protocol.SecureMessage;
import chat.dim.protocol.TextContent;

public class ClientMessageProcessor extends MessageProcessor {

    public ClientMessageProcessor(Facebook facebook, Messenger messenger) {
        super(facebook, messenger);
    }

    @Override
    protected CommonMessenger getMessenger() {
        return (CommonMessenger) super.getMessenger();
    }

    @Override
    public List<SecureMessage> processSecureMessage(SecureMessage sMsg, ReliableMessage rMsg) {
        try {
            return super.processSecureMessage(sMsg, rMsg);
        } catch (NullPointerException e) {
            String errMsg = e.getMessage();
            if (errMsg != null && errMsg.startsWith("receiver error")) {
                // not mine? ignore it
                return new ArrayList<>();
            } else {
                throw e;
            }
        }
    }

    @Override
    public List<Content> processContent(Content content, ReliableMessage rMsg) {
        List<Content> responses = super.processContent(content, rMsg);
        if (responses == null || responses.size() == 0) {
            // respond nothing
            return new ArrayList<>();
        } else if (responses.get(0) instanceof HandshakeCommand) {
            // urgent command
            return responses;
        }
        ID sender = rMsg.getSender();
        ID receiver = rMsg.getReceiver();
        User user = getFacebook().selectLocalUser(receiver);
        assert user != null : "receiver error: " + receiver;
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
        return new ArrayList<>();
    }

    @Override
    protected ContentProcessor.Creator createCreator() {
        return new ClientContentProcessorCreator(getFacebook(), getMessenger());
    }
}
