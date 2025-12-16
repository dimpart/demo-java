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
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import chat.dim.CommonFacebook;
import chat.dim.CommonMessenger;
import chat.dim.EntityChecker;
import chat.dim.Session;
import chat.dim.log.Log;
import chat.dim.mkm.DocumentUtils;
import chat.dim.mkm.User;
import chat.dim.protocol.EntityType;
import chat.dim.protocol.Envelope;
import chat.dim.protocol.ID;
import chat.dim.protocol.ReceiptCommand;
import chat.dim.protocol.Visa;
import chat.dim.skywalker.Runner;


// Singleton
enum _SharedBotsManager {

    INSTANCE;

    public static _SharedBotsManager getInstance() {
        return INSTANCE;
    }

    _SharedBotsManager() {
        manager = new GroupBotsManager();
        manager.start();
    }

    final GroupBotsManager manager;
}


final class GroupBotsManager extends Runner {
    public GroupBotsManager() {
        super(Runner.INTERVAL_SLOW);
    }

    public static GroupBotsManager getInstance() {
        return _SharedBotsManager.getInstance().manager;
    }

    private final List<ID> commonAssistants = new ArrayList<>();

    private final Set<ID> candidates = new HashSet<>();   // bot IDs to be check
    private final Map<ID, Long> times = new HashMap<>();  // bot IDs with respond time

    private WeakReference<CommonMessenger> messengerRef = null;

    public void setMessenger(CommonMessenger messenger) {
        messengerRef = messenger == null ? null : new WeakReference<>(messenger);
    }
    public CommonMessenger getMessenger() {
        WeakReference<CommonMessenger> ref = messengerRef;
        return ref == null ? null : ref.get();
    }

    public CommonFacebook getFacebook() {
        CommonMessenger messenger = getMessenger();
        return messenger == null ? null : messenger.getFacebook();
    }

    /**
     *  When received receipt command from the bot
     *  update the speed of this bot.
     */
    public boolean updateRespondTime(ReceiptCommand content, Envelope envelope) {
        /*/
        String app = content.getString("app", null);
        if (app == null) {
            app = content.getString("app_id", null);
        }
        if (app == null || !app.equals("chat.dim.group.assistant")) {
            return false;
        }
        /*/
        //
        //  1. check sender
        //
        ID sender = envelope.getSender();
        if (!EntityType.BOT.equals(sender.getType())) {
            return false;
        }
        Envelope originalEnvelope = content.getOriginalEnvelope();
        if (originalEnvelope == null) {
            Log.error("receipt content error: " + content.toMap());
            return false;
        }
        ID originalReceiver = originalEnvelope.getReceiver();
        Date originalTime = originalEnvelope.getTime();
        if (originalReceiver == null || originalTime == null) {
            Log.error("original envelope error: " + originalEnvelope.toMap());
            return false;
        } else if (!originalReceiver.equals(sender)) {
            assert originalReceiver.isBroadcast() : "sender error: " + sender + ", " + originalReceiver;
            return false;
        }
        //
        //  2. check send time
        //
        long duration = (new Date()).getTime() - originalTime.getTime();
        if (duration <= 0) {
            Log.error("receipt time error: " + originalTime);
            return false;
        }
        //
        //  3. check duration
        //
        Long cached = times.get(sender);
        if (cached != null && cached <= duration) {
            return false;
        }
        times.put(sender, duration);
        return true;
    }

    /**
     *  When received new config from current Service Provider,
     *  set common assistants of this SP.
     */
    public void setCommonAssistants(List<ID> bots) {
        candidates.addAll(bots);
        commonAssistants.clear();
        commonAssistants.addAll(bots);
    }

    public List<ID> getAssistants(ID group) {
        CommonFacebook facebook = getFacebook();
        if (facebook == null) {
            assert false : "facebook not found";
            return null;
        }
        List<ID> bots = facebook.getAssistants(group);
        if (bots == null || bots.isEmpty()) {
            return commonAssistants;
        }
        candidates.addAll(bots);
        return bots;
    }

    /**
     *  Get the fastest group bot
     */
    public ID getFastestAssistant(ID group) {
        List<ID> bots = getAssistants(group);
        if (bots == null || bots.isEmpty()) {
            Log.warning("group bots not found: " + group.toString());
            return null;
        }
        ID prime = null;
        long primeDuration = 0;
        Long duration;
        for (ID ass : bots) {
            duration = times.get(ass);
            if (duration == null || duration <= 0) {
                Log.info("group bot not respond yet, ignore it: " + ass + ", " + group);
                continue;
            } else if (primeDuration == 0) {
                // first responded bot
                Log.info("first responded bot: " + ass + ", " + group);
            } else if (primeDuration < duration) {
                Log.info("this bot is slower: " + ass + ", " + prime + ", " + group);
                continue;
            }
            prime = ass;
            primeDuration = duration;
        }
        if (prime == null) {
            prime = bots.get(0);
            Log.info("no bot responded, take the first one: " + prime + ", " + group);
        } else {
            Log.info("got the fastest bot: " + prime + ", " + primeDuration + ", " + group);
        }
        return prime;
    }

    @Override
    public boolean process() {
        CommonFacebook facebook = getFacebook();
        CommonMessenger messenger = getMessenger();
        if (facebook == null || messenger == null) {
            return false;
        }
        //
        //  1. check session
        //
        Session session = messenger.getSession();
        if (session == null || session.getSessionKey() == null || !session.isActive()) {
            // not login yet
            return false;
        }
        //
        //  2. get visa
        //
        Visa visa;
        try {
            User me = facebook.getCurrentUser();
            visa = me == null ? null : DocumentUtils.lastVisa(me.getDocuments());
            if (visa == null) {
                Log.error("failed to get visa: " + me);
                return false;
            }
        } catch (Exception e) {
            Log.error("failed to get current user: " + e);
            return false;
        }
        EntityChecker checker = facebook.getEntityChecker();
        //
        //  3. check candidates
        //
        Set<ID> bots = new HashSet<>(candidates);
        candidates.clear();
        for (ID item : bots) {
            if (times.get(item) != null) {
                // no need to check again
                Log.info("group bot already responded: " + item);
                continue;
            }
            // no respond yet, try to push visa to the bot
            try {
                checker.sendVisa(visa, item, false);
            } catch (Exception e) {
                Log.error("failed to query assistant: " + item + ", " + e);
            }
        }
        return false;
    }

    public void start() {
        Thread thread = new Thread(this);
        thread.setDaemon(true);
        thread.start();
    }

}
