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

package io.helidon.webserver.http2;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConnectionStreamTest {

    private static final int SETTINGS_MAX_CONCURRENT_STREAMS = 50;

    @Test
    void concurrentModification() throws InterruptedException {
        Http2ConnectionStreams streams = new Http2ConnectionStreams();
        for (int i = 1; i < SETTINGS_MAX_CONCURRENT_STREAMS * 2; i += 2) {
            Http2ServerStream s = mockStream(i);
            streams.put(new Http2Connection.StreamContext(i, 8192, s));
            streams.activate(i);
        }

        CountDownLatch removed = new CountDownLatch(streams.contexts().size());
        for (Http2Connection.StreamContext ctx : streams.contexts()) {
            Thread.ofVirtual().start(() -> {
                streams.remove(ctx.stream().streamId());
                removed.countDown();
            });
        }

        assertThat(removed.await(10, TimeUnit.SECONDS), Matchers.is(true));
        streams.doMaintenance();

        assertThat(streams.contexts().size(), Matchers.is(0));
        assertThat(streams.size(), Matchers.is(0));
    }

    @Test
    void queuedRemovalStopsCountingStreamAsActive() {
        Http2ConnectionStreams streams = new Http2ConnectionStreams();
        Http2ServerStream s = mockStream(1);

        streams.put(new Http2Connection.StreamContext(1, 8192, s));
        streams.activate(1);
        streams.remove(1);

        assertThat(streams.size(), Matchers.is(0));
        assertThat(streams.isEmpty(), Matchers.is(true));
    }

    @Test
    void duplicateRemovalDoesNotChangeActiveCount() {
        Http2ConnectionStreams streams = new Http2ConnectionStreams();
        Http2ServerStream first = mockStream(1);
        Http2ServerStream second = mockStream(3);

        streams.put(new Http2Connection.StreamContext(1, 8192, first));
        streams.activate(1);
        streams.remove(1);
        streams.doMaintenance();
        streams.remove(1);
        streams.put(new Http2Connection.StreamContext(3, 8192, second));
        streams.activate(3);

        assertThat(streams.size(), Matchers.is(1));
        assertThat(streams.isEmpty(), Matchers.is(false));
    }

    @Test
    void idleStreamDoesNotCountAsActive() {
        Http2ConnectionStreams streams = new Http2ConnectionStreams();
        Http2ServerStream s = mockStream(1);

        streams.put(new Http2Connection.StreamContext(1, 8192, s));

        assertThat(streams.size(), Matchers.is(0));
        assertThat(streams.isEmpty(), Matchers.is(true));
    }

    private static Http2ServerStream mockStream(int streamId) {
        Http2ServerStream s = mock(Http2ServerStream.class);
        when(s.streamId()).thenReturn(streamId);
        return s;
    }
}
