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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;

class QuicTransportErrorsTest {
    @Test
    void shouldResolveKnownErrorCode() {
        assertThat(QuicTransportErrors.ofCode(QuicTransportErrors.PROTOCOL_VIOLATION.code()).orElseThrow(),
                   is(QuicTransportErrors.PROTOCOL_VIOLATION));
    }

    @Test
    void shouldResolveCryptoErrorRange() {
        assertThat(QuicTransportErrors.ofCode(0x0100 + 40).orElseThrow(), is(QuicTransportErrors.CRYPTO_ERROR));
        assertThat(QuicTransportErrors.text(0x0100 + 40), is("CRYPTO_ERROR|0x128"));
    }

    @Test
    void shouldReturnEmptyForUnknownErrorCode() {
        assertThat(QuicTransportErrors.ofCode(0xFFFF_FFFFL).stream().toList(), empty());
    }

    @Test
    void shouldRenderUnknownErrorCode() {
        assertThat(QuicTransportErrors.text(0x12345), is("Unknown [0x12345]"));
    }
}
