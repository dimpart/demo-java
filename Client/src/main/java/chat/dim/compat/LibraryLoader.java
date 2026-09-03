/* license: https://mit-license.org
 * ==============================================================================
 * The MIT License (MIT)
 *
 * Copyright (c) 2025 Albert Moky
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
package chat.dim.compat;

import chat.dim.CryptoPluginLoader;
import chat.dim.ExtensionLoader;
import chat.dim.PluginLoader;


public class LibraryLoader implements Runnable {

    private final ExtensionLoader extensionLoader;
    private final PluginLoader pluginLoader;
    private final CryptoPluginLoader cryptoLoader;

    private boolean loaded = false;

    public LibraryLoader(ExtensionLoader extensionLoader, PluginLoader pluginLoader, CryptoPluginLoader cryptoLoader) {

        if (extensionLoader == null) {
            this.extensionLoader = new ClientExtensionLoader();
        } else {
            this.extensionLoader = extensionLoader;
        }

        if (pluginLoader == null) {
            this.pluginLoader = new ClientPluginLoader();
        } else {
            this.pluginLoader = pluginLoader;
        }

        if (cryptoLoader == null) {
            this.cryptoLoader = new CryptoPluginLoader();
        } else {
            this.cryptoLoader = cryptoLoader;
        }
    }

    @Override
    public void run() {
        if (loaded) {
            // no need to load it again
            return;
        } else {
            // mark it to loaded
            loaded = true;
        }
        // try to load all extensions
        load();
    }

    protected void load() {
        extensionLoader.load();
        pluginLoader.load();
        cryptoLoader.load();
    }

}
