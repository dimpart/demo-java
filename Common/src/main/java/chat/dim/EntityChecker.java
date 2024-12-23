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
package chat.dim;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import chat.dim.dbi.AccountDBI;
import chat.dim.mkm.MetaHelper;
import chat.dim.protocol.Document;
import chat.dim.protocol.GroupCommand;
import chat.dim.protocol.ID;
import chat.dim.protocol.Meta;
import chat.dim.protocol.ReliableMessage;
import chat.dim.protocol.Visa;
import chat.dim.type.Duration;
import chat.dim.type.Pair;
import chat.dim.utils.FrequencyChecker;
import chat.dim.utils.RecentTimeChecker;

public abstract class EntityChecker {

    // each query will be expired after 10 minutes
    public static Duration QUERY_EXPIRES = Duration.ofMinutes(10);

    // each respond will be expired after 10 minutes
    public static Duration RESPOND_EXPIRES = Duration.ofMinutes(10);

    // query checkers
    private final FrequencyChecker<ID> metaQueries;
    private final FrequencyChecker<ID> docsQueries;
    private final FrequencyChecker<ID> membersQueries;

    // response checker
    private final FrequencyChecker<ID> documentResponses;

    // recent time checkers
    private final RecentTimeChecker<ID> lastDocumentTimes;
    private final RecentTimeChecker<ID> lastHistoryTimes;

    // group => member
    private final Map<ID, ID> lastActiveMembers;

    protected final AccountDBI database;

    public EntityChecker(AccountDBI db) {
        super();
        database = db;

        lastActiveMembers = new HashMap<>();
        lastDocumentTimes = new RecentTimeChecker<>();
        lastHistoryTimes  = new RecentTimeChecker<>();

        metaQueries    = new FrequencyChecker<>(QUERY_EXPIRES);
        docsQueries    = new FrequencyChecker<>(QUERY_EXPIRES);
        membersQueries = new FrequencyChecker<>(QUERY_EXPIRES);

        documentResponses = new FrequencyChecker<>(RESPOND_EXPIRES);
    }

    protected boolean isMetaQueryExpired(ID identifier) {
        return metaQueries.isExpired(identifier, null, false);
    }
    protected boolean isDocumentQueryExpired(ID identifier) {
        return docsQueries.isExpired(identifier, null, false);
    }
    protected boolean isMembersQueryExpired(ID identifier) {
        return membersQueries.isExpired(identifier, null, false);
    }

    protected boolean isDocumentResponseExpired(ID identifier, boolean force) {
        return documentResponses.isExpired(identifier, null, force);
    }

    public void setLastActiveMember(ID group, ID member) {
        lastActiveMembers.put(group, member);
    }
    protected ID getLastActiveMember(ID group) {
        return lastActiveMembers.get(group);
    }

    // update 'SDT' - Sender Document Time
    public boolean setLastDocumentTime(ID identifier, Date current) {
        return lastDocumentTimes.setLastTime(identifier, current);
    }

    // update 'GHT' - Group History Time
    public boolean setLastGroupHistoryTime(ID group, Date current) {
        return lastHistoryTimes.setLastTime(group, current);
    }

    //
    //  Meta
    //

    /**
     *  Check meta for querying
     *
     * @param identifier - entity ID
     * @param meta       - exists meta
     * @return ture on querying
     */
    public boolean checkMeta(ID identifier, Meta meta) {
        if (needsQueryMeta(identifier, meta)) {
            /*/
            if (!isMetaQueryExpired(identifier)) {
                // query not expired yet
                return false;
            }
            /*/
            return queryMeta(identifier);
        } else {
            // no need to query meta again
            return false;
        }
    }

    /**
     *  check whether need to query meta
     */
    protected boolean needsQueryMeta(ID identifier, Meta meta) {
        if (identifier.isBroadcast()) {
            // broadcast entity has no meta to query
            return false;
        } else if (meta == null) {
            // meta not found, sure to query
            return true;
        }
        assert MetaHelper.matches(identifier, meta) : "meta not match: " + identifier + ", " + meta;
        return false;
    }

    //
    //  Documents
    //

