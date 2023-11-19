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

import java.util.List;

import chat.dim.ClientFacebook;
import chat.dim.CommonFacebook;
import chat.dim.CommonMessenger;
import chat.dim.core.TwinsHelper;
import chat.dim.mkm.Group;
import chat.dim.protocol.Bulletin;
import chat.dim.protocol.Document;
import chat.dim.protocol.EntityType;
import chat.dim.protocol.ID;
import chat.dim.protocol.Meta;

public class GroupDelegate extends TwinsHelper implements Group.DataSource {

    public GroupDelegate(CommonFacebook facebook, CommonMessenger messenger) {
        super(facebook, messenger);
    }

    @Override
    public CommonFacebook getFacebook() {
        return (CommonFacebook) super.getFacebook();
    }

    @Override
    public CommonMessenger getMessenger() {
        return (CommonMessenger) super.getMessenger();
    }

    public String buildGroupName(List<ID> members) {
        assert members.size() > 0 : "members should not be empty here";
        CommonFacebook facebook = getFacebook();
        StringBuilder text = new StringBuilder(facebook.getName(members.get(0)));
        String nickname;
        for (int i = 1; i < members.size(); ++i) {
            nickname = facebook.getName(members.get(i));
            if (nickname.isEmpty()) {
                continue;
            }
            text.append(", ").append(nickname);
            if (text.length() > 32) {
                return text.substring(0, 28) + " ...";
            }
        }
        return text.toString();
    }

    //
    //  Entity DataSource
    //

    @Override
    public Meta getMeta(ID identifier) {
        CommonFacebook facebook = getFacebook();
        return facebook.getMeta(identifier);
    }

    @Override
    public List<Document> getDocuments(ID identifier) {
        CommonFacebook facebook = getFacebook();
        return facebook.getDocuments(identifier);
    }

    public Bulletin getBulletin(ID group) {
        CommonFacebook facebook = getFacebook();
        return facebook.getBulletin(group);
    }

    public boolean saveDocument(Document doc) {
        CommonFacebook facebook = getFacebook();
        return facebook.saveDocument(doc);
    }

    //
    //  Group DataSource
    //

    @Override
    public ID getFounder(ID group) {
        CommonFacebook facebook = getFacebook();
        return facebook.getFounder(group);
    }

    @Override
    public ID getOwner(ID group) {
        CommonFacebook facebook = getFacebook();
        return facebook.getOwner(group);
    }

    @Override
    public List<ID> getAssistants(ID group) {
        CommonFacebook facebook = getFacebook();
        return facebook.getAssistants(group);
    }

    @Override
    public List<ID> getMembers(ID group) {
        CommonFacebook facebook = getFacebook();
        return facebook.getMembers(group);
    }

    public boolean saveMembers(List<ID> newMembers, ID group) {
        ClientFacebook facebook = (ClientFacebook) getFacebook();
        return facebook.saveMembers(newMembers, group);
    }

    //
    //  Administrators
    //

    public List<ID> getAdministrators(ID group) {
        ClientFacebook facebook = (ClientFacebook) getFacebook();
        return facebook.getAdministrators(group);
    }

    public boolean saveAdministrators(List<ID> newAdmins, ID group) {
        ClientFacebook facebook = (ClientFacebook) getFacebook();
        return facebook.saveAdministrators(newAdmins, group);
    }

    //
    //  Membership
    //

    public boolean isFounder(ID user, ID group) {
        assert user.isUser() && group.isGroup() : "ID error: " + user + ", " + group;
        ID founder = getFounder(group);
        if (founder != null) {
            return founder.equals(user);
        }
        // check member's public key with group's meta.key
        Meta gMeta = getMeta(group);
        Meta mMeta = getMeta(user);
        if (gMeta == null || mMeta == null) {
            assert false : "failed to get meta for group: " + group + ", user: " + user;
            return false;
        }
        return gMeta.matchPublicKey(gMeta.getPublicKey());
    }

    public boolean isOwner(ID user, ID group) {
        assert user.isUser() && group.isGroup() : "ID error: " + user + ", " + group;
        ID owner = getOwner(group);
        if (owner != null) {
            return owner.equals(user);
        }
        if (EntityType.GROUP.equals(group.getType())) {
            // this is a polylogue
            return isFounder(user, group);
        }
        throw new IllegalArgumentException("only polylogue so far");
    }

    public boolean isMember(ID user, ID group) {
        assert user.isUser() && group.isGroup() : "ID error: " + user + ", " + group;
        List<ID> members = getMembers(group);
        return members != null && members.contains(user);
    }

    public boolean isAssistant(ID bot, ID group) {
        assert bot.isUser() && group.isGroup() : "ID error: " + bot + ", " + group;
        List<ID> assistants = getAssistants(group);
        return assistants != null && assistants.contains(bot);
    }

    public boolean isAdministrator(ID user, ID group) {
        assert user.isUser() && group.isGroup() : "ID error: " + user + ", " + group;
        List<ID> admins = getAdministrators(group);
        return admins != null && admins.contains(user);
    }

}
