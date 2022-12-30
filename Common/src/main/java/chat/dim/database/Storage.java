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
package chat.dim.database;

import chat.dim.filesys.ExternalStorage;
import chat.dim.utils.Template;

public class Storage extends ExternalStorage {

    public static String rootDirectory = "/var/.dim";
    public static String pubDirTemplate = "{ROOT}/public";
    public static String priDirTemplate = "{ROOT}/private";

    protected final String publicDirectory;
    protected final String privateDirectory;

    public Storage(String rootDir, String publicDir, String privateDir) {
        super();
        if (rootDir == null || rootDir.length() == 0) {
            rootDir = rootDirectory;
        }
        if (publicDir == null || publicDir.length() == 0) {
            publicDir = Template.replace(pubDirTemplate, "ROOT", rootDir);
        }
        if (privateDir == null || privateDir.length() == 0) {
            privateDir = Template.replace(priDirTemplate, "ROOT", rootDir);
        }
        publicDirectory = publicDir;
        privateDirectory = privateDir;
    }
}
