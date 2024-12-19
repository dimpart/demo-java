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

import chat.dim.ClientMessenger;
import chat.dim.Facebook;
import chat.dim.Messenger;
import chat.dim.dbi.SessionDBI;
import chat.dim.protocol.Content;
import chat.dim.protocol.ID;
import chat.dim.protocol.LoginCommand;
import chat.dim.protocol.ReliableMessage;
import chat.dim.utils.Log;

public class LoginCommandProcessor extends BaseCommandProcessor {

    public LoginCommandProcessor(Facebook facebook, Messenger messenger) {
        super(facebook, messenger);
    }

    @Override
    protected ClientMessenger getMessenger() {
        return (ClientMessenger) super.getMessenger();
    }

    private SessionDBI getDatabase() {
        return getMessenger().getSession().getDatabase();
    }

    @Override
    public List<Content> processContent(Content content, ReliableMessage rMsg) {
        assert content instanceof LoginCommand : "login command error: " + content;
        LoginCommand command = (LoginCommand) content;
        ID sender = command.getIdentifier();
        assert rMsg.getSender().equals(sender) : "sender not match: " + sender + ", " + rMsg.getSender();
        // save login command to session db
        SessionDBI db = getDatabase();
        if (db.saveLoginCommandMessage(sender, command, rMsg)) {
            Log.info("saved login command for user: " + sender);
        } else {
            Log.error("failed to save login command: " + sender + ", " + command);
        }
        // no need to response login command
        return null;
    }
}
