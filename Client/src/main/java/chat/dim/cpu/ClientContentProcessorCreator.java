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

import chat.dim.Facebook;
import chat.dim.Messenger;
import chat.dim.cpu.app.GroupHistoryHandler;
import chat.dim.cpu.group.ExpelCommandProcessor;
import chat.dim.cpu.group.InviteCommandProcessor;
import chat.dim.cpu.group.JoinCommandProcessor;
import chat.dim.cpu.group.QueryCommandProcessor;
import chat.dim.cpu.group.QuitCommandProcessor;
import chat.dim.cpu.group.ResetCommandProcessor;
import chat.dim.cpu.group.ResignCommandProcessor;
import chat.dim.dkd.ContentProcessor;
import chat.dim.protocol.AnsCommand;
import chat.dim.protocol.Command;
import chat.dim.protocol.ContentType;
import chat.dim.protocol.GroupCommand;
import chat.dim.protocol.HandshakeCommand;
import chat.dim.protocol.LoginCommand;
import chat.dim.protocol.group.GroupHistory;
import chat.dim.protocol.group.QueryCommand;

public class ClientContentProcessorCreator extends BaseContentProcessorCreator {

    public ClientContentProcessorCreator(Facebook facebook, Messenger messenger) {
        super(facebook, messenger);
    }

    protected AppCustomizedProcessor createCustomizedContentProcessor(Facebook facebook, Messenger messenger) {
        AppCustomizedProcessor cpu = new AppCustomizedProcessor(facebook, messenger);

        // 'chat.dim.group:history'
        cpu.setHandler(
                GroupHistory.APP,
                GroupHistory.MOD,
                new GroupHistoryHandler(facebook, messenger)
        );

        return cpu;
    }

    @Override
    public ContentProcessor createContentProcessor(String msgType) {
        switch (msgType) {

            // application customized
            case ContentType.APPLICATION:
            case "application":
            case ContentType.CUSTOMIZED:
            case "customized":
                return createCustomizedContentProcessor(getFacebook(), getMessenger());

            // history command
            case ContentType.HISTORY:
            case "history":
                return new HistoryCommandProcessor(getFacebook(), getMessenger());
        }
        // others
        return super.createContentProcessor(msgType);
    }

    @Override
    public ContentProcessor createCommandProcessor(String type, String name) {
        switch (name) {

            case Command.RECEIPT:
                return new ReceiptCommandProcessor(getFacebook(), getMessenger());
            case HandshakeCommand.HANDSHAKE:
                return new HandshakeCommandProcessor(getFacebook(), getMessenger());
            case LoginCommand.LOGIN:
                return new LoginCommandProcessor(getFacebook(), getMessenger());
            case AnsCommand.ANS:
                return new AnsCommandProcessor(getFacebook(), getMessenger());

            // group commands
            case "group":
                return new GroupCommandProcessor(getFacebook(), getMessenger());
            case GroupCommand.INVITE:
                return new InviteCommandProcessor(getFacebook(), getMessenger());
            case GroupCommand.EXPEL:
                // Deprecated (use 'reset' instead)
                return new ExpelCommandProcessor(getFacebook(), getMessenger());
            case GroupCommand.JOIN:
                return new JoinCommandProcessor(getFacebook(), getMessenger());
            case GroupCommand.QUIT:
                return new QuitCommandProcessor(getFacebook(), getMessenger());
            case QueryCommand.QUERY:
                return new QueryCommandProcessor(getFacebook(), getMessenger());
            case GroupCommand.RESET:
                return new ResetCommandProcessor(getFacebook(), getMessenger());
            case GroupCommand.RESIGN:
                return new ResignCommandProcessor(getFacebook(), getMessenger());
        }
        // others
        return super.createCommandProcessor(type, name);
    }
}
