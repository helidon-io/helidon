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

package io.helidon.quic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class QuicExceptionNullContractTest {
    @Test
    void shouldRejectNullExceptionContext() {
        IllegalStateException cause = new IllegalStateException();

        assertThrows(NullPointerException.class,
                     () -> new QuicException(null));
        assertThrows(NullPointerException.class,
                     () -> new QuicException(null, cause));
        assertThrows(NullPointerException.class,
                     () -> new QuicException("failure", null));
        assertThrows(NullPointerException.class,
                     () -> new QuicPacketAuthenticationException(null, cause));
        assertThrows(NullPointerException.class,
                     () -> new QuicPacketAuthenticationException("failure", null));
        assertThrows(NullPointerException.class,
                     () -> new QuicStreamLimitException(null));
        assertThrows(NullPointerException.class,
                     () -> new QuicKeyUnavailableException(null, QuicTLSEngine.KeySpace.INITIAL));
        assertThrows(NullPointerException.class,
                     () -> new QuicKeyUnavailableException("failure", null));
        assertThrows(NullPointerException.class,
                     () -> new QuicServerRuntime.RejectedTlsSelectionException(null, true));
        assertThrows(NullPointerException.class,
                     () -> new QuicServerRuntime.RejectedTlsSelectionException(null, true, cause));
        assertThrows(NullPointerException.class,
                     () -> new QuicServerRuntime.RejectedTlsSelectionException("failure", true, null));
    }
}
