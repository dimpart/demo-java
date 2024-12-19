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

import java.util.Date;
import java.util.List;

import chat.dim.cpu.GeneralContentProcessorFactory;
import chat.dim.dkd.ContentProcessor;
import chat.dim.protocol.Content;
import chat.dim.protocol.ID;
import chat.dim.protocol.ReliableMessage;

public abstract class CommonMessageProcessor extends MessageProcessor {

    public CommonMessageProcessor(CommonFacebook facebook, CommonMessenger messenger) {
        super(facebook, messenger);
    }

    @Override
    protected CommonFacebook getFacebook() {
        return (CommonFacebook) super.getFacebook();
    }

    @Override
    protected CommonMessenger getMessenger() {
        return (CommonMessenger) super.getMessenger();
    }

    @Override
    protected ContentProcessor.Factory createFactory(Facebook facebook, Messenger messenger) {
        ContentProcessor.Creator creator = createCreator(facebook, messenger);
        return new GeneralContentProcessorFactory(creator);
    }
    protected abstract ContentProcessor.Creator createCreator(Facebook facebook, Messenger messenger);

    private boolean checkVisaTime(Content content, ReliableMessage rMsg) {
        CommonFacebook facebook = getFacebook();
        EntityChecker checker = facebook.getEntityChecker();
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
            // check whether needs update
            if (docUpdated) {
                // checking for new isa
                facebook.getDocuments(sender);
            }
        }
        return docUpdated;
    }

    @Override
    public List<Content> processContent(Content content, ReliableMessage rMsg) {
        List<Content> responses = super.processContent(content, rMsg);

        // check sender's document times from the message
        // to make sure the user info synchronized
        checkVisaTime(content, rMsg);

        return responses;
    }
}
