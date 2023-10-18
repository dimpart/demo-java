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

import chat.dim.CipherKeyDelegate;
import chat.dim.ClientMessenger;
import chat.dim.CommonFacebook;
import chat.dim.CommonMessenger;
import chat.dim.crypto.SymmetricKey;
import chat.dim.mkm.User;
import chat.dim.protocol.Content;
import chat.dim.protocol.Envelope;
import chat.dim.protocol.FileContent;
import chat.dim.protocol.ForwardContent;
import chat.dim.protocol.ID;
import chat.dim.protocol.InstantMessage;
import chat.dim.protocol.ReliableMessage;
import chat.dim.type.Pair;
import chat.dim.utils.Log;

public abstract class GroupEmitter {

    // NOTICE: group assistants (bots) can help the members to redirect messages
    //
    //      if members.length < POLYLOGUE_LIMIT,
    //          means it is a small polylogue group, let the members to split
    //          and send group messages by themself, this can keep the group
    //          more secretive because no one else can know the group ID even;
    //      else,
    //          set 'assistants' in the bulletin document to tell all members
    //          that they can let the group bot to do the job for them.
    //
    public static int POLYLOGUE_LIMIT = 32;

    // NOTICE: expose group ID to reduce encrypting time
    //
    //      if members.length < SECRET_GROUP_LIMIT,
    //          means it is a tiny group, you can choose to hide the group ID,
    //          that you can split and encrypt message one by one;
    //      else,
    //          you should expose group ID in the instant message level, then
    //          encrypt message by one symmetric key for this group, after that,
    //          split and send to all members directly.
    public static int SECRET_GROUP_LIMIT = 16;

    protected final GroupDelegate delegate;
    protected final GroupPacker packer;

    public GroupEmitter(GroupDelegate dataSource) {
        super();
        delegate = dataSource;
        packer = createPacker();
    }

    // override for customized packer
    protected GroupPacker createPacker() {
        return new GroupPacker(delegate);
    }

    protected CommonFacebook getFacebook() {
        return delegate.getFacebook();
    }
    protected CommonMessenger getMessenger() {
        return delegate.getMessenger();
    }

    protected SymmetricKey getEncryptKey(ID sender, ID receiver) {
        ClientMessenger messenger = (ClientMessenger) getMessenger();
        CipherKeyDelegate keyCache = messenger.getCipherKeyDelegate();
        return keyCache.getCipherKey(sender, receiver, true);
    }

    /**
     *  Send group message content
     */
    public Pair<InstantMessage, ReliableMessage> sendContent(Content content, int priority) {
        CommonFacebook facebook = getFacebook();
        assert facebook != null : "facebook not ready";

        // get 'sender' => 'group'
        ID group = content.getGroup();
        if (group == null) {
            assert false : "not a group message: " + content;
            return null;
        }
        User user = facebook.getCurrentUser();
        if (user == null) {
            assert false : "failed to get current user";
            return null;
        }
        ID sender = user.getIdentifier();

        // pack and send
        Envelope envelope = Envelope.create(sender, group, null);
        InstantMessage iMsg = InstantMessage.create(envelope, content);
        ReliableMessage rMsg = sendMessage(iMsg, priority);
        return new Pair<>(iMsg, rMsg);
    }

    public ReliableMessage sendMessage(InstantMessage iMsg, int priority) {
        Content content = iMsg.getContent();
        ID group = content.getGroup();
        if (group == null) {
            assert false : "not a group message: " + iMsg;
            return null;
        }

        //
        //  0. check file message
        //
        if (content instanceof FileContent) {
            FileContent file = (FileContent) content;
            // call emitter to encrypt & upload file data before send out
            SymmetricKey password = getEncryptKey(iMsg.getSender(), group);
            boolean ok = uploadFileData(file, password, iMsg);
            assert ok : "failed to upload file data: " + file;
        }

        //
        //  1. check group bots
        //
        List<ID> bots = delegate.getAssistants(group);
        if (bots != null && bots.size() > 0) {
            // group bots found, forward this message to any bot to let it split for me;
            // this can reduce my jobs.
            ID prime = bots.get(0);
            return forwardMessage(prime, group, iMsg, priority);
        }

        //
        //  2. check group members
        //
        List<ID> allMembers = delegate.getMembers(group);
        if (allMembers == null || allMembers.isEmpty()) {
            assert false : "failed to get members for group: " + group;
            return null;
        }
        // no 'assistants' found in group's bulletin document?
        // split group messages and send to all members one by one
        if (allMembers.size() < SECRET_GROUP_LIMIT) {
            // it is a tiny group, split this message before encrypting and signing,
            // then send this group message to all members one by one
            int success = splitAndSendMessage(allMembers, group, iMsg, priority);
            Log.info("split " + success + " message(s) for group: " + group);
            return null;
        } else {
            // encrypt and sign this message first,
            // then split and send to all members one by one
            return disperseMessage(allMembers, group, iMsg, priority);
        }
    }

