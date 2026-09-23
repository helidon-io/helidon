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

package io.helidon.webserver;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HttpTransportObserverSupportTest {
    @Test
    void recognizesSocketTimeout() {
        assertThat(HttpTransportObserverSupport.isTimeout(new SocketTimeoutException()), is(true));
    }

    @Test
    void recognizesConcurrentTimeout() {
        assertThat(HttpTransportObserverSupport.isTimeout(new TimeoutException()), is(true));
    }

    @Test
    void recognizesNestedSocketTimeout() {
        var failure = new IllegalStateException(new UncheckedIOException(new SocketTimeoutException()));

        assertThat(HttpTransportObserverSupport.isTimeout(failure), is(true));
    }

    @Test
    void recognizesNestedConcurrentTimeout() {
        var failure = new IllegalStateException(new IOException(new TimeoutException()));

        assertThat(HttpTransportObserverSupport.isTimeout(failure), is(true));
    }

    @Test
    void doesNotClassifyOtherFailuresAsTimeouts() {
        var failure = new IllegalStateException("timeout", new IOException("read failed"));

        assertThat(HttpTransportObserverSupport.isTimeout(failure), is(false));
    }

    @Test
    void stopsAtSelfReferencingCause() {
        var failure = new IllegalStateException() {
            private boolean causeRead;

            @Override
            public synchronized Throwable getCause() {
                assertThat("a self-referencing cause must not be revisited", causeRead, is(false));
                causeRead = true;
                return this;
            }
        };

        assertThat(HttpTransportObserverSupport.isTimeout(failure), is(false));
    }

    @Test
    void rejectsNullFailure() {
        assertThrows(NullPointerException.class, () -> HttpTransportObserverSupport.isTimeout(null));
    }
}
