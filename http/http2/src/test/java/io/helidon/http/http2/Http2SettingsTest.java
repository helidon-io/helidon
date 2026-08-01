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

class Http2SettingsTest {

    private static final int UNKNOWN_ID = 0x8; // SETTINGS_ENABLE_CONNECT_PROTOCOL (RFC 8441)
    private static final long BIG_WINDOW = 64L * 1024 * 1024;

    @Test
    void knownSettingsAreParsed() {
        BufferData frame = settingsBuffer(
                setting(Http2Setting.INITIAL_WINDOW_SIZE.identifier(), BIG_WINDOW),
                setting(Http2Setting.MAX_FRAME_SIZE.identifier(), 32_768L));

        Http2Settings settings = Http2Settings.create(frame);

        assertThat(settings.value(Http2Setting.INITIAL_WINDOW_SIZE), is(BIG_WINDOW));
        assertThat(settings.value(Http2Setting.MAX_FRAME_SIZE), is(32_768L));
        assertThat(settings.hasValue(Http2Setting.INITIAL_WINDOW_SIZE), is(true));
    }

    @Test
    void unknownSettingBeforeInitialWindowSizeIsSkipped() {
        // #12196: unknown id before INITIAL_WINDOW_SIZE must not drop the window setting
        BufferData frame = settingsBuffer(
                setting(UNKNOWN_ID, 0L),
                setting(Http2Setting.INITIAL_WINDOW_SIZE.identifier(), BIG_WINDOW));

        Http2Settings settings = Http2Settings.create(frame);

        assertThat(settings.hasValue(Http2Setting.INITIAL_WINDOW_SIZE), is(true));
        assertThat(settings.value(Http2Setting.INITIAL_WINDOW_SIZE), is(BIG_WINDOW));
    }

    @Test
    void unknownSettingAfterInitialWindowSizeIsSkipped() {
        BufferData frame = settingsBuffer(
                setting(Http2Setting.INITIAL_WINDOW_SIZE.identifier(), BIG_WINDOW),
                setting(UNKNOWN_ID, 0L));

        Http2Settings settings = Http2Settings.create(frame);

        assertThat(settings.value(Http2Setting.INITIAL_WINDOW_SIZE), is(BIG_WINDOW));
    }

    @Test
    void multipleUnknownSettingsDoNotCorruptLaterKnownSettings() {
        BufferData frame = settingsBuffer(
                setting(0x7, 1L),
                setting(UNKNOWN_ID, 0L),
                setting(0x63, 99L),
                setting(Http2Setting.MAX_CONCURRENT_STREAMS.identifier(), 100L),
                setting(Http2Setting.INITIAL_WINDOW_SIZE.identifier(), BIG_WINDOW));

        Http2Settings settings = Http2Settings.create(frame);

        assertThat(settings.value(Http2Setting.MAX_CONCURRENT_STREAMS), is(100L));
        assertThat(settings.value(Http2Setting.INITIAL_WINDOW_SIZE), is(BIG_WINDOW));
    }

    @Test
    void onlyUnknownSettingsYieldsEmptyPresentValues() {
        BufferData frame = settingsBuffer(
                setting(UNKNOWN_ID, 1L),
                setting(0x63, 2L));

        Http2Settings settings = Http2Settings.create(frame);

        assertThat(settings.hasValue(Http2Setting.INITIAL_WINDOW_SIZE), is(false));
        assertThat(settings.value(Http2Setting.INITIAL_WINDOW_SIZE),
                   is((long) WindowSize.DEFAULT_WIN_SIZE));
        assertThat(settings.presentValue(Http2Setting.ENABLE_PUSH).isEmpty(), is(true));
    }

    private static byte[] setting(int identifier, long value) {
        BufferData tmp = BufferData.create(6);
        tmp.writeInt16(identifier);
        tmp.writeUnsignedInt32(value);
        byte[] bytes = new byte[6];
        tmp.read(bytes);
        return bytes;
    }

    private static BufferData settingsBuffer(byte[]... settings) {
        int size = settings.length * 6;
        byte[] all = new byte[size];
        int offset = 0;
        for (byte[] setting : settings) {
            System.arraycopy(setting, 0, all, offset, setting.length);
            offset += setting.length;
        }
        return BufferData.create(all);
    }
}
