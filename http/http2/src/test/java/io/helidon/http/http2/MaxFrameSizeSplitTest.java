/*
 * Copyright (c) 2023, 2026 Oracle and/or its affiliates.
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

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.socket.PeerInfo;
import io.helidon.common.socket.SocketContext;
import io.helidon.http.HeaderNames;
import io.helidon.http.WritableHeaders;
import io.helidon.logging.common.LogConfig;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static java.lang.System.Logger.Level.DEBUG;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertAll;

class MaxFrameSizeSplitTest {

    private static final System.Logger LOGGER = System.getLogger(MaxFrameSizeSplitTest.class.getName());

    private static final String TEST_STRING = "Helidon data!!!!";
    private static final byte[] TEST_DATA = TEST_STRING.getBytes(StandardCharsets.UTF_8);
    private static final SocketAddress TEST_SOCKET_ADDRESS = InetSocketAddress.createUnresolved("localhost", 0);
    private static final PeerInfo TEST_PEER_INFO = new PeerInfo() {
        @Override
        public SocketAddress address() {
            return TEST_SOCKET_ADDRESS;
        }

        @Override
        public String host() {
            return "localhost";
        }

        @Override
        public int port() {
            return 0;
        }

        @Override
        public Optional<Principal> tlsPrincipal() {
            return Optional.empty();
        }

        @Override
        public Optional<Certificate[]> tlsCertificates() {
            return Optional.empty();
        }
    };
    private static final SocketContext TEST_SOCKET_CONTEXT = new SocketContext() {
        @Override
        public PeerInfo remotePeer() {
            return TEST_PEER_INFO;
        }

        @Override
        public PeerInfo localPeer() {
            return TEST_PEER_INFO;
        }

        @Override
        public boolean isSecure() {
            return false;
        }

        @Override
        public String socketId() {
            return "test";
        }

        @Override
        public String childSocketId() {
            return "test-child";
        }
    };

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

    @Test
    void writeSplitHeadersOnceAndPreserveEndStream() {
        int maxFrameSize = 32;
        int streamId = 3;
        WritableHeaders<?> httpHeaders = WritableHeaders.create();
        httpHeaders.add(Http2Headers.STATUS_NAME, "200");
        httpHeaders.add(HeaderNames.create("x-large-header"), "abc".repeat(128));
        Http2Headers http2Headers = Http2Headers.create(httpHeaders);

        BufferData encodedHeaders = BufferData.growing(512);
        http2Headers.write(Http2Headers.DynamicTable.create(Http2Setting.HEADER_TABLE_SIZE.defaultValue()),
                           Http2HuffmanEncoder.create(),
                           encodedHeaders);
        BufferData[] expectedFragments = Http2Headers.split(encodedHeaders, maxFrameSize);
        assertThat("Test setup must force a split header block", expectedFragments.length > 1, is(true));

        List<byte[]> writes = new ArrayList<>();
        DataWriter dataWriter = new DataWriter() {
            @Override
            public void write(BufferData... buffers) {
                writeNow(buffers);
            }

            @Override
            public void write(BufferData buffer) {
                writeNow(buffer);
            }

            @Override
            public void writeNow(BufferData... buffers) {
                writeNow(BufferData.create(buffers));
            }

            @Override
            public void writeNow(BufferData buffer) {
                writes.add(buffer.readBytes());
            }
        };
        FlowControl.Outbound flowControl = new FlowControl.Outbound() {
            @Override
            public void decrementWindowSize(int decrement) {
            }

            @Override
            public void resetStreamWindowSize(int size) {
            }

            @Override
            public int getRemainingWindowSize() {
                return Integer.MAX_VALUE;
            }

            @Override
            public long incrementStreamWindowSize(int increment) {
                return Integer.MAX_VALUE;
            }

            @Override
            public Http2FrameData[] cut(Http2FrameData frame) {
                return new Http2FrameData[] {frame};
            }

            @Override
            public void blockTillUpdate() {
            }

            @Override
            public int maxFrameSize() {
                return maxFrameSize;
            }
        };

        Http2ConnectionWriter writer = new Http2ConnectionWriter(TEST_SOCKET_CONTEXT, dataWriter, List.of());
        writer.writeHeaders(http2Headers,
                            streamId,
                            Http2Flag.HeaderFlags.create(Http2Flag.END_OF_HEADERS | Http2Flag.END_OF_STREAM),
                            flowControl);

        List<Http2FrameHeader> writtenHeaders = new ArrayList<>();
        for (byte[] write : writes) {
            BufferData frameBytes = BufferData.create(write);
            Http2FrameHeader header = Http2FrameHeader.create(frameBytes);
            writtenHeaders.add(header);
            assertThat("Frame payload length must match the serialized frame header",
                       frameBytes.available(),
                       is(header.length()));
        }

        assertAll(
                () -> assertThat("Unexpected number of frames for one split header block",
                                 writtenHeaders.size(),
                                 is(expectedFragments.length)),
                () -> assertThat("First frame type", writtenHeaders.get(0).type(), is(Http2FrameType.HEADERS)),
                () -> assertThat("First HEADERS frame must preserve END_STREAM",
                                 writtenHeaders.get(0).flags(Http2FrameTypes.HEADERS).endOfStream(),
                                 is(true)),
                () -> assertThat("First HEADERS frame must defer END_HEADERS",
                                 writtenHeaders.get(0).flags(Http2FrameTypes.HEADERS).endOfHeaders(),
                                 is(false)),
                () -> assertThat("Final header-block frame type",
                                 writtenHeaders.get(expectedFragments.length - 1).type(),
                                 is(Http2FrameType.CONTINUATION)),
                () -> assertThat("Final continuation must end the header block",
                                 writtenHeaders.get(expectedFragments.length - 1)
                                         .flags(Http2FrameTypes.CONTINUATION)
                                         .endOfHeaders(),
                                 is(true))
        );
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
