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
import chat.dim.TwinsHelper;
import chat.dim.mkm.Group;
import chat.dim.mkm.MetaHelper;
import chat.dim.protocol.Bulletin;
import chat.dim.protocol.Document;
import chat.dim.protocol.EntityType;
import chat.dim.protocol.Envelope;
import chat.dim.protocol.ID;
import chat.dim.protocol.Meta;
import chat.dim.protocol.ReceiptCommand;
import chat.dim.utils.Log;

public class GroupDelegate extends TwinsHelper implements Group.DataSource {

    public GroupDelegate(CommonFacebook facebook, CommonMessenger messenger) {
        super(facebook, messenger);
        GroupBotsManager.getInstance().setMessenger(messenger);
    }

    @Override
    public ClientFacebook getFacebook() {
        return (ClientFacebook) super.getFacebook();
    }

    @Override
    public CommonMessenger getMessenger() {
        return (CommonMessenger) super.getMessenger();
    }

    public String buildGroupName(List<ID> members) {
        CommonFacebook facebook = getFacebook();
        if (members == null || members.isEmpty() || facebook == null) {
            assert false : "members should not be empty here";
            return null;
        }
        StringBuilder text = new StringBuilder();
        text.append(facebook.getName(members.get(0)));
        String nickname;
        for (int i = 1; i < members.size(); ++i) {
            nickname = facebook.getName(members.get(i));
            if (nickname == null || nickname.isEmpty()) {
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
        return facebook == null ? null : facebook.getMeta(identifier);
    }

    @Override
    public List<Document> getDocuments(ID identifier) {
        CommonFacebook facebook = getFacebook();
        return facebook == null ? null : facebook.getDocuments(identifier);
    }

    public Bulletin getBulletin(ID group) {
        CommonFacebook facebook = getFacebook();
        return facebook == null ? null : facebook.getBulletin(group);
    }

    public boolean saveDocument(Document doc) {
        CommonFacebook facebook = getFacebook();
        return facebook != null && facebook.saveDocument(doc);
    }

    //
    //  Group DataSource
    //

    @Override
    public ID getFounder(ID group) {
        CommonFacebook facebook = getFacebook();
        return facebook == null ? null : facebook.getFounder(group);
    }

    @Override
    public ID getOwner(ID group) {
        CommonFacebook facebook = getFacebook();
        return facebook == null ? null : facebook.getOwner(group);
    }

    @Override
    public List<ID> getMembers(ID identifier) {
        CommonFacebook facebook = getFacebook();
        return facebook == null ? null : facebook.getMembers(identifier);
    }

    public boolean saveMembers(List<ID> newMembers, ID group) {
        ClientFacebook facebook = getFacebook();
        return facebook != null && facebook.saveMembers(newMembers, group);
    }

    //
    //  Group Assistants
    //

    @Override
    public List<ID> getAssistants(ID identifier) {
        GroupBotsManager manager = GroupBotsManager.getInstance();
        return manager.getAssistants(identifier);
    }

    public ID getFastestAssistant(ID identifier) {
        GroupBotsManager manager = GroupBotsManager.getInstance();
        return manager.getFastestAssistant(identifier);
    }

    public void setCommonAssistants(List<ID> bots) {
        GroupBotsManager manager = GroupBotsManager.getInstance();
        manager.setCommonAssistants(bots);
    }

    public boolean updateRespondTime(ReceiptCommand content, Envelope envelope) {
        GroupBotsManager manager = GroupBotsManager.getInstance();
        return manager.updateRespondTime(content, envelope);
    }

    //
    //  Administrators
    //

    public List<ID> getAdministrators(ID identifier) {
        ClientFacebook facebook = getFacebook();
        return facebook == null ? null : facebook.getAdministrators(identifier);
    }

    public boolean saveAdministrators(List<ID> admins, ID group) {
        ClientFacebook facebook = getFacebook();
        return facebook != null && facebook.saveAdministrators(admins, group);
    }

    //
    //  Membership
    //

    public boolean isFounder(ID user, ID group) {
        ID founder = getFounder(group);
        if (founder != null) {
            return founder.equals(user);
        }
        // check member's public key with group's meta.key
        Meta gMeta = getMeta(group);
        Meta mMeta = getMeta(user);
        if (gMeta == null || mMeta == null) {
            Log.error("failed to get meta: " + group + ", " + user);
            return false;
        }
        return MetaHelper.matches(mMeta.getPublicKey(), gMeta);
    }

    public boolean isOwner(ID user, ID group) {
        ID owner = getOwner(group);
        if (owner != null) {
            return owner.equals(user);
        }
        if (EntityType.GROUP.equals(group.getType())) {
            // this is a polylogue
            return isFounder(user, group);
        }
        throw new IllegalArgumentException("only Polylogue so far");
    }

    public boolean isMember(ID user, ID group) {
        List<ID> members = getMembers(group);
        return members != null && members.contains(user);
    }

    public boolean isAdministrator(ID user, ID group) {
        List<ID> admins = getAdministrators(group);
        return admins != null && admins.contains(user);
    }

    public boolean isAssistant(ID user, ID group) {
        List<ID> bots = getAssistants(group);
        return bots != null && bots.contains(user);
    }

}
