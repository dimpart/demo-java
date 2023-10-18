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
import java.util.List;

import chat.dim.CommonFacebook;
import chat.dim.CommonMessenger;
import chat.dim.Register;
import chat.dim.dbi.AccountDBI;
import chat.dim.mkm.Station;
import chat.dim.mkm.User;
import chat.dim.protocol.Command;
import chat.dim.protocol.Content;
import chat.dim.protocol.Document;
import chat.dim.protocol.DocumentCommand;
import chat.dim.protocol.ForwardContent;
import chat.dim.protocol.GroupCommand;
import chat.dim.protocol.ID;
import chat.dim.protocol.Meta;
import chat.dim.protocol.MetaCommand;
import chat.dim.protocol.ReliableMessage;
import chat.dim.protocol.group.InviteCommand;
import chat.dim.protocol.group.ResetCommand;
import chat.dim.type.Pair;
import chat.dim.utils.Log;

public class GroupManager {

    protected final GroupDelegate delegate;

    protected final GroupPacker packer;

    protected final GroupHelper helper;
    protected final GroupHistoryBuilder builder;

    public GroupManager(GroupDelegate dataSource) {
        super();
        delegate = dataSource;
        packer = createPacker();
        helper = createHelper();
        builder = createBuilder();
    }

    // override for customized packer
    protected GroupPacker createPacker() {
        return new GroupPacker(delegate);
    }

    // override for customized helper
    protected GroupHelper createHelper() {
        return new GroupHelper(delegate);
    }

    // override for customized builder
    protected GroupHistoryBuilder createBuilder() {
        return new GroupHistoryBuilder(delegate);
    }

    protected CommonFacebook getFacebook() {
        return delegate.getFacebook();
    }
    protected CommonMessenger getMessenger() {
        return delegate.getMessenger();
    }

    protected AccountDBI getDatabase() {
        return delegate.getFacebook().getDatabase();
    }

    /**
     *  Create new group with members
     *  (broadcast document & members to all members and neighbor station)
     *
     * @param members - initial group members
     * @return new group ID
     */
    public ID createGroup(List<ID> members) {
        assert members.size() > 1 : "not enough members: " + members;

        //
        //  0. get current user
        //
        User user = getFacebook().getCurrentUser();
        if (user == null) {
            assert false : "failed to get current user";
            return null;
        }
        ID founder = user.getIdentifier();

        //
        //  1. check founder (owner)
        //
        int pos = members.indexOf(founder);
        if (pos < 0) {
            // put me in the first position
            members.add(0, founder);
        } else if (pos > 0) {
            // move me to the front
            members.remove(pos);
            members.add(0, founder);
        }
        String groupName = delegate.buildGroupName(members);

        //
        //  2. create group with name
        //
        Register register = new Register(getDatabase());
        ID group = register.createGroup(founder, groupName);
        Log.info("new group: " + group + " (" + groupName + "), founder: " + founder);

        //
        //  3. upload meta+document to neighbor station(s)
        //  DISCUSS: should we let the neighbor stations know the group info?
        //
        Meta meta = delegate.getMeta(group);
        Document doc = delegate.getDocument(group, "*");
        Command content;
        if (doc != null) {
            content = DocumentCommand.response(group, meta, doc);
            sendCommand(content, Station.ANY);          // to neighbor(s)
        } else if (meta != null) {
            content = MetaCommand.response(group, meta);
            sendCommand(content, Station.ANY);          // to neighbor(s)
        } else {
            assert false : " failed to get group info: " + group;
            return null;
        }

        //
        //  4. create & broadcast 'reset' group command with new members
        //
        if (resetMembers(group, members)) {
            Log.info("created group " + group + " with " + members.size() + " members");
        } else {
            Log.error("failed to create group " + group + " with " + members.size() + " members");
        }

        return group;
    }

    // DISCUSS: should we let the neighbor stations know the group info?
    //      (A) if we do this, it can provide a convenience that,
    //          when someone receive a message from an unknown group,
    //          it can query the group info from the neighbor immediately;
    //          and its potential risk is that anyone not in the group can also
    //          know the group info (only the group ID, name, and admins, ...)
    //      (B) but, if we don't let the station knows it,
    //          then we must shared the group info with our members themself;
    //          and if none of them is online, you cannot get the newest info
    //          immediately until someone online again.

