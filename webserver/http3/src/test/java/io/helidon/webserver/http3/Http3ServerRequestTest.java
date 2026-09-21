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

package io.helidon.webserver.http3;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import io.helidon.common.buffers.BufferData;
import io.helidon.http.HttpPrologue;
import io.helidon.http.Method;
import io.helidon.http.ServerRequestHeaders;
import io.helidon.http.encoding.ContentDecoder;
import io.helidon.webserver.ConnectionContext;
import io.helidon.webserver.http.HttpSecurity;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class Http3ServerRequestTest {
    static Http3ServerRequest request(ConnectionContext context) {
        HttpPrologue prologue = HttpPrologue.create("HTTP/3",
                                                    "HTTP",
                                                    "3",
                                                    Method.GET,
                                                    "/before/routing",
                                                    true);
        Http3ServerRequest.RequestMeta metadata = new Http3ServerRequest.RequestMeta(
                1,
                false,
                _ -> BufferData.empty(),
                () -> {
                },
                () -> {
                },
                null,
                new Http3ServerRequest.EntityLimits(1024, 1024));
        return Http3ServerRequest.create(context,
                                         mock(HttpSecurity.class),
                                         prologue,
                                         ServerRequestHeaders.create(),
                                         "example.com",
                                         ContentDecoder.NO_OP,
                                         metadata);
    }

    @Test
    void matchingPatternSupplierIsLazyAndReplaceable() {
        Http3ServerRequest request = request(mock(ConnectionContext.class));
        AtomicInteger invocations = new AtomicInteger();
        request.matchingPattern(() -> {
            invocations.incrementAndGet();
            return Optional.of("/resource/{id}");
        });

        assertThat(invocations.get(), is(0));
        assertThat(request.matchingPattern(), is(Optional.of("/resource/{id}")));
        assertThat(request.matchingPattern(), is(Optional.of("/resource/{id}")));
        assertThat(invocations.get(), is(1));
        assertThrows(NullPointerException.class,
                     () -> request.matchingPattern((Supplier<Optional<String>>) null));

        request.matchingPattern(Optional::empty);
        assertThat(request.matchingPattern(), is(Optional.empty()));
        request.matchingPattern("/final");
        assertThat(request.matchingPattern(), is(Optional.of("/final")));
        request.matchingPattern((String) null);
        assertThat(request.matchingPattern(), is(Optional.empty()));
    }

    @Test
    void matchingPatternSupplierRejectsNullResult() {
        Http3ServerRequest request = request(mock(ConnectionContext.class));
        request.matchingPattern(() -> null);

        assertThrows(NullPointerException.class, request::matchingPattern);
    }

    @Test
    void providesPathBeforeRouting() {
        Http3ServerRequest request = request(mock(ConnectionContext.class));

        assertThat(request.path(), notNullValue());
        assertThat(request.path().path(), is("/before/routing"));
    }

    @Test
    void rejectsNullRoutingMetadata() {
        Http3ServerRequest request = request(mock(ConnectionContext.class));

        assertThrows(NullPointerException.class, () -> request.path(null));
        assertThrows(NullPointerException.class, () -> request.prologue(null));
    }
}