    /**
     *  Send file data encrypted with password
     *  (store download URL & decrypt key into file content after uploaded)
     *
     * @param content  - file content
     * @param password - symmetric key to encrypt/decrypt file data
     * @param iMsg     - outgoing message
     */
    protected abstract boolean uploadFileData(FileContent content, SymmetricKey password, InstantMessage iMsg);

    /**
     *  Encrypt & sign message, then forward to the bot
     */
    private ReliableMessage forwardMessage(ID bot, ID group, InstantMessage iMsg, int priority) {
        assert bot.isUser() && group.isGroup() : "ID error: " + bot + ", " + group;
        // NOTICE: because group assistant (bot) cannot be a member of the group, so
        //         if you want to send a group command to any assistant, you must
        //         set the bot ID as 'receiver' and set the group ID in content;
        //         this means you must send it to the bot directly.
        CommonMessenger messenger = getMessenger();

        // group bots designated, let group bot to split the message, so
        // here must expose the group ID; this will cause the client to
        // use a "user-to-group" encrypt key to encrypt the message content,
        // this key will be encrypted by each member's public key, so
        // all members will received a message split by the group bot,
        // but the group bots cannot decrypt it.
        iMsg.setString("group", group);

        // the group bot can only get the message 'signature',
        // but cannot know the 'sn' because it cannot decrypt the content,
        // this is usually not a problem;
        // but sometimes we want to respond a receipt with original sn,
        // so I suggest to expose 'sn' too.
        long sn = iMsg.getContent().getSerialNumber();
        iMsg.put("sn", sn);

        // pack message
        ReliableMessage rMsg = packer.encryptAndSignMessage(iMsg);
        if (rMsg == null) {
            assert false : "failed to encrypt & sign message: " + iMsg.getSender() + " => " + group;
            return null;
        }

        // forward the group message to any bot
        Content content = ForwardContent.create(rMsg);
        Pair<InstantMessage, ReliableMessage> pair = messenger.sendContent(null, bot, content, priority);
        if (pair == null || pair.second == null) {
            assert false : "failed to forward message for group: " + group + ", bot: " + bot;
            return null;
        }

        // OK, return the forwarding message
        return rMsg;
    }

    /**
     *  Encrypt & sign message, then disperse to all members
     */
    private ReliableMessage disperseMessage(List<ID> allMembers, ID group, InstantMessage iMsg, int priority) {
        assert group.isGroup() : "group ID error: " + group;
        // assert !iMsg.containsKey("group") : "should not happen";
        CommonMessenger messenger = getMessenger();

        // NOTICE: there are too many members in this group
        //         if we still hide the group ID, the cost will be very high.
        //  so,
        //      here I suggest to expose 'group' on this message's envelope
        //      to use a user-to-group password to encrypt the message content,
        //      and the actual receiver can get the decrypt key
        //      with the accurate direction: (sender -> group)
        iMsg.setString("group", group);

        ID sender = iMsg.getSender();

        // pack message
        ReliableMessage rMsg = packer.encryptAndSignMessage(iMsg);
        if (rMsg == null) {
            assert false : "failed to encrypt & sign message: " + sender + " => " + group;
            return null;
        }

        // split messages
        List<ReliableMessage> messages = packer.splitMessage(rMsg, allMembers);
        ID receiver;
        boolean ok;
        for (ReliableMessage item : messages) {
            receiver = item.getReceiver();
            if (sender.equals(receiver)) {
                assert false : "cycled message: " + sender + " => " + receiver + ", " + group;
                continue;
            }
            // send message
            ok = messenger.sendReliableMessage(item, priority);
            assert ok : "failed to send message: " + sender + " => " + receiver + ", " + group;
        }

        return rMsg;
    }

    /**
     *  Split and send (encrypt + sign) group messages to all members one by one
     */
    private int splitAndSendMessage(List<ID> allMembers, ID group, InstantMessage iMsg, int priority) {
        assert group.isGroup() : "group ID error: " + group;
        assert !iMsg.containsKey("group") : "should not happen";
        CommonMessenger messenger = getMessenger();

        // NOTICE: this is a tiny group
        //         I suggest NOT to expose the group ID to maximize its privacy,
        //         the cost is we cannot use a user-to-group password here;
        //         So the other members can only treat it as a personal message
        //         and use the user-to-user symmetric key to decrypt content,
        //         they can get the group ID after decrypted.

        ID sender = iMsg.getSender();
        int success = 0;

        // split messages
        List<InstantMessage> messages = packer.splitMessage(iMsg, allMembers);
        ID receiver;
        ReliableMessage rMsg;
        for (InstantMessage item : messages) {
            receiver = item.getReceiver();
            if (sender.equals(receiver)) {
                assert false : "cycled message: " + sender + " => " + receiver + ", " + group;
                continue;
            }
            // send message
            rMsg = messenger.sendInstantMessage(item, priority);
            if (rMsg == null) {
                Log.error("failed to send message: " + receiver + " in group " + group);
                continue;
            }
            success += 1;
        }

        // done!
        return success;
    }

}