    /**
     *  Check documents for querying/updating
     *
     * @param identifier - entity ID
     * @param documents  - exist document
     * @return true on querying
     */
    public boolean checkDocuments(ID identifier, List<Document> documents) {
        if (needsQueryDocuments(identifier, documents)) {
            /*/
            if (!isDocumentQueryExpired(identifier)) {
                // query not expired yet
                return false;
            }
            /*/
            return queryDocuments(identifier, documents);
        } else {
            // no need to update documents now
            return false;
        }
    }

    /**
     *  check whether need to query documents
     */
    protected boolean needsQueryDocuments(ID identifier, List<Document> documents) {
        if (identifier.isBroadcast()) {
            // broadcast entity has no document to query
            return false;
        } else if (documents == null || documents.isEmpty()) {
            // documents not found, sure to query
            return true;
        }
        Date current = getLastDocumentTime(identifier, documents);
        return lastDocumentTimes.isExpired(identifier, current);
    }

    public Date getLastDocumentTime(ID identifier, List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return null;
        }
        Date lastTime = null;
        Date docTime;
        for (Document doc : documents) {
            assert doc.getIdentifier().equals(identifier) : "document not match: " + identifier + ", " + doc;
            docTime = doc.getTime();
            if (docTime == null) {
                assert false : "document error: " + doc;
            } else if (lastTime == null || lastTime.before(docTime)) {
                lastTime = docTime;
            }
        }
        return lastTime;
    }

    //
    //  Group Members
    //

    /**
     *  Check group members for querying
     *
     * @param group   - group ID
     * @param members - exist members
     * @return true on querying
     */
    public boolean checkMembers(ID group, List<ID> members) {
        if (needsQueryMembers(group, members)) {
            /*/
            if (!isMembersQueryExpired(group)) {
                // query not expired yet
                return false;
            }
            /*/
            return queryMembers(group, members);
        } else {
            // no need to update group members now
            return false;
        }
    }

    /**
     *  check whether need to query group members
     */
    protected boolean needsQueryMembers(ID group, List<ID> members) {
        if (group.isBroadcast()) {
            // broadcast group has no members to query
            return false;
        } else if (members == null || members.isEmpty()) {
            // members not found, sure to query
            return true;
        }
        Date current = getLastGroupHistoryTime(group);
        return lastHistoryTimes.isExpired(group, current);
    }

    public Date getLastGroupHistoryTime(ID group) {
        List<Pair<GroupCommand, ReliableMessage>> array = database.getGroupHistories(group);
        if (array == null || array.isEmpty()) {
            return null;
        }
        Date lastTime = null;
        GroupCommand his;
        Date hisTime;
        for (Pair<GroupCommand, ReliableMessage> pair : array) {
            his = pair.first;
            if (his == null) {
                assert false : "group command error: " + pair;
                continue;
            }
            hisTime = his.getTime();
            if (hisTime == null) {
                assert false : "group command error: " + his;
            } else if (lastTime == null || lastTime.before(hisTime)) {
                lastTime = hisTime;
            }
        }
        return lastTime;
    }

    // -------- Querying

    /**
     *  Request for meta with entity ID
     *  (call 'isMetaQueryExpired()' before sending command)
     *
     * @param identifier - entity ID
     * @return false on duplicated
     */
    public abstract boolean queryMeta(ID identifier);

    /**
     *  Request for documents with entity ID
     *  (call 'isDocumentQueryExpired()' before sending command)
     *
     * @param identifier - entity ID
     * @param documents  - exist documents
     * @return false on duplicated
     */
    public abstract boolean queryDocuments(ID identifier, List<Document> documents);

    /**
     *  Request for group members with group ID
     *  (call 'isMembersQueryExpired()' before sending command)
     *
     * @param group      - group ID
     * @param members    - exist members
     * @return false on duplicated
     */
    public abstract boolean queryMembers(ID group, List<ID> members);

    // -------- Responding

    ///  Send my visa document to contact
    ///  if document is updated, force to send it again.
    ///  else only send once every 10 minutes.
    public abstract boolean sendVisa(Visa visa, ID receiver, boolean updated);

}
