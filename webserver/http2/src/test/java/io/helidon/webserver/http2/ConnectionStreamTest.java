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

package io.helidon.webserver.http2;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;

class ConnectionStreamTest {

    private static final int SETTINGS_MAX_CONCURRENT_STREAMS = 50;

    @Test
    void concurrentModification() {
        Http2ServerStream s = mock(Http2ServerStream.class);
        Http2ConnectionStreams streams = new Http2ConnectionStreams();
        for (int i = 1; i < 10_000; i += 2) {

            streams.doMaintenance(SETTINGS_MAX_CONCURRENT_STREAMS);
            assertThat(streams.size(), Matchers.lessThanOrEqualTo(SETTINGS_MAX_CONCURRENT_STREAMS));

            streams.put(new Http2Connection.StreamContext(i, 8192, s));
            int toRemoveStreamId = i;
            for (Http2Connection.StreamContext ctx : streams.contexts()) {
                Thread.ofVirtual().start(() -> streams.remove(toRemoveStreamId));
            }
        }
    }
}
