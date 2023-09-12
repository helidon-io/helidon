/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Stream;

import io.helidon.common.buffers.BufferData;
import io.helidon.logging.common.LogConfig;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static java.lang.System.Logger.Level.DEBUG;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class MaxFrameSizeSplitTest {

    private static final System.Logger LOGGER = System.getLogger(MaxFrameSizeSplitTest.class.getName());

    private static final String TEST_STRING = "Helidon data!!!!";
    private static final byte[] TEST_DATA = TEST_STRING.getBytes(StandardCharsets.UTF_8);

    @BeforeAll
    static void beforeAll() {
        LogConfig.configureRuntime();
    }

    private static Stream<SplitTest> splitMultiple() {
        return Stream.of(new SplitTest(17, 1, 16),
                         new SplitTest(16, 1, 16),
                         new SplitTest(15, 2, 1),
                         new SplitTest(14, 2, 2),
                         new SplitTest(13, 2, 3),
                         new SplitTest(12, 2, 4),
                         new SplitTest(11, 2, 5),
                         new SplitTest(10, 2, 6),
                         new SplitTest(9, 2, 7),
                         new SplitTest(8, 2, 8),
                         new SplitTest(7, 3, 2),
                         new SplitTest(6, 3, 4),
                         new SplitTest(5, 4, 1),
                         new SplitTest(4, 4, 4),
                         new SplitTest(3, 6, 1),
                         new SplitTest(2, 8, 2),
                         new SplitTest(1, 16, 1)
        );
    }

    @Test
    void splitHeaders() {
        BufferData bf = BufferData.create("This is so long text!");
        BufferData[] split = Http2Headers.split(bf, 12);
        assertThat(split.length, is(2));
        assertThat(split[0].available(), is(12));
        assertThat(split[1].available(), is(9));
    }

    @ParameterizedTest
    @MethodSource
    void splitMultiple(SplitTest args) {
        Http2FrameData frameData = createFrameData(TEST_DATA);
        Http2FrameData[] split = frameData.split(args.sizeOfFrames());
        assertThat("Unexpected number of frames", split.length, is(args.numberOfFrames()));

        BufferData joined = Stream.of(split)
                .collect(() -> BufferData.create(TEST_DATA.length),
                         (bb, b) -> bb.write(b.data()),
                         (bb, bb2) -> {
                         });

        assertThat("Result after split and join differs",
                   joined.readString(joined.available(), StandardCharsets.UTF_8),
                   is(TEST_STRING));

        // Reload data depleted by previous test
        split = createFrameData(TEST_DATA).split(args.sizeOfFrames());

        for (int i = 0; i < args.numberOfFrames() - 1; i++) {
            Http2FrameData frame = split[i];
            assertThat("Only last frame can have endOfStream flag",
                       frame.header().flags(Http2FrameTypes.DATA).endOfStream(),
                       is(false));

            byte[] bytes = toBytes(frame);
            LOGGER.log(DEBUG, i + ". frame: " + Arrays.toString(bytes));
            assertThat("Unexpected size of frame " + i, bytes.length, is(args.sizeOfFrames()));
        }

        Http2FrameData lastFrame = split[args.numberOfFrames() - 1];
        assertThat("Last frame is missing endOfStream flag",
                   lastFrame.header().flags(Http2FrameTypes.DATA).endOfStream(),
                   is(true));

        byte[] bytes = toBytes(lastFrame);
        LOGGER.log(DEBUG, args.numberOfFrames() - 1 + ". frame: " + Arrays.toString(bytes));
        assertThat("Unexpected size of the last frame", bytes.length, is(args.sizeOfLastFrame()));
    }

    private Http2FrameData createFrameData(byte[] data) {
        Http2FrameHeader http2FrameHeader = Http2FrameHeader.create(data.length,
                                                                    Http2FrameTypes.DATA,
                                                                    Http2Flag.DataFlags.create(Http2Flag.DataFlags.END_OF_STREAM),
                                                                    1);
        return new Http2FrameData(http2FrameHeader, BufferData.create(data));
    }

    private byte[] toBytes(Http2FrameData frameData) {
        return toBytes(frameData.data());
    }

    private byte[] toBytes(BufferData data) {
        byte[] b = new byte[data.available()];
        data.read(b);
        return b;
    }

    private record SplitTest(int sizeOfFrames,
                             int numberOfFrames,
                             int sizeOfLastFrame) {

    }
}