    /**
     *  Reset group members
     *  (broadcast new group history to all members)
     *
     * @param group      - group ID
     * @param newMembers - new member list
     * @return false on error
     */
    public boolean resetMembers(ID group, List<ID> newMembers) {
        assert group.isGroup() && newMembers.size() > 0 : "params error: " + group + ", " + newMembers;

        //
        //  0. get current user
        //
        User user = getFacebook().getCurrentUser();
        if (user == null) {
            assert false : "failed to get current user";
            return false;
        }
        ID me = user.getIdentifier();

        // check member list
        ID first = newMembers.get(0);
        boolean ok = delegate.isOwner(first, group);
        if (!ok) {
            assert false : "group owner must be the first member: " + group;
            return false;
        }
        // member list OK, check expelled members
        List<ID> oldMembers = delegate.getMembers(group);
        List<ID> expelList = new ArrayList<>();
        for (ID item : oldMembers) {
            if (!newMembers.contains(item)) {
                expelList.add(item);
            }
        }

        //
        //  1. check permission
        //
        boolean isOwner = me.equals(first);
        boolean isAdmin = delegate.isAdministrator(me, group);
        boolean isBot = delegate.isAssistant(me, group);
        boolean canReset = isOwner || isAdmin;
        if (!canReset) {
            assert false : "cannot reset members of group: " + group;
            return false;
        }
        // only the owner or admin can reset group members
        assert !isBot : "group bot cannot reset members: " + group + ", " + me;

        //
        //  2. build 'reset' command
        //
        Pair<ResetCommand, ReliableMessage> pair = builder.buildResetCommand(group, newMembers);
        if (pair == null || pair.first == null || pair.second == null) {
            assert false : "failed to build 'reset' command for group: " + group;
            return false;
        }
        ResetCommand reset = pair.first;
        ReliableMessage rMsg = pair.second;

        //
        //  3. save 'reset' command, and update new members
        //
        if (!helper.saveGroupHistory(reset, rMsg, group)) {
            assert false : "failed to save 'reset' command for group: " + group;
            return false;
        } else if (!delegate.saveMembers(newMembers, group)) {
            assert false : "failed to update members of group: " + group;
            return false;
        } else {
            Log.info("group members updated: " + group + ", " + newMembers.size());
        }

        //
        //  4. forward all group history
        //
        List<ReliableMessage> messages = builder.buildGroupHistories(group);
        ForwardContent forward = ForwardContent.create(messages);

        List<ID> bots = delegate.getAssistants(group);
        if (bots != null && bots.size() > 0) {
            // let the group bots know the newest member ID list,
            // so they can split group message correctly for us.
            sendCommand(forward, bots);                 // to all assistants
        } else {
            // group bots not exist,
            // send the command to all members
            sendCommand(forward, newMembers);           // to new members
            sendCommand(forward, expelList);            // to removed members
        }

        return true;
    }

