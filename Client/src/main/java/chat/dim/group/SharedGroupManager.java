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
package chat.dim.group;

import java.lang.ref.WeakReference;
import java.util.List;

import chat.dim.CommonFacebook;
import chat.dim.CommonMessenger;
import chat.dim.mkm.Group;
import chat.dim.mkm.User;
import chat.dim.protocol.Bulletin;
import chat.dim.protocol.Document;
import chat.dim.protocol.ID;
import chat.dim.protocol.InstantMessage;
import chat.dim.protocol.Meta;
import chat.dim.protocol.ReliableMessage;
import chat.dim.type.Copier;


// Singleton
enum _SharedManager {

    INSTANCE;

    public static _SharedManager getInstance() {
        return INSTANCE;
    }

    _SharedManager() {
        manager = new SharedGroupManager();
    }

    final SharedGroupManager manager;
}


public final class SharedGroupManager implements Group.DataSource {

    public static SharedGroupManager getInstance() {
        return _SharedManager.getInstance().manager;
    }

    WeakReference<CommonFacebook> facebookRef;
    WeakReference<CommonMessenger> messengerRef;

    public CommonFacebook getFacebook() {
        WeakReference<CommonFacebook> ref = facebookRef;
        return ref == null ? null : ref.get();
    }
    public CommonMessenger getMessenger() {
        WeakReference<CommonMessenger> ref = messengerRef;
        return ref == null ? null : ref.get();
    }

    public void setFacebook(CommonFacebook facebook) {
        facebookRef = facebook == null ? null : new WeakReference<>(facebook);
        clearDelegates();
    }
    public void setMessenger(CommonMessenger messenger) {
        messengerRef = messenger == null ? null : new WeakReference<>(messenger);
        clearDelegates();
    }

    //
    //  delegates
    //
    private GroupDelegate groupDelegate = null;
    private GroupManager groupManager   = null;
    private AdminManager adminManager   = null;
    private GroupEmitter groupEmitter   = null;

    private void clearDelegates() {
        groupDelegate     = null;
        groupManager      = null;
        adminManager = null;
        groupEmitter      = null;
    }

    public GroupDelegate getDelegate() {
        GroupDelegate delegate = groupDelegate;
        if (delegate == null) {
            groupDelegate = delegate = new GroupDelegate(getFacebook(), getMessenger());
        }
        return delegate;
    }
    public GroupManager getManager() {
        GroupManager delegate = groupManager;
        if (delegate == null) {
            groupManager = delegate = new GroupManager(getDelegate());
        }
        return delegate;
    }
    public AdminManager getAdminManager() {
        AdminManager delegate = adminManager;
        if (delegate == null) {
            adminManager = delegate = new AdminManager(getDelegate());
        }
        return delegate;
    }
    public GroupEmitter getEmitter() {
        GroupEmitter delegate = groupEmitter;
        if (delegate == null) {
            groupEmitter = delegate = new GroupEmitter(getDelegate());
        }
        return delegate;
    }

    public String buildGroupName(List<ID> members) {
        GroupDelegate delegate = getDelegate();
        return delegate.buildGroupName(members);
    }

    //
    //  Entity DataSource
    //

    @Override
    public Meta getMeta(ID identifier) {
        GroupDelegate delegate = getDelegate();
        return delegate.getMeta(identifier);
    }

    @Override
    public List<Document> getDocuments(ID identifier) {
        GroupDelegate delegate = getDelegate();
        return delegate.getDocuments(identifier);
    }

    public Bulletin getBulletin(ID group) {
        GroupDelegate delegate = getDelegate();
        return delegate.getBulletin(group);
    }

    //
    //  Group DataSource
    //

    @Override
    public ID getFounder(ID group) {
        GroupDelegate delegate = getDelegate();
        return delegate.getFounder(group);
    }

    @Override
    public ID getOwner(ID group) {
        GroupDelegate delegate = getDelegate();
        return delegate.getOwner(group);
    }

