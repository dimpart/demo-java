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
package chat.dim;

import java.util.Date;
import java.util.List;

import chat.dim.mkm.Station;
import chat.dim.mkm.User;
import chat.dim.protocol.Content;
import chat.dim.protocol.Document;
import chat.dim.protocol.DocumentCommand;
import chat.dim.protocol.GroupCommand;
import chat.dim.protocol.ID;
import chat.dim.protocol.InstantMessage;
import chat.dim.protocol.MetaCommand;
import chat.dim.protocol.ReliableMessage;
import chat.dim.protocol.group.QueryCommand;
import chat.dim.type.Pair;
import chat.dim.utils.Log;

public class ClientChecker extends EntityChecker {

    public ClientChecker(ClientFacebook facebook) {
        super(facebook);
    }

    @Override
    public boolean queryMeta(ID identifier) {
        if (!isMetaQueryExpired(identifier)) {
            // query not expired yet
            Log.debug("meta query not expired yet: " + identifier);
            return false;
        }
        Log.info("querying meta for: " + identifier);
        CommonMessenger messenger = getMessenger();
        Content content = MetaCommand.query(identifier);
        Pair<InstantMessage, ReliableMessage> pair;
        pair = messenger.sendContent(content, null, Station.ANY, 1);
        return pair != null && pair.second != null;
    }

    @Override
    public boolean queryDocuments(ID identifier, List<Document> documents) {
        if (!isDocumentQueryExpired(identifier)) {
            // query not expired yet
            Log.debug("document query not expired yet: " + identifier);
            return false;
        }
        Date lastTime = getLastDocumentTime(identifier, documents);
        Log.info("querying documents for: " + identifier + ", last time: " + lastTime);
        CommonMessenger messenger = getMessenger();
        Content content = DocumentCommand.query(identifier, lastTime);
        Pair<InstantMessage, ReliableMessage> pair;
        pair = messenger.sendContent(content, null, Station.ANY, 1);
        return pair != null && pair.second != null;
    }

    @Override
    public boolean queryMembers(ID group, List<ID> members) {
        if (!isMembersQueryExpired(group)) {
            // query not expired yet
            Log.debug("members query not expired yet: " + group);
            return false;
        }
        CommonFacebook facebook = getFacebook();
        User user = facebook.getCurrentUser();
        if (user == null) {
            assert false : "failed to get current user";
            return false;
        }
        ID me = user.getIdentifier();
        Date lastTime = getLastGroupHistoryTime(group);
        Log.info("querying members for group: " + group + ", last time: " + lastTime);
        // build query command for group members
        QueryCommand command = GroupCommand.query(group, lastTime);
        boolean ok;
        // 1. check group bots
        ok = queryMembersFromAssistants(me, command);
        if (ok) {
            return true;
        }
        // 2. check administrators
        ok = queryMembersFromAdministrators(me, command);
        if (ok) {
            return true;
        }
        // 3. check group owner
        ok = queryMembersFromOwner(me, command);
        if (ok) {
            return true;
        }
        // all failed, try last active member
        Pair<InstantMessage, ReliableMessage> pair = null;
        ID lastMember = getLastActiveMember(group);
        if (lastMember != null) {
            Log.info("querying members from: " + lastMember + ", group: " + group);
            CommonMessenger messenger = getMessenger();
            pair = messenger.sendContent(command, me, lastMember, 1);
        }
        Log.error("group not ready: " + group);
        return pair != null && pair.second != null;
    }

    protected boolean queryMembersFromAssistants(ID sender, QueryCommand command) {
        CommonFacebook facebook = getFacebook();
        CommonMessenger messenger = getMessenger();
        ID group = command.getGroup();
        assert group != null : "group command error: " + command;
        List<ID> bots = facebook.getAssistants(group);
        if (bots == null || bots.isEmpty()) {
            Log.warning("assistants not designated for group: " + group);
            return false;
        }
        int success = 0;
        Pair<InstantMessage, ReliableMessage> pair;
        // querying members from bots
        Log.info("querying members from bots: " + bots + ", group: " + group);
        for (ID receiver : bots) {
            if (sender.equals(receiver)) {
                Log.warning("ignore cycled querying: " + sender + ", group: " + group);
                continue;
            }
            pair = messenger.sendContent(command, sender, receiver, 1);
            if (pair != null && pair.second != null) {
                success += 1;
            }
        }
        if (success == 0) {
            // failed
            return false;
        }
        ID lastMember = getLastActiveMember(group);
        if (lastMember != null && !bots.contains(lastMember)) {
            Log.info("querying members from: " + lastMember + ", group: " + group);
            messenger.sendContent(command, sender, lastMember, 1);
        }
        return true;
    }

    protected boolean queryMembersFromAdministrators(ID sender, QueryCommand command) {
        CommonFacebook facebook = getFacebook();
        CommonMessenger messenger = getMessenger();
        ID group = command.getGroup();
        assert group != null : "group command error: " + command;
        List<ID> admins = facebook.getAdministrators(group);
        if (admins == null || admins.isEmpty()) {
            Log.warning("administrators not found for group: " + group);
            return false;
        }
        int success = 0;
        Pair<InstantMessage, ReliableMessage> pair;
        // querying members from admins
        Log.info("querying members from admins: " + admins + ", group: " + group);
        for (ID receiver : admins) {
            if (sender.equals(receiver)) {
                Log.warning("ignore cycled querying: " + sender + ", group: " + group);
                continue;
            }
            pair = messenger.sendContent(command, sender, receiver, 1);
            if (pair != null && pair.second != null) {
                success += 1;
            }
        }
        if (success == 0) {
            // failed
            return false;
        }
        ID lastMember = getLastActiveMember(group);
        if (lastMember != null && !admins.contains(lastMember)) {
            Log.info("querying members from: " + lastMember + ", group: " + group);
            messenger.sendContent(command, sender, lastMember, 1);
        }
        return true;
    }

    protected boolean queryMembersFromOwner(ID sender, QueryCommand command) {
        CommonFacebook facebook = getFacebook();
        CommonMessenger messenger = getMessenger();
        ID group = command.getGroup();
        assert group != null : "group command error: " + command;
        ID owner = facebook.getOwner(group);
        if (owner == null) {
            Log.warning("owner not found for group: " + group);
            return false;
        } else if (owner.equals(sender)) {
            Log.error("you are the owner of group: " + group);
            return false;
        }
        Pair<InstantMessage, ReliableMessage> pair;
        // querying members from owner
        Log.info("querying members from owner: " + owner + ", group: " + group);
        pair = messenger.sendContent(command, sender, owner, 1);
        if (pair == null || pair.second == null) {
            // failed
            return false;
        }
        ID lastMember = getLastActiveMember(group);
        if (lastMember != null && !lastMember.equals(owner)) {
            Log.info("querying members from: " + lastMember + ", group: " + group);
            messenger.sendContent(command, sender, lastMember, 1);
        }
        return true;
    }

}
