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
package chat.dim.utils;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public class ConfigParser {

    private Map<String, Map<String, String>> config = new HashMap<>();

    @Override
    public String toString() {
        return config.toString();
    }

    public Set<String> getSections() {
        return config.keySet();
    }

    public void setItems(String section, Map<String, String> items) {
        config.put(section, items);
    }
    public Map<String, String> getItems(String section) {
        return config.get(section);
    }

    public String getString(String section, String option) {
        Map<String, String> items = config.get(section);
        return items == null ? null : items.get(option);
    }
    public int getInt(String section, String option) throws NumberFormatException {
        String value = getString(section, option);
        return parseInt(value);
    }
    public float getFloat(String section, String option) throws NumberFormatException {
        String value = getString(section, option);
        return parseFloat(value);
    }

    public boolean getBoolean(String section, String option) {
        String value = getString(section, option);
        return parseBoolean(value);
    }

    public void parse(String iniFileContent) {
        parse(RE_LINES.split(iniFileContent));
    }
    private void parse(String[] contents) {
        config = new HashMap<>();
        String section = "default";
        Map<String, String> items = new HashMap<>();
        String text;
        int end;
        String[] pair;
        String name, value;
        for (String line : contents) {
            // remove annotations
            text = RE_ANNOTATION.split(line, 2)[0].trim();
            end = text.length() - 1;
            if (end < 2) {
                continue;
            }
            // check new section
            if (text.charAt(0) == '[' && text.charAt(end) == ']') {
                if (items.size() > 0) {
                    // store old section
                    config.put(section, items);
                }
                // create new section
                section = text.substring(1, end);
                items = new HashMap<>();
                continue;
            }
            pair = RE_PAIR.split(text, 2);
            if (pair.length != 2) {
                continue;
            }
            name = pair[0].trim();
            value = pair[1].trim();
            if (name.length() == 0 || value.length() == 0) {
                continue;
            }
            items.put(name, value);
        }
        if (items.size() > 0) {
            // store old section
            config.put(section, items);
        }
    }

    //
    //  value parsers
    //
    private static float parseFloat(String value) {
        try {
            if (value != null) {
                return Float.parseFloat(value);
            }
        } catch (NumberFormatException e) {
            e.printStackTrace();
        }
        return 0.0f;
    }
    private static int parseInt(String value) {
        try {
            if (value != null) {
                return Integer.parseInt(value);
            }
        } catch (NumberFormatException e) {
            e.printStackTrace();
        }
        return 0;
    }
    private static boolean parseBoolean(String value) {
        return value != null && BOOLEAN_STATES.get(value);
    }

    private static final Pattern RE_LINES = Pattern.compile("[\n]");
    private static final Pattern RE_ANNOTATION = Pattern.compile("[#;]");
    private static final Pattern RE_PAIR = Pattern.compile("[:=]");

    private static final Map<String, Boolean> BOOLEAN_STATES = new HashMap<>();

    static {

        final String[] TRUE_STATES = {"1", "yes", "true", "on"};
        final String[] FALSE_STATES = {"0", "no", "false", "off"};

        for (String state : TRUE_STATES) {
            BOOLEAN_STATES.put(state, true);
        }
        for (String state : FALSE_STATES) {
            BOOLEAN_STATES.put(state, false);
        }
    }
}
