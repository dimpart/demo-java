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

import chat.dim.dkd.AppCustomizedContent;
import chat.dim.plugins.ExtensionLoader;
import chat.dim.protocol.AnsCommand;
import chat.dim.protocol.BlockCommand;
import chat.dim.protocol.ContentType;
import chat.dim.protocol.HandshakeCommand;
import chat.dim.protocol.LoginCommand;
import chat.dim.protocol.MuteCommand;
import chat.dim.protocol.ReportCommand;
import chat.dim.protocol.group.QueryCommand;
import chat.dim.protocol.group.QueryGroupCommand;

/**
 *  Extensions Loader
 *  ~~~~~~~~~~~~~~~~~
 */
public class CommonExtensionLoader extends ExtensionLoader {

    @Override
    protected void registerCustomizedFactories() {

        // Application Customized
        setContentFactory(ContentType.CUSTOMIZED, "customized", AppCustomizedContent::new);
        setContentFactory(ContentType.APPLICATION, "application", AppCustomizedContent::new);

        //super.registerCustomizedFactories();
    }

    /**
     *  Command factories
     */
    @Override
    protected void registerCommandFactories() {
        super.registerCommandFactories();

        // ANS
        setCommandFactory(AnsCommand.ANS, AnsCommand::new);

        // Handshake
        setCommandFactory(HandshakeCommand.HANDSHAKE, HandshakeCommand::new);
        // Login
        setCommandFactory(LoginCommand.LOGIN, LoginCommand::new);

        // Mute
        setCommandFactory(MuteCommand.MUTE, MuteCommand::new);
        // Block
        setCommandFactory(BlockCommand.BLOCK, BlockCommand::new);

        // Report
        setCommandFactory(ReportCommand.REPORT, ReportCommand::new);
        setCommandFactory(ReportCommand.ONLINE, ReportCommand::new);
        setCommandFactory(ReportCommand.OFFLINE, ReportCommand::new);

        // Group command (deprecated)
        setCommandFactory(QueryCommand.QUERY, QueryGroupCommand::new);
    }

}
