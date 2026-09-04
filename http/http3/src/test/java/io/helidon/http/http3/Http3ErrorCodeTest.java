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

package io.helidon.http.http3;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import static java.util.Map.entry;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;

class Http3ErrorCodeTest {
    @Test
    void shouldModelEveryStandardErrorCode() {
        Map<Http3ErrorCode, Long> expected = Map.ofEntries(
                entry(Http3ErrorCode.NO_ERROR, 0x0100L),
                entry(Http3ErrorCode.GENERAL_PROTOCOL_ERROR, 0x0101L),
                entry(Http3ErrorCode.INTERNAL_ERROR, 0x0102L),
                entry(Http3ErrorCode.STREAM_CREATION_ERROR, 0x0103L),
                entry(Http3ErrorCode.CLOSED_CRITICAL_STREAM, 0x0104L),
                entry(Http3ErrorCode.FRAME_UNEXPECTED, 0x0105L),
                entry(Http3ErrorCode.FRAME_ERROR, 0x0106L),
                entry(Http3ErrorCode.EXCESSIVE_LOAD, 0x0107L),
                entry(Http3ErrorCode.ID_ERROR, 0x0108L),
                entry(Http3ErrorCode.SETTINGS_ERROR, 0x0109L),
                entry(Http3ErrorCode.MISSING_SETTINGS, 0x010aL),
                entry(Http3ErrorCode.REQUEST_REJECTED, 0x010bL),
                entry(Http3ErrorCode.REQUEST_CANCELLED, 0x010cL),
                entry(Http3ErrorCode.REQUEST_INCOMPLETE, 0x010dL),
                entry(Http3ErrorCode.MESSAGE_ERROR, 0x010eL),
                entry(Http3ErrorCode.CONNECT_ERROR, 0x010fL),
                entry(Http3ErrorCode.VERSION_FALLBACK, 0x0110L),
                entry(Http3ErrorCode.QPACK_DECOMPRESSION_FAILED, 0x0200L),
                entry(Http3ErrorCode.QPACK_ENCODER_STREAM_ERROR, 0x0201L),
                entry(Http3ErrorCode.QPACK_DECODER_STREAM_ERROR, 0x0202L));

        assertThat(expected.entrySet(), hasSize(Http3ErrorCode.values().length));
        assertThat(new HashSet<>(expected.values()), hasSize(expected.size()));
        expected.forEach((errorCode, code) -> {
            assertThat(errorCode.code(), equalTo(code));
            assertThat(Http3ErrorCode.find(code).orElseThrow(), sameInstance(errorCode));
            assertThat(Http3Protocol.applicationErrorToString(code), equalTo(errorCode.wireName()));
        });
    }

    @Test
    void shouldKeepExtensionCodesUntyped() {
        assertThat(Http3ErrorCode.find(0x21), is(Optional.empty()));
        assertThat(Http3Protocol.applicationErrorToString(0x21), equalTo("ApplicationError(code=0x0000000000000021)"));
    }
}
