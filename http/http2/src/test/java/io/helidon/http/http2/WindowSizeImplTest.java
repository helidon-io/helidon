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

import java.util.function.BiConsumer;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class WindowSizeImplTest {

    private static final BiConsumer<Integer, Http2WindowUpdate> NOOP_WINDOW_UPDATE_WRITER = (streamId, windowUpdate) -> {
    };

    @Test
    void connectionWindowIncrementReturnsOverflowValueAsLong() {
        ConnectionFlowControl flowControl = ConnectionFlowControl.clientBuilder(NOOP_WINDOW_UPDATE_WRITER)
                .build();

        long remaining = flowControl.incrementOutboundConnectionWindowSize(WindowSize.MAX_WIN_SIZE);

        assertThat(remaining, is((long) WindowSize.DEFAULT_WIN_SIZE + WindowSize.MAX_WIN_SIZE));
        assertThat(flowControl.outbound().getRemainingWindowSize(), is(WindowSize.MAX_WIN_SIZE));
    }

    @Test
    void streamWindowIncrementReturnsOverflowValueAsLong() {
        ConnectionFlowControl flowControl = ConnectionFlowControl.clientBuilder(NOOP_WINDOW_UPDATE_WRITER)
                .build();
        StreamFlowControl streamFlowControl = flowControl.createStreamFlowControl(1,
                                                                                 WindowSize.DEFAULT_WIN_SIZE,
                                                                                 WindowSize.DEFAULT_MAX_FRAME_SIZE);

        long remaining = streamFlowControl.outbound().incrementStreamWindowSize(WindowSize.MAX_WIN_SIZE);

        assertThat(remaining, is((long) WindowSize.DEFAULT_WIN_SIZE + WindowSize.MAX_WIN_SIZE));
    }
}
