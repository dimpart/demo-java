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
package chat.dim.type;

import java.util.Date;

import chat.dim.base.BaseConverter;

public class SafeConverter extends BaseConverter {

    @Override
    public Boolean getBoolean(Object value, Boolean defaultValueIfNull) {
        try {
            return super.getBoolean(value, defaultValueIfNull);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public Byte getByte(Object value, Byte defaultValueIfNull) {
        try {
            return super.getByte(value, defaultValueIfNull);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public Short getShort(Object value, Short defaultValueIfNull) {
        try {
            return super.getShort(value, defaultValueIfNull);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public Integer getInteger(Object value, Integer defaultValueIfNull) {
        try {
            return super.getInteger(value, defaultValueIfNull);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public Long getLong(Object value, Long defaultValueIfNull) {
        try {
            return super.getLong(value, defaultValueIfNull);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public Float getFloat(Object value, Float defaultValueIfNull) {
        try {
            return super.getFloat(value, defaultValueIfNull);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public Double getDouble(Object value, Double defaultValueIfNull) {
        try {
            return super.getDouble(value, defaultValueIfNull);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public Date getDateTime(Object value, Date defaultValueIfNull) {
        try {
            return super.getDateTime(value, defaultValueIfNull);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