    /**
     *  Invite new members to this group
     *
     * @param group      - group ID
     * @param newMembers - inviting member list
     * @return false on error
     */
    public boolean inviteMembers(ID group, List<ID> newMembers) {
        assert group.isGroup() && newMembers.size() > 0 : "params error: " + group + ", " + newMembers;

        //
        //  0. get current user
        //
        User user = getFacebook().getCurrentUser();
        if (user == null) {
            assert false : "failed to get current user";
            return false;
        }
        ID me = user.getIdentifier();

        List<ID> oldMembers = delegate.getMembers(group);

        boolean isOwner = delegate.isOwner(me, group);
        boolean isAdmin = delegate.isAdministrator(me, group);
        boolean isMember = delegate.isMember(me, group);

        //
        //  1. check permission
        //
        boolean canReset = isOwner || isAdmin;
        if (canReset) {
            // You are the owner/admin, then
            // append new members and 'reset' the group
            List<ID> members = new ArrayList<>(oldMembers);
            for (ID item : newMembers) {
                if (!members.contains(item)) {
                    members.add(item);
                }
            }
            return resetMembers(group, members);
        } else if (!isMember) {
            assert false : "cannot invite member into group: " + group;
            return false;
        }
        // invited by ordinary member

        //
        //  2. build 'invite' command
        //
        InviteCommand invite = GroupCommand.invite(group, newMembers);
        ReliableMessage rMsg = packer.packMessage(invite, me);
        if (rMsg == null) {
            assert false : "failed to build 'invite' command for group: " + group;
            return false;
        } else if (!helper.saveGroupHistory(invite, rMsg, group)) {
            assert false : "failed to save 'invite' command for group: " + group;
            return false;
        }
        ForwardContent forward = ForwardContent.create(rMsg);

        //
        //  3. forward group command(s)
        //
        List<ID> bots = delegate.getAssistants(group);
        if (bots != null && bots.size() > 0) {
            // let the group bots know the newest member ID list,
            // so they can split group message correctly for us.
            sendCommand(forward, bots);                 // to all assistants
            return true;
        }

        // forward 'invite' to old members
        sendCommand(forward, oldMembers);               // to old members

        // forward all group history to new members
        List<ReliableMessage> messages = builder.buildGroupHistories(group);
        forward = ForwardContent.create(messages);

        // TODO: remove that members already exist before sending?
        sendCommand(forward, newMembers);               // to new members
        return true;
    }

    /**
     *  Quit from this group
     *  (broadcast a 'quit' command to all members)
     *
     * @param group - group ID
     * @return false on error
     */
    public boolean quitGroup(ID group) {
        assert group.isGroup() : "group ID error: " + group;

        //
        //  0. get current user
        //
        User user = getFacebook().getCurrentUser();
        if (user == null) {
            assert false : "failed to get current user";
            return false;
        }
        ID me = user.getIdentifier();

        List<ID> members = delegate.getMembers(group);
        if (members == null || members.isEmpty()) {
            assert false : "failed to get members for group: " + group;
            return false;
        }

        boolean isOwner = delegate.isOwner(me, group);
        boolean isAdmin = delegate.isAdministrator(me, group);
        boolean isBot = delegate.isAssistant(me, group);
        boolean isMember = members.contains(me);

        //
        //  1. check permission
        //
        if (isOwner) {
            assert false : "owner cannot quit from group: " + group;
            return false;
        } else if (isAdmin) {
            assert false : "administrator cannot quit from group: " + group;
            return false;
        }
        assert !isBot : "group bot cannot quit: " + group + ", " + me;

        //
        //  2. update local storage
        //
        if (isMember) {
            Log.warning("quitting group: " + group + ", " + me);
            members = new ArrayList<>(members);
            members.remove(me);
            boolean ok = delegate.saveMembers(members, group);
            assert ok : "failed to save members for group: " + group;
        } else {
            Log.error("member not in group: " + group + ", " + me);
        }

        //
        //  3. build 'quit' command
        //
        Command content = GroupCommand.quit(group);
        ReliableMessage rMsg = packer.packMessage(content, me);
        if (rMsg == null) {
            assert false : "failed to pack group message: " + group;
            return false;
        }
        ForwardContent forward = ForwardContent.create(rMsg);

        //
        //  4. forward 'quit' command
        //
        List<ID> bots = delegate.getAssistants(group);
        if (bots != null && bots.size() > 0) {
            // let the group bots know the newest member ID list,
            // so they can split group message correctly for us.
            sendCommand(forward, bots);                 // to group bots
        } else {
            // group bots not exist,
            // send the command to all members directly
            sendCommand(forward, members);              // to all members
        }

        return true;
    }

    private void sendCommand(Content content, ID receiver) {
        List<ID> members = new ArrayList<>();
        members.add(receiver);
        sendCommand(content, members);
    }

    private void sendCommand(Content content, List<ID> members) {
        // 1. get sender
        User user = getFacebook().getCurrentUser();
        if (user == null) {
            assert false : "failed to get current user";
            return;
        }
        ID me = user.getIdentifier();
        // 2. send to all receivers
        CommonMessenger messenger = getMessenger();
        for (ID receiver : members) {
            if (me.equals(receiver)) {
                Log.info("skip cycled message: " + me + " => " + receiver);
                continue;
            }
            messenger.sendContent(me, receiver, content, 1);
        }
    }

}
