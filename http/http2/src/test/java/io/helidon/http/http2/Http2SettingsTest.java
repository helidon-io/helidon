/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class Http2SettingsTest {
    @Test
    void ignoresUnsupportedRegisteredSetting() {
        Http2Settings settings = Http2Settings.create(frameData(0x8, 1,
                                                                 Http2Setting.INITIAL_WINDOW_SIZE.identifier(), 0));

        assertAll(
                () -> assertThat("Initial window setting is present",
                                 settings.hasValue(Http2Setting.INITIAL_WINDOW_SIZE), is(true)),
                () -> assertThat("Initial window size", settings.value(Http2Setting.INITIAL_WINDOW_SIZE), is(0L))
        );
    }

    @Test
    void unsupportedSettingDoesNotCorruptKnownSettings() {
        // The high 16 bits of the unsupported value look like HEADER_TABLE_SIZE.
        Http2Settings settings = Http2Settings.create(frameData(Http2Setting.HEADER_TABLE_SIZE.identifier(), 256,
                                                                 0xF001, 0x00010000,
                                                                 Http2Setting.INITIAL_WINDOW_SIZE.identifier(), 32_768));

        assertAll(
                () -> assertThat("Header table size", settings.value(Http2Setting.HEADER_TABLE_SIZE), is(256L)),
                () -> assertThat("Initial window setting is present",
                                 settings.hasValue(Http2Setting.INITIAL_WINDOW_SIZE), is(true)),
                () -> assertThat("Initial window size",
                                 settings.value(Http2Setting.INITIAL_WINDOW_SIZE), is(32_768L))
        );
    }

    private static BufferData frameData(long... identifierValues) {
        BufferData frame = BufferData.create(identifierValues.length / 2 * 6);
        for (int i = 0; i < identifierValues.length; i += 2) {
            frame.writeInt16((int) identifierValues[i]);
            frame.writeUnsignedInt32(identifierValues[i + 1]);
        }
        return frame;
    }
}