    @Override
    public List<ID> getAssistants(ID group) {
        GroupDelegate delegate = getDelegate();
        return delegate.getAssistants(group);
    }

    @Override
    public List<ID> getMembers(ID group) {
        GroupDelegate delegate = getDelegate();
        return delegate.getMembers(group);
    }

    public List<ID> getAdministrators(ID group) {
        GroupDelegate delegate = getDelegate();
        return delegate.getAdministrators(group);
    }

    public boolean isOwner(ID user, ID group) {
        GroupDelegate delegate = getDelegate();
        return delegate.isOwner(user, group);
    }

    public boolean broadcastGroupDocument(Bulletin doc) {
        AdminManager delegate = getAdminManager();
        return delegate.broadcastGroupDocument(doc);
    }

    //
    //  Group Manage
    //

    /**
     *  Create new group with members
     *
     * @param members - all members
     * @return group ID
     */
    public ID createGroup(List<ID> members) {
        GroupManager delegate = getManager();
        return delegate.createGroup(members);
    }

    /**
     *  Update 'administrators' in bulletin document
     *
     * @param admins - new administrator ID list
     * @param group  - group ID
     * @return true on success
     */
    public boolean updateAdministrators(List<ID> admins, ID group) {
        AdminManager delegate = getAdminManager();
        return delegate.updateAdministrators(admins, group);
    }

    /**
     *  Reset group members
     *
     * @param members - new member ID list
     * @param group   - group ID
     * @return true on success
     */
    public boolean resetGroupMembers(List<ID> members, ID group) {
        GroupManager delegate = getManager();
        return delegate.resetMembers(members, group);
    }

    /**
     *  Expel members from this group
     *
     * @param removingMembers - members to be removed
     * @param group           - group ID
     * @return true on success
     */
    public boolean expelGroupMembers(List<ID> removingMembers, ID group) {
        if (removingMembers.isEmpty()) {
            assert false : "params error: " + group + ", " + removingMembers;
            return false;
        }

        CommonFacebook facebook = getFacebook();
        User user = facebook == null ? null : facebook.getCurrentUser();
        if (user == null) {
            assert false : "failed to get current user";
            return false;
        }
        ID me = user.getIdentifier();

        GroupDelegate delegate = getDelegate();
        List<ID> oldMembers = delegate.getMembers(group);

        boolean isOwner = delegate.isOwner(me, group);
        boolean isAdmin = delegate.isAdministrator(me, group);

        // check permission
        boolean canReset = isOwner || isAdmin;
        if (canReset) {
            // You are the owner/admin, then
            // remove the members and 'reset' the group
            List<ID> members = Copier.copyList(oldMembers);
            for (ID item : removingMembers) {
                members.remove(item);
            }
            return resetGroupMembers(members, group);
        }

        // not an admin/owner
        throw new SecurityException("Cannot expel members from group: " + group);
    }

    /**
     *  Invite new members to this group
     *
     * @param addingMembers - new member ID list to be added
     * @param group         - group ID
     * @return true on success
     */
    public boolean inviteGroupMembers(List<ID> addingMembers, ID group) {
        GroupManager delegate = getManager();
        return delegate.inviteMembers(addingMembers, group);
    }

    /**
     *  Quit from this group
     *
     * @param group - group ID
     * @return true on success
     */
    public boolean quitGroup(ID group) {
        GroupManager delegate = getManager();
        return delegate.quitGroup(group);
    }

    //
    //  Sending group message
    //

    /**
     *  Send group message content
     *
     * @param iMsg     - message to be sent
     * @param priority - smaller is faster
     * @return network message
     */
    public ReliableMessage sendInstantMessage(InstantMessage iMsg, int priority) {
        assert iMsg.getContent().getGroup() != null : "group message error: " + iMsg;
        iMsg.put("GF", true);  // group flag for notification
        GroupEmitter delegate = getEmitter();
        return delegate.sendInstantMessage(iMsg, priority);
    }

}
