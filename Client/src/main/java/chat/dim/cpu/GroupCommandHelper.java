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
package chat.dim.cpu;

import java.util.ArrayList;
import java.util.List;

import chat.dim.CommonFacebook;
import chat.dim.CommonMessenger;
import chat.dim.Facebook;
import chat.dim.Messenger;
import chat.dim.core.TwinsHelper;
import chat.dim.dbi.AccountDBI;
import chat.dim.protocol.Document;
import chat.dim.protocol.GroupCommand;
import chat.dim.protocol.ID;
import chat.dim.protocol.Meta;
import chat.dim.protocol.ReliableMessage;
import chat.dim.protocol.group.ResetCommand;
import chat.dim.protocol.group.ResignCommand;
import chat.dim.type.Pair;
import chat.dim.utils.Log;

public class GroupCommandHelper extends TwinsHelper {

    public GroupCommandHelper(Facebook facebook, Messenger messenger) {
        super(facebook, messenger);
    }

    protected CommonFacebook getFacebook() {
        Facebook facebook = super.getFacebook();
        assert facebook instanceof CommonFacebook : "facebook error: " + facebook;
        return (CommonFacebook) facebook;
    }

    protected CommonMessenger getMessenger() {
        Messenger messenger = super.getMessenger();
        assert messenger instanceof CommonMessenger : "messenger error: " + messenger;
        return (CommonMessenger) messenger;
    }

    /**
     *  Get group meta
     *  if not found, query it from any station
     */
    public Meta getMeta(ID group) {
        Meta meta = getFacebook().getMeta(group);
        if (meta == null) {
            getMessenger().queryMeta(group);
        }
        return meta;
    }

    /**
     *  Get group document
     *  if not found, query it from any station
     */
    public Document getDocument(ID group) {
        Document doc = getFacebook().getDocument(group, "*");
        if (doc == null) {
            getMessenger().queryDocument(group);
        }
        return doc;
    }

    /**
     *  Get group owner
     *  when bulletin document exists
     */
    public ID getOwner(ID group) {
        Document doc = getDocument(group);
        if (doc == null) {
            // the owner(founder) should be set in the bulletin document of group
            return null;
        }
        return getFacebook().getOwner(group);
    }

    /**
     *  Get group bots
     *  when bulletin document exists
     */
    public List<ID> getAssistants(ID group) {
        Document doc = getDocument(group);
        if (doc == null) {
            // the group assistants should be set in the bulletin document
            return null;
        }
        return getFacebook().getAssistants(group);
    }

    /**
     *  Get administrators
     *  when bulletin document exists
     */
    public List<ID> getAdministrators(ID group) {
        Document doc = getDocument(group);
        if (doc == null) {
            // the administrators should be set in the bulletin document
            return null;
        }
        AccountDBI db = getFacebook().getDatabase();
        return db.getAdministrators(group);
    }
    public boolean saveAdministrators(List<ID> admins, ID group) {
        AccountDBI db = getFacebook().getDatabase();
        return db.saveAdministrators(admins, group);
    }

    /**
     *  Get members when owner exists,
     *  if not found, query from bots/admins/owner
     */
    public List<ID> getMembers(ID group) {
        ID owner = getOwner(group);
        if (owner == null) {
            // the owner must exist before members
            return null;
        }
        List<ID> members = getFacebook().getMembers(group);
        if (members == null || members.isEmpty()) {
            getMessenger().queryMembers(group);
        }
        return members;
    }
    public boolean saveMembers(List<ID> members, ID group) {
        AccountDBI db = getFacebook().getDatabase();
        return db.saveMembers(members, group);
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
        AccountDBI db = getFacebook().getDatabase();
        if (content instanceof ResetCommand) {
            Log.warning("cleaning group history for 'reset' command: " + rMsg.getSender() + " => " + group);
            return db.clearGroupMemberHistories(group);
        }
        return db.saveGroupHistory(content, rMsg, group);
    }
    public List<Pair<GroupCommand, ReliableMessage>> getGroupHistories(ID group) {
        AccountDBI db = getFacebook().getDatabase();
        return db.getGroupHistories(group);
    }
    public Pair<ResetCommand, ReliableMessage> getResetCommandMessage(ID group) {
        AccountDBI db = getFacebook().getDatabase();
        return db.getResetCommandMessage(group);
    }
    boolean clearGroupMemberHistories(ID group) {
        AccountDBI db = getFacebook().getDatabase();
        return db.clearGroupMemberHistories(group);
    }
    boolean clearGroupAdminHistories(ID group) {
        AccountDBI db = getFacebook().getDatabase();
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
            Document bulletin = getDocument(group);
            if (bulletin == null) {
                assert false : "group document not exists: " + group;
                return true;
            }
            return AccountDBI.isExpired(bulletin.getTime(), content.getTime());
        }
        // membership command, check with reset command
        Pair<ResetCommand, ReliableMessage> pair = getResetCommandMessage(group);
        ResetCommand cmd = pair.first;
        //ReliableMessage msg = pair.second;
        if (cmd == null/* || msg == null*/) {
            return false;
        }
        return AccountDBI.isExpired(cmd.getTime(), content.getTime());
    }

    public static List<ID> getMembers(GroupCommand content) {
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
