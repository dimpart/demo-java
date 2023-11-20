/* license: https://mit-license.org
 *
 *  DIM-SDK : Decentralized Instant Messaging Software Development Kit
 *
 *                                Written in 2023 by Moky <albert.moky@gmail.com>
 *
 * ==============================================================================
 * The MIT License (MIT)
 *
 * Copyright (c) 2023 Albert Moky
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
package chat.dim.group;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import chat.dim.dbi.AccountDBI;
import chat.dim.mkm.DocumentHelper;
import chat.dim.protocol.Bulletin;
import chat.dim.protocol.GroupCommand;
import chat.dim.protocol.ID;
import chat.dim.protocol.ReliableMessage;
import chat.dim.protocol.group.ResetCommand;
import chat.dim.protocol.group.ResignCommand;
import chat.dim.type.Pair;
import chat.dim.utils.Log;

public class GroupHelper {

    protected final GroupDelegate delegate;

    public GroupHelper(GroupDelegate dataSource) {
        super();
        delegate = dataSource;
    }

    protected AccountDBI getDatabase() {
        return delegate.getFacebook().getArchivist().getDatabase();
    }

    //
    //  Group History Command
    //
    public boolean saveGroupHistory(GroupCommand content, ReliableMessage rMsg, ID group) {
        assert group.equals(content.getGroup()) : "group ID error: " + group + ", " + content;
        if (isCommandExpired(content)) {
            Log.warning("drop expired command: " + content.getCmd() + ", " + rMsg.getSender() + " => " + group);
            return false;
        }
        Date cmdTime = content.getTime();
        if (cmdTime == null) {
            assert false : "group command error: " + content;
        } else {
            // calibrate the clock
            // make sure the command time is not in the far future
            long current = System.currentTimeMillis() + 65536;
            if (cmdTime.getTime() > current) {
                assert false : "group command time error: " + cmdTime + ", " + content;
                return false;
            }
        }
        AccountDBI db = getDatabase();
        if (content instanceof ResetCommand) {
            Log.warning("cleaning group history for 'reset' command: " + rMsg.getSender() + " => " + group);
            return db.clearGroupMemberHistories(group);
        }
        return db.saveGroupHistory(content, rMsg, group);
    }
    public List<Pair<GroupCommand, ReliableMessage>> getGroupHistories(ID group) {
        AccountDBI db = getDatabase();
        return db.getGroupHistories(group);
    }
    public Pair<ResetCommand, ReliableMessage> getResetCommandMessage(ID group) {
        AccountDBI db = getDatabase();
        return db.getResetCommandMessage(group);
    }
    boolean clearGroupMemberHistories(ID group) {
        AccountDBI db = getDatabase();
        return db.clearGroupMemberHistories(group);
    }
    boolean clearGroupAdminHistories(ID group) {
        AccountDBI db = getDatabase();
        return db.clearGroupAdminHistories(group);
    }

    //
    //  command time
    //  (all group commands received must after the cached 'reset' command)
    //
    public boolean isCommandExpired(GroupCommand content) {
        ID group = content.getGroup();
        if (group == null) {
            assert false : "group content error: " + content;
            return true;
        }
        if (content instanceof ResignCommand) {
            // administrator command, check with document time
            Bulletin doc = delegate.getBulletin(group);
            if (doc == null) {
                assert false : "group document not exists: " + group;
                return true;
            }
            return DocumentHelper.isBefore(doc.getTime(), content.getTime());
        }
        // membership command, check with reset command
        Pair<ResetCommand, ReliableMessage> pair = getResetCommandMessage(group);
        ResetCommand cmd = pair.first;
        //ReliableMessage msg = pair.second;
        if (cmd == null/* || msg == null*/) {
            return false;
        }
        return DocumentHelper.isBefore(cmd.getTime(), content.getTime());
    }

    public static List<ID> getCommandMembers(GroupCommand content) {
        // get from 'members'
        List<ID> members = content.getMembers();
        if (members == null) {
            members = new ArrayList<>();
            // get from 'member'
            ID single = content.getMember();
            if (single != null) {
                members.add(single);
            }
        }
        return members;
    }

}
