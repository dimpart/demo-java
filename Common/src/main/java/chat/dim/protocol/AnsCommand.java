/* license: https://mit-license.org
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
package chat.dim.protocol;

import java.util.List;
import java.util.Map;

import chat.dim.dkd.cmd.BaseCommand;
import chat.dim.utils.ArrayUtils;

/**
 *  Command message: {
 *      type : 0x88,
 *      sn   : 123,
 *
 *      command : "ans",
 *      names   : "...",        // query with alias(es, separated by ' ')
 *      records : {             // respond with record(s)
 *          "{alias}": "{ID}",
 *      }
 *  }
 */
public class AnsCommand extends BaseCommand {

    public static final String ANS = "ans";

    public AnsCommand(Map<String, Object> content) {
        super(content);
    }

    public AnsCommand(String names) {
        super(ANS);
        if (names != null && names.length() > 0) {
            put("names", names);
        }
    }

    public List<String> getNames() {
        String names = getString("names", null);
        if (names == null) {
            return null;
        }
        return ArrayUtils.split(" ", names);
    }

    @SuppressWarnings("unchecked")
    public Map<String, String> getRecords() {
        return (Map<String, String>) get("records");
    }
    public void setRecords(Map<String, String> records) {
        put("records", records);
    }

    //
    //  Factories
    //

    public static AnsCommand query(String names) {
        return new AnsCommand(names);
    }
    public static AnsCommand query(List<String> names) {
        return new AnsCommand(ArrayUtils.join(" ", names));
    }

    public static AnsCommand response(String names, Map<String, String> records) {
        AnsCommand command = new AnsCommand(names);
        command.setRecords(records);
        return command;
    }
    public static AnsCommand response(List<String> names, Map<String, String> records) {
        AnsCommand command = new AnsCommand(ArrayUtils.join(" ", names));
        command.setRecords(records);
        return command;
    }
}
