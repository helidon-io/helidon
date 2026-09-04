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

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Http3ProtocolExceptionTest {
    @Test
    void shouldRetainNonNullDiagnosticsWithoutCause() {
        Http3ProtocolException failure = Http3ProtocolException.connectionError(
                Http3ErrorCode.GENERAL_PROTOCOL_ERROR, "failure");

        assertThat(failure.getMessage(), is("failure"));
        assertThat(failure.scope(), is(Http3ProtocolException.Scope.CONNECTION));
    }

    @Test
    void shouldRejectNullFactoryArguments() {
        IllegalStateException cause = new IllegalStateException();

        assertThrows(NullPointerException.class,
                     () -> Http3ProtocolException.connectionError(null, "failure"));
        assertThrows(NullPointerException.class,
                     () -> Http3ProtocolException.connectionError(Http3ErrorCode.GENERAL_PROTOCOL_ERROR, null));
        assertThrows(NullPointerException.class,
                     () -> Http3ProtocolException.connectionError(null, "failure", cause));
        assertThrows(NullPointerException.class,
                     () -> Http3ProtocolException.connectionError(Http3ErrorCode.GENERAL_PROTOCOL_ERROR, null, cause));
        assertThrows(NullPointerException.class,
                     () -> Http3ProtocolException.connectionError(Http3ErrorCode.GENERAL_PROTOCOL_ERROR, "failure", null));
        assertThrows(NullPointerException.class,
                     () -> Http3ProtocolException.streamError(null, "failure"));
        assertThrows(NullPointerException.class,
                     () -> Http3ProtocolException.streamError(Http3ErrorCode.GENERAL_PROTOCOL_ERROR, null));
        assertThrows(NullPointerException.class,
                     () -> Http3ProtocolException.streamError(null, "failure", cause));
        assertThrows(NullPointerException.class,
                     () -> Http3ProtocolException.streamError(Http3ErrorCode.GENERAL_PROTOCOL_ERROR, null, cause));
        assertThrows(NullPointerException.class,
                     () -> Http3ProtocolException.streamError(Http3ErrorCode.GENERAL_PROTOCOL_ERROR, "failure", null));
    }
}
