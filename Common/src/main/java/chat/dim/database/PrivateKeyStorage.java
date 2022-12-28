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
import java.util.ArrayList;
import java.util.List;

import chat.dim.crypto.DecryptKey;
import chat.dim.crypto.PrivateKey;
import chat.dim.dbi.PrivateKeyDBI;
import chat.dim.protocol.ID;
import chat.dim.utils.Log;
import chat.dim.utils.Template;

/**
 *  Private Key Storage
 *  ~~~~~~~~~~~~~~~~~~~
 *
 *  (1) Identify Key - paired to meta.key, CONSTANT
 *      file path: '.dim/private/{ADDRESS}/secret.js'
 *  (2) Message Keys - paired to visa.key, VOLATILE
 *      file path: '.dim/private/{ADDRESS}/secret_keys.js'
 */
public class PrivateKeyStorage extends Storage implements PrivateKeyDBI {

    public static String ID_KEY_PATH = "{PRIVATE}/{ADDRESS}/secret.js";
    public static String MSG_KEYS_PATH = "{PRIVATE}/{ADDRESS}/secret_keys.js";

    public PrivateKeyStorage(String rootDir, String publicDir, String privateDir) {
        super(rootDir, publicDir, privateDir);
    }

    public void showInfo() {
        String path1 = Template.replace(ID_KEY_PATH, "PRIVATE", privateDirectory);
        String path2 = Template.replace(MSG_KEYS_PATH, "PRIVATE", privateDirectory);
        Log.info("!!!    id key path: " + path1);
        Log.info("!!!  msg keys path: " + path2);
    }

    private String getIdKeyPath(ID entity) {
        String path = ID_KEY_PATH;
        path = Template.replace(path, "PRIVATE", privateDirectory);
        path = Template.replace(path, "ADDRESS", entity.getAddress().toString());
        return path;
    }
    protected PrivateKey loadIdKey(ID identifier) {
        String path = getIdKeyPath(identifier);
        try {
            Object info = loadJSON(path);
            return PrivateKey.parse(info);
        } catch (IOException e) {
            //e.printStackTrace();
            Log.error("failed to load id key: " + path);
            return null;
        }
    }
    protected boolean saveIdKey(PrivateKey key, ID identifier) {
        String path = getIdKeyPath(identifier);
        try {
            return saveJSON(key.toMap(), path) > 0;
        } catch (IOException e) {
            //e.printStackTrace();
            Log.error("failed to save id key: " + path);
            return false;
        }
    }

    private String getMsgKeysPath(ID entity) {
        String path = MSG_KEYS_PATH;
        path = Template.replace(path, "PRIVATE", privateDirectory);
        path = Template.replace(path, "ADDRESS", entity.getAddress().toString());
        return path;
    }
    @SuppressWarnings("unchecked")
    protected List<PrivateKey> loadMsgKeys(ID identifier) {
        List<PrivateKey> privateKeys = new ArrayList<>();
        String path = getMsgKeysPath(identifier);
        try {
            Object content = loadJSON(path);
            if (content != null) {
                PrivateKey key;
                List<Object> array = (List<Object>) content;
                for (Object item : array) {
                    key = PrivateKey.parse(item);
                    assert key != null : "private key error: " + item;
                    privateKeys.add(key);
                }
            }
        } catch (IOException e) {
            //e.printStackTrace();
            Log.info("failed to load msg keys from: " + path);
        }
        return privateKeys;
    }
    protected boolean saveMsgKey(PrivateKey key, ID identifier) {
        List<PrivateKey> privateKeys = loadMsgKeys(identifier);
        privateKeys = PrivateKeyDBI.insertKey(key, privateKeys);
        if (privateKeys == null) {
            // nothing changed
            return false;
        }
        List<?> plain = PrivateKeyDBI.revertPrivateKeys(privateKeys);
        String path = getMsgKeysPath(identifier);
        try {
            return saveJSON(plain, path) > 0;
        } catch (IOException e) {
            //e.printStackTrace();
            Log.error("failed to save msg key: " + path);
            return false;
        }
    }

    @Override
    public boolean savePrivateKey(PrivateKey key, String type, ID user) {
        if (type == null || type.equals(META)) {
            // save private key for meta
            return saveIdKey(key, user);
        } else {
            // save private key for visa
            return saveMsgKey(key, user);
        }
    }

    @Override
    public List<DecryptKey> getPrivateKeysForDecryption(ID user) {
        List<PrivateKey> privateKeys = loadMsgKeys(user);
        // the 'ID key' could be used for encrypting message too (RSA),
        // so we append it to the decrypt keys here
        PrivateKey idKey = loadIdKey(user);
        if (idKey instanceof DecryptKey) {
            if (PrivateKeyDBI.findKey(idKey, privateKeys) < 0) {
                privateKeys.add(idKey);
            }
        }
        return PrivateKeyDBI.convertDecryptKeys(privateKeys);
    }

    @Override
    public PrivateKey getPrivateKeyForSignature(ID user) {
        return getPrivateKeyForVisaSignature(user);
    }

    @Override
    public PrivateKey getPrivateKeyForVisaSignature(ID user) {
        return loadIdKey(user);
    }
}
