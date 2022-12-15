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
package chat.dim.dbi;

import java.util.List;

import chat.dim.crypto.DecryptKey;
import chat.dim.crypto.PrivateKey;
import chat.dim.protocol.ID;

/**
 *  Account DBI
 *  ~~~~~~~~~~~
 */
public interface PrivateKeyDBI {

    String META = "M";
    String VISA = "V";

    /**
     *  Save private key for user
     *
     * @param user - user ID
     * @param key - private key
     * @param type - 'M' for matching meta.key; or 'P' for matching profile.key
     * @return false on error
     */
    boolean savePrivateKey(PrivateKey key, String type, ID user);

    /**
     *  Get private keys for user
     *
     * @param user - user ID
     * @return all keys marked for decryption
     */
    List<DecryptKey> getPrivateKeysForDecryption(ID user);

    /**
     *  Get private key for user
     *
     * @param user - user ID
     * @return first key marked for signature
     */
    PrivateKey getPrivateKeyForSignature(ID user);

    /**
     *  Get private key for user
     *
     * @param user - user ID
     * @return the private key matched with meta.key
     */
    PrivateKey getPrivateKeyForVisaSignature(ID user);
}
