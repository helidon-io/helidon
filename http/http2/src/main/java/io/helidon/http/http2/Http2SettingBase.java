/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.helidon.http.http2;

import io.helidon.common.buffers.BufferData;

/**
 * Base of settings.
 *
 * @param <T> type of setting
 */
abstract class Http2SettingBase<T> implements Http2Setting<T> {
    /**
     * Maximal size of an unsigned integer, represented as long.
     */
    static final long MAX_UNSIGNED_INT = 0xFFFFFFFFL;

    private final String name;
    private final T defaultValue;
    private final int identifier;

    Http2SettingBase(String name, int identifier, T defaultValue) {
        this.name = name;
        this.identifier = identifier;
        this.defaultValue = defaultValue;
    }

    @Override
    public int identifier() {
        return identifier;
    }

    @Override
    public T defaultValue() {
        return defaultValue;
    }

    @Override
    public String toString() {
        return name;
    }

    protected String name() {
        return name;
    }

    static final class BooleanSetting extends Http2SettingBase<Boolean> {
        BooleanSetting(String name, int identifier, Boolean defaultValue) {
            super(name, identifier, defaultValue);
        }

        @Override
        public Boolean read(BufferData frame) {
            int value = frame.readInt32();
            if (value == 0) {
                return false;
            }
            if (value == 1) {
                return true;
            }
            throw new Http2Exception(Http2ErrorCode.PROTOCOL, "Setting " + name()
                    + " only allows values 0 and 1, actual value: " + value);
        }

        @Override
        public void write(BufferData data, Boolean value) {
            data.writeInt16(identifier());
            data.writeInt32(value ? 1 : 0);
        }
    }

    static final class NumberSetting extends Http2SettingBase<Long> {
        // todo the max value can be 32bit unsigned integer (e.g. not long max value)
        NumberSetting(String name, int identifier, Long defaultValue) {
            super(name, identifier, checkMax(name, defaultValue));
        }

        @Override
        public Long read(BufferData frame) {
            return frame.readUnsignedInt32();
        }

        @Override
        public void write(BufferData data, Long value) {
            checkMax(name(), value);
            data.writeInt16(identifier());
            data.writeUnsignedInt32(value);
        }

        private static Long checkMax(String name, long value) {
            if (value > MAX_UNSIGNED_INT) {
                throw new Http2Exception(Http2ErrorCode.PROTOCOL, "Value too big for setting "
                        + name + ", value: " + value);
            }
            return value;
        }
    }
}
