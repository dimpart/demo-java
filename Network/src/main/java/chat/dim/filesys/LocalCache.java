/* license: https://mit-license.org
 *
 *  DIM-SDK : Decentralized Instant Messaging Software Development Kit
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
package chat.dim.filesys;

public enum LocalCache {

    INSTANCE;

    public static LocalCache getInstance() {
        return INSTANCE;
    }

    /**
     *  Base directory
     */
    private String base = "/tmp/.dim";  // "/sdcard/chat.dim.sechat"
    private boolean built = false;

    public void setRoot(String dir) {
        // lazy create
        base = dir;
        built = false;
    }
    public String getRoot() {
        if (built) {
            return base;
        }
        // make sure base directory built
        Paths.mkdirs(base);
        // forbid the gallery from scanning media files
        if (ExternalStorage.setNoMedia(base)) {
            built = true;
        }
        return base;
    }

    //
    //  Directories
    //

    /**
     *  Protected caches directory
     *  (meta/visa/document, image/audio/video, ...)
     *
     * @return "/sdcard/chat.dim.sechat/caches"
     */
    public String getCachesDirectory() {
        return Paths.append(getRoot(), "caches");
    }

    /**
     *  Protected temporary directory
     *  (uploading, downloaded)
     *
     * @return "/sdcard/chat.dim.sechat/tmp"
     */
    public String getTemporaryDirectory() {
        return Paths.append(getRoot(), "tmp");
    }

    //
    //  Paths
    //

    /**
     *  Avatar image file path
     *
     * @param filename - image filename: hex(md5(data)) + ext
     * @return "/sdcard/chat.dim.sechat/caches/avatar/{AA}/{BB}/{filename}"
     */
    public String getAvatarFilePath(String filename) {
        String dir = getCachesDirectory();
        String AA = filename.substring(0, 2);
        String BB = filename.substring(2, 4);
        return Paths.append(dir, "avatar", AA, BB, filename);
    }

    /**
     *  Cached file path
     *  (image, audio, video, ...)
     *
     * @param filename - messaged filename: hex(md5(data)) + ext
     * @return "/sdcard/chat.dim.sechat/caches/files/{AA}/{BB}/{filename}"
     */
    public String getCacheFilePath(String filename) {
        String dir = getCachesDirectory();
        String AA = filename.substring(0, 2);
        String BB = filename.substring(2, 4);
        return Paths.append(dir, "files", AA, BB, filename);
    }

    /**
     *  Encrypted data file path
     *
     * @param filename - messaged filename: hex(md5(data)) + ext
     * @return "/sdcard/chat.dim.sechat/tmp/upload/{filename}"
     */
    public String getUploadFilePath(String filename) {
        String dir = getTemporaryDirectory();
        return Paths.append(dir, "upload", filename);
    }

    /**
     *  Encrypted data file path
     *
     * @param filename - messaged filename: hex(md5(data)) + ext
     * @return "/sdcard/chat.dim.sechat/tmp/download/{filename}"
     */
    public String getDownloadFilePath(String filename) {
        String dir = getTemporaryDirectory();
        return Paths.append(dir, "download", filename);
    }
}
