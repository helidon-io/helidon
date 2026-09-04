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

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;

import static io.helidon.quic.frame.QuicFrame.MAX_VL_INTEGER;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QuicCloseCommandTest {
    @Test
    void throwableDetailIsLocalByDefault() {
        IllegalStateException failure = new IllegalStateException("local secret");

        QuicCloseCommand command = QuicCloseCommand.transport(failure);

        assertThat(command.cause().orElseThrow(), sameInstance(failure));
        assertThat(command.logMessage(), containsString("local secret"));
        assertThat(command.peerDetail(), is(Optional.empty()));
    }

    @Test
    void connectionFailureRemainsTheStableTerminationCause() {
        QuicConnectionException failure = new QuicConnectionException("connection failed");

        QuicTermination termination = QuicTermination.local(QuicCloseCommand.transport(failure), null);

        assertThat(termination.closeCause(), sameInstance(failure));
    }

    @Test
    void peerDetailIsSanitizedWithoutMutatingOriginalCommand() {
        QuicCloseCommand original = QuicCloseCommand.application(0x100);

        QuicCloseCommand disclosed = original.withPeerDetail("a\r\n\t\u0000b\u2028c");

        assertThat(original.peerDetail(), is(Optional.empty()));
        assertThat(disclosed.peerDetail().orElseThrow(), is("a    b c"));
    }

    @Test
    void peerDetailIsBoundedOnUnicodeBoundary() {
        String prefix = "€".repeat(85) + "x";

        String detail = QuicCloseCommand.application(0x100)
                .withPeerDetail(prefix + "😀")
                .peerDetail()
                .orElseThrow();

        assertThat(detail, is(prefix));
        assertThat(detail.getBytes(StandardCharsets.UTF_8).length, is(256));
    }

    @Test
    void streamContextIsValidatedAndDoesNotMutateOriginalCommand() {
        QuicCloseCommand original = QuicCloseCommand.application(0x100);

        QuicCloseCommand associated = original.withStream(17);

        assertThat(original.streamId().isEmpty(), is(true));
        assertThat(associated.streamId().orElseThrow(), is(17L));
        assertThrows(IllegalArgumentException.class, () -> original.withStream(-1));
        assertThrows(IllegalArgumentException.class, () -> original.withStream(MAX_VL_INTEGER + 1));
    }

    @Test
    void serverHandshakeTimeoutUsesValidatedHandshakeDeliveryAndRetainsLocalCause() {
        TimeoutException timeout = new TimeoutException("handshake timed out");

        QuicCloseCommand command = QuicCloseCommand.serverHandshakeTimeout(timeout, timeout.getMessage());
        QuicCloseCommand disclosed = command.withPeerDetail("safe").withStream(17);
        QuicCloseCommand fallback = command.silentFallback();

        assertThat(command.kind(), is(QuicCloseCommand.Kind.CONNECTION_CLOSE));
        assertThat(command.layer(), is(QuicCloseCommand.Layer.TRANSPORT));
        assertThat(command.errorCode().orElseThrow(), is(QuicTransportErrors.NO_ERROR.code()));
        assertThat(command.frameType().isEmpty(), is(true));
        assertThat(command.keySpace(), is(Optional.empty()));
        assertThat(command.cause().orElseThrow(), sameInstance(timeout));
        assertThat(command.peerDetail(), is(Optional.empty()));
        assertThat(command.delivery(), is(QuicCloseCommand.Delivery.VALIDATED_HANDSHAKE_LEVELS));
        assertThat(disclosed.delivery(), is(QuicCloseCommand.Delivery.VALIDATED_HANDSHAKE_LEVELS));
        assertThat(fallback.kind(), is(QuicCloseCommand.Kind.SILENT));
        assertThat(fallback.cause().orElseThrow(), sameInstance(timeout));
        assertThat(fallback.logMessage(), is(timeout.getMessage()));
    }
}
