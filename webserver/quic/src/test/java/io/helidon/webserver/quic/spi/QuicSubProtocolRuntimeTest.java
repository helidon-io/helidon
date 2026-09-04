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

package io.helidon.webserver.quic.spi;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import io.helidon.quic.QuicConnection;
import io.helidon.webserver.spi.TransportBinding;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QuicSubProtocolRuntimeTest {
    @Test
    void rejectsNullGracePeriodBeforeClosingRuntime() {
        AtomicBoolean closed = new AtomicBoolean();
        QuicSubProtocolRuntime runtime = new QuicSubProtocolRuntime() {
            @Override
            public List<String> alpnIds() {
                return List.of("test");
            }

            @Override
            public void accept(QuicConnection connection) {
            }

            @Override
            public void close() {
                closed.set(true);
            }
        };

        assertThat(runtime.minimumPeerUniStreams(), is(0L));
        assertThrows(NullPointerException.class, () -> runtime.closeGracefully(null));
        assertThat(closed.get(), is(false));

        assertThat(runtime.closeGracefully(Duration.ZERO), is(TransportBinding.ShutdownResult.GRACEFUL));
        assertThat(closed.get(), is(true));
    }
}
