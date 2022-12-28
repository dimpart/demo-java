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

import java.io.IOException;

import chat.dim.dbi.MetaDBI;
import chat.dim.protocol.ID;
import chat.dim.protocol.Meta;
import chat.dim.utils.Log;
import chat.dim.utils.Template;

/**
 *  Meta for Entities (User/Group)
 *  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
 *
 *  file path: '.dim/public/{ADDRESS}/meta.js'
 */
public class MetaStorage extends Storage implements MetaDBI {

    public static String META_PATH = "{PUBLIC}/{ADDRESS}/meta.js";

    public MetaStorage(String rootDir, String publicDir, String privateDir) {
        super(rootDir, publicDir, privateDir);
    }

    public void showInfo() {
        String path = Template.replace(META_PATH, "PUBLIC", publicDirectory);
        Log.info("!!!      meta path: " + path);
    }

    private String getMetaPath(ID entity) {
        String path = META_PATH;
        path = Template.replace(path, "PUBLIC", publicDirectory);
        path = Template.replace(path, "ADDRESS", entity.getAddress().toString());
        return path;
    }

    @Override
    public boolean saveMeta(Meta meta, ID entity) {
        String path = getMetaPath(entity);
        try {
            return saveJSON(meta.toMap(), path) > 0;
        } catch (IOException e) {
            //e.printStackTrace();
            Log.error("failed to save meta: " + path);
            return false;
        }
    }

    @Override
    public Meta getMeta(ID entity) {
        String path = getMetaPath(entity);
        try {
            Object info = loadJSON(path);
            return Meta.parse(info);
        } catch (IOException e) {
            //e.printStackTrace();
            Log.error("failed to get meta: " + path);
            return null;
        }
    }
}
