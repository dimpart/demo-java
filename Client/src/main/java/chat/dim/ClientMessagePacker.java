/* license: https://mit-license.org
 *
 *  DIMP : Decentralized Instant Messaging Protocol
 *
 *                                Written in 2022 by Moky <albert.moky@gmail.com>
 *
 * ==============================================================================
 * The MIT License (MIT)
 *
 * Copyright (c) 2022 Albert Moky
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import chat.dim.protocol.ID;
import chat.dim.protocol.InstantMessage;

public abstract class ClientMessagePacker extends CommonPacker {

    public ClientMessagePacker(Facebook facebook, Messenger messenger) {
        super(facebook, messenger);
    }

    @Override
    protected ClientFacebook getFacebook() {
        Facebook facebook = super.getFacebook();
        assert facebook instanceof ClientFacebook : "facebook error: " + facebook;
        return (ClientFacebook) facebook;
    }

    @Override
    protected boolean checkReceiver(InstantMessage iMsg) {
        ID receiver = iMsg.getReceiver();
        if (receiver.isBroadcast()) {
            // broadcast message
            return true;
        } else if (receiver.isUser()) {
            // check user's meta & document
            return super.checkReceiver(iMsg);
        }
        //
        //  check group's meta & members
        //
        List<ID> members = getMembers(receiver);
        if (members == null || members.isEmpty()) {
            // group not ready, suspend message for waiting meta/members
            Map<String, String> error = new HashMap<>();
            error.put("message", "group not ready");
            error.put("group", receiver.toString());
            suspendMessage(iMsg, error);  // iMsg.put("error", error);
            return false;
        }
        //
        //  check group members' visa key
        //
        List<ID> waiting = new ArrayList<>();
        for (ID item : members) {
            if (getVisaKey(item) == null) {
                // member not ready
                waiting.add(item);
            }
        }
        if (waiting.isEmpty()) {
            // all members' visa keys exist
            return true;
        }
        // members not ready, suspend message for waiting document
        Map<String, Object> error = new HashMap<>();
        error.put("message", "members not ready");
        error.put("group", receiver.toString());
        error.put("members", ID.revert(waiting));
        suspendMessage(iMsg, error);  // iMsg.put("error", error);
        return false;
    }

}
