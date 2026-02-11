/* license: https://mit-license.org
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
package chat.dim.compat;

import chat.dim.dkd.group.FireGroupCommand;
import chat.dim.dkd.group.HireGroupCommand;
import chat.dim.dkd.group.QueryGroupCommand;
import chat.dim.dkd.group.ResignGroupCommand;
import chat.dim.plugins.ExtensionLoader;
import chat.dim.protocol.AnsCommand;
import chat.dim.protocol.BlockCommand;
import chat.dim.protocol.Command;
import chat.dim.protocol.Content;
import chat.dim.protocol.ContentType;
import chat.dim.protocol.HandshakeCommand;
import chat.dim.protocol.LoginCommand;
import chat.dim.protocol.MuteCommand;
import chat.dim.protocol.ReportCommand;
import chat.dim.protocol.group.GroupCommand;
import chat.dim.protocol.group.QueryCommand;

/**
 *  Extensions Loader
 *  ~~~~~~~~~~~~~~~~~
 */
public class CommonExtensionLoader extends ExtensionLoader {

    private void copyContentFactory(String type, String alias) {
        Content.Factory factory = Content.getFactory(type);
        assert factory != null : "content factory not exists: " + type;
        Content.setFactory(alias, factory);
    }

    @Override
    protected void registerContentFactories() {
        super.registerContentFactories();

        // Text
        copyContentFactory(ContentType.TEXT, "text");

        // File
        copyContentFactory(ContentType.FILE, "file");
        // Image
        copyContentFactory(ContentType.IMAGE, "image");
        // Audio
        copyContentFactory(ContentType.AUDIO, "audio");
        // Video
        copyContentFactory(ContentType.VIDEO, "video");

        // Web Page
        copyContentFactory(ContentType.PAGE, "page");

        // Name Card
        copyContentFactory(ContentType.NAME_CARD, "card");

        // Quote
        copyContentFactory(ContentType.QUOTE, "quote");

        // Money
        copyContentFactory(ContentType.MONEY, "money");
        copyContentFactory(ContentType.TRANSFER, "transfer");
        // ...

        // Command
        copyContentFactory(ContentType.COMMAND, "command");

        // History Command
        copyContentFactory(ContentType.HISTORY, "history");

        // Content Array
        copyContentFactory(ContentType.ARRAY, "array");

        // Combine and Forward
        copyContentFactory(ContentType.COMBINE_FORWARD, "combine");

        // Top-Secret
        copyContentFactory(ContentType.FORWARD, "forward");

        // unknown content type
        copyContentFactory(ContentType.ANY, "*");
    }

    @Override
    protected void registerCustomizedFactories() {
        super.registerCustomizedFactories();

        // Application Customized
        copyContentFactory(ContentType.CUSTOMIZED, "customized");
        copyContentFactory(ContentType.CUSTOMIZED, "application");
        copyContentFactory(ContentType.CUSTOMIZED, ContentType.APPLICATION);

    }

    /**
     *  Command factories
     */
    @Override
    protected void registerCommandFactories() {
        super.registerCommandFactories();

        // ANS
        Command.setFactory(AnsCommand.ANS, AnsCommand::new);

        // Handshake
        Command.setFactory(HandshakeCommand.HANDSHAKE, HandshakeCommand::new);
        // Login
        Command.setFactory(LoginCommand.LOGIN, LoginCommand::new);

        // Mute
        Command.setFactory(MuteCommand.MUTE, MuteCommand::new);
        // Block
        Command.setFactory(BlockCommand.BLOCK, BlockCommand::new);

        // Report
        Command.setFactory(ReportCommand.REPORT,  ReportCommand::new);
        Command.setFactory(ReportCommand.ONLINE,  ReportCommand::new);
        Command.setFactory(ReportCommand.OFFLINE, ReportCommand::new);

        // Group command (deprecated)
        Command.setFactory(QueryCommand.QUERY,   QueryGroupCommand::new);
        // Group Admin Commands
        Command.setFactory(GroupCommand.HIRE,    HireGroupCommand::new);
        Command.setFactory(GroupCommand.FIRE,    FireGroupCommand::new);
        Command.setFactory(GroupCommand.RESIGN,  ResignGroupCommand::new);
    }

}
