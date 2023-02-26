/* license: https://mit-license.org
 * ==============================================================================
 * The MIT License (MIT)
 *
 * Copyright (c) 2019 Albert Moky
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
package chat.dim.protocol;

import java.util.HashMap;
import java.util.Map;

import chat.dim.dkd.cmd.BaseCommand;

/**
 *  Receipt command message: {
 *      type : 0x88,
 *      sn   : 456,
 *
 *      command : "receipt",
 *      text    : "...",  // text message
 *      origin  : {       // original message envelope
 *          sender    : "...",
 *          receiver  : "...",
 *          time      : 0,
 *          sn        : 123,
 *          signature : "..."
 *      }
 *  }
 */
public class ReceiptCommand extends BaseCommand {

    public static final String RECEIPT   = "receipt";

    // original message envelope
    private Envelope envelope;

    public ReceiptCommand(Map<String, Object> content) {
        super(content);
        envelope = null;
    }

    public ReceiptCommand(String text, Envelope env, long sn, String signature) {
        super(RECEIPT);
        // text message
        if (text != null) {
            put("text", text);
        }
        envelope = env;
        // envelope of the message responding to
        Map<String, Object> origin;
        if (env == null) {
            origin = new HashMap<>();
        } else {
            origin = env.toMap();
        }
        // sn of the message responding to
        if (sn > 0) {
            origin.put("sn", sn);
        }
        // signature of the message responding to
        if (signature != null) {
            origin.put("signature", signature);
        }
        if (origin.size() > 0) {
            put("origin", origin);
        }
    }

    //-------- setters/getters --------

    public String getText() {
        return getString("text");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getOrigin() {
        Object origin = get("origin");
        return origin == null ? null : (Map<String, Object>) origin;
    }

    public Envelope getOriginalEnvelope() {
        if (envelope == null) {
            // origin: { sender: "...", receiver: "...", time: 0 }
            Map<String, Object> origin = getOrigin();
            if (origin != null && origin.get("sender") != null) {
                envelope = Envelope.parse(origin);
            }
        }
        return envelope;
    }

    public long getOriginalSerialNumber() {
        Map<String, Object> origin = getOrigin();
        if (origin == null) {
            return 0;
        }
        Object sn = origin.get("sn");
        return sn == null ? 0 : ((Number) sn).longValue();
    }

    public String getOriginalSignature() {
        Map<String, Object> origin = getOrigin();
        if (origin == null) {
            return null;
        }
        return (String) origin.get("signature");
    }

    public boolean matchMessage(InstantMessage iMsg) {
        // check signature
        String sig1 = getOriginalSignature();
        if (sig1 != null) {
            // if contains signature, check it
            String sig2 = iMsg.getString("signature");
            if (sig2 != null) {
                if (sig1.length() > 8) {
                    sig1 = sig1.substring(0, 8);
                }
                if (sig2.length() > 8) {
                    sig2 = sig2.substring(0, 8);
                }
                return sig1.equals(sig2);
            }
        }
        // check envelope
        Envelope env1 = getOriginalEnvelope();
        if (env1 != null) {
            // if contains envelope, check it
            return env1.equals(iMsg.getEnvelope());
        }
        // check serial number
        // (only the original message's receiver can know this number)
        Content content = iMsg.getContent();
        long sn2 = content.getSerialNumber();
        long sn1 = getOriginalSerialNumber();
        return sn1 == sn2;
    }

    //
    //  Factory method
    //

    /**
     *  Create receipt with text message and origin message envelope
     *
     * @param text - message text
     * @param rMsg - origin message
     * @return ReceiptCommand
     */
    public static ReceiptCommand create(String text, ReliableMessage rMsg) {
        Envelope env = null;
        if (rMsg != null) {
            Map<String, Object> info = rMsg.copyMap(false);
            info.remove("data");
            info.remove("key");
            info.remove("keys");
            info.remove("meta");
            info.remove("visa");
            env = Envelope.parse(info);
        }
        return new ReceiptCommand(text, env, 0, null);
    }
}
