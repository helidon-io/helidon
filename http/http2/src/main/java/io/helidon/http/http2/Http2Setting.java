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

import java.util.Map;

import io.helidon.common.buffers.BufferData;

/**
 * HTTP/2 setting.
 *
 * @param <T> type of the setting
 */
public interface Http2Setting<T> {
    /**
     * Setting for header table size.
     */
    Http2Setting<Long> HEADER_TABLE_SIZE = new Http2SettingBase.NumberSetting("HEADER_TABLE_SIZE", 0x1, 4096L);
    /**
     * Setting to enable or disable push.
     */
    Http2Setting<Boolean> ENABLE_PUSH = new Http2SettingBase.BooleanSetting("ENABLE_PUSH", 0x2, true);
    /**
     * Setting to define maximal number of open streams.
     */
    Http2Setting<Long> MAX_CONCURRENT_STREAMS = new Http2SettingBase.NumberSetting("MAX_CONCURRENT_STREAMS",
                                                                                   0x3,
                                                                                   Http2SettingBase.MAX_UNSIGNED_INT);
    /**
     * Setting to define initial window size.
     */
    Http2Setting<Long> INITIAL_WINDOW_SIZE = new Http2SettingBase.NumberSetting("INITIAL_WINDOW_SIZE",
                                                                                0x4,
                                                                                (long) WindowSize.DEFAULT_WIN_SIZE);
    /**
     * Setting to define maximal frame size.
     */
    Http2Setting<Long> MAX_FRAME_SIZE = new Http2SettingBase.NumberSetting("MAX_FRAME_SIZE", 0x5, 16_384L);
    /**
     * Setting to define maximal header list size.
     */
    Http2Setting<Long> MAX_HEADER_LIST_SIZE = new Http2SettingBase.NumberSetting("MAX_HEADER_LIST_SIZE ",
                                                                                 0x6,
                                                                                 Http2SettingBase.MAX_UNSIGNED_INT);
    /**
     * Settings mapped by setting identifier.
     */
    Map<Integer, Http2Setting<?>> BY_ID = Map.of(HEADER_TABLE_SIZE.identifier(), HEADER_TABLE_SIZE,
                                                 ENABLE_PUSH.identifier(), ENABLE_PUSH,
                                                 MAX_CONCURRENT_STREAMS.identifier(), MAX_CONCURRENT_STREAMS,
                                                 INITIAL_WINDOW_SIZE.identifier(), INITIAL_WINDOW_SIZE,
                                                 MAX_FRAME_SIZE.identifier(), MAX_FRAME_SIZE,
                                                 MAX_HEADER_LIST_SIZE.identifier(), MAX_HEADER_LIST_SIZE);

    /**
     * Setting identifier.
     *
     * @return identifier
     */
    int identifier();

    /**
     * Typed default value of this setting.
     *
     * @return default value
     */
    T defaultValue();

    /**
     * Read setting value from the frame buffer.
     *
     * @param frame frame buffer
     * @return value of setting
     */
    T read(BufferData frame);

    /**
     * Write setting to the provided buffer.
     *
     * @param data  buffer to write setting to
     * @param value value to write
     */
    void write(BufferData data, T value);
}
