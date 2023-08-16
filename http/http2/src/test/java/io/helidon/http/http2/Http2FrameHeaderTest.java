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

import java.util.HexFormat;

import io.helidon.common.buffers.BufferData;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class Http2FrameHeaderTest {
    @Test
    void testWriteRead() {
        int length = 439;
        Http2FrameTypes<Http2Flag.NoFlags> frameType = Http2FrameTypes.GO_AWAY;
        Http2Flag.NoFlags flags = Http2Flag.NoFlags.create();
        int streamId = 0;

        Http2FrameHeader frameHeader = Http2FrameHeader.create(length, frameType, flags, streamId);
        BufferData buffer = frameHeader.write();

        Http2FrameHeader header = Http2FrameHeader.create(buffer);

        assertAll(
                () -> assertThat("Frame length", header.length(), is(length)),
                () -> assertThat("Stream ID", header.streamId(), is(streamId)),
                () -> assertThat("Flags", header.flags(), is(flags.value())),
                () -> assertThat(header.type(), is(frameType.type()))
        );
    }

    BufferData dataFromHex(String hexEncoded) {
        byte[] bytes = HexFormat.of().parseHex(hexEncoded.replace(" ", ""));
        return BufferData.create(bytes);
    }

    @Test
    void testOver128() {
        Http2FrameHeader header = Http2FrameHeader.create(3,
                                                          Http2FrameTypes.HEADERS,
                                                          Http2Flag.HeaderFlags.create(Http2Flag.END_OF_HEADERS),
                                                          129);
        BufferData data = header.write();

        header = Http2FrameHeader.create(data);

        assertThat(header.length(), is(3));
        assertThat(header.type(), is(Http2FrameType.HEADERS));
        assertThat(header.flags(), is(Http2Flag.HeaderFlags.create(Http2Flag.END_OF_HEADERS).value()));
        assertThat(header.streamId(), is(129));
    }
}