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

import java.nio.ByteBuffer;

import io.helidon.common.buffers.BufferData;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QuicConnectionIdTest {
    @Test
    void shouldCopyConnectionIdBytesFromSourceArray() {
        byte[] bytes = new byte[] {0x01, 0x02, 0x03};
        PeerConnectionId connectionId = new PeerConnectionId(bytes);

        bytes[0] = 0x7f;

        assertThat(connectionId.length(), is(3));
        assertThat(connectionId.bytes(), is(new byte[] {0x01, 0x02, 0x03}));
        assertThat(connectionId.toHexString(), is("010203"));
    }

    @Test
    void shouldCopyConnectionIdAndStatelessResetToken() {
        byte[] token = new byte[16];
        token[0] = 0x33;
        PeerConnectionId connectionId = PeerConnectionId.create(BufferData.create(new byte[] {0x0a, 0x0b}), token);

        token[0] = 0x44;
        byte[] copiedToken = connectionId.statelessResetToken().orElseThrow();
        copiedToken[1] = 0x55;

        assertThat(connectionId.bytes(), is(new byte[] {0x0a, 0x0b}));
        assertThat(connectionId.statelessResetToken().orElseThrow()[0], is((byte) 0x33));
        assertThat(connectionId.statelessResetToken().orElseThrow()[1], is((byte) 0x00));
    }

    @Test
    void shouldCreateBufferConnectionIdsWithoutStatelessResetToken() {
        PeerConnectionId bufferDataId = PeerConnectionId.create(BufferData.create(new byte[] {0x01, 0x02}));
        PeerConnectionId byteBufferId = PeerConnectionId.create(ByteBuffer.wrap(new byte[] {0x03, 0x04}));

        assertThat(bufferDataId.bytes(), is(new byte[] {0x01, 0x02}));
        assertThat(bufferDataId.statelessResetToken().isEmpty(), is(true));
        assertThat(byteBufferId.bytes(), is(new byte[] {0x03, 0x04}));
        assertThat(byteBufferId.statelessResetToken().isEmpty(), is(true));
    }

    @Test
    void shouldRejectNullStatelessResetToken() {
        assertThrows(NullPointerException.class,
                     () -> PeerConnectionId.create(BufferData.create(new byte[] {0x01}), null));
        assertThrows(NullPointerException.class,
                     () -> PeerConnectionId.create(ByteBuffer.wrap(new byte[] {0x01}), null));
    }

    @Test
    void shouldCompareAndMatchConnectionIdsByBytes() {
        PeerConnectionId first = new PeerConnectionId(new byte[] {0x01, 0x02});
        PeerConnectionId same = new PeerConnectionId(new byte[] {0x01, 0x02});
        PeerConnectionId different = new PeerConnectionId(new byte[] {0x01, 0x03});

        assertThat(first, is(same));
        assertThat(first.hashCode(), is(same.hashCode()));
        assertThat(first.compareTo(different), is(not(0)));
        assertThat(first.matches(BufferData.create(new byte[] {0x01, 0x02})), is(true));
        assertThat(first.compareBytes(BufferData.create(new byte[] {0x01, 0x03})) < 0, is(true));
        assertThat(first.bufferData().readBytes(), is(new byte[] {0x01, 0x02}));
    }

    @Test
    void shouldRejectInvalidStatelessResetTokenLength() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                                                   () -> PeerConnectionId.create(BufferData.create(new byte[] {0x01}),
                                                                                 new byte[] {0x01}));

        assertThat(ex.getMessage(), is("Invalid stateless reset token length 1"));
    }

    @Test
    void shouldValidateAndResetLocallyGeneratedConnectionIdFromByteBuffer() {
        QuicConnectionIdFactory factory = QuicConnectionIdFactory.server();
        QuicConnectionId connectionId = factory.newConnectionId();

        QuicConnectionId validated = factory.unsafeConnectionIdFor(ByteBuffer.wrap(connectionId.bytes()));
        ByteBuffer statelessReset = factory.statelessReset(ByteBuffer.wrap(connectionId.bytes()), 43);

        assertThat(validated, is(connectionId));
        assertThat(statelessReset, is(notNullValue()));
        assertThat(statelessReset.remaining(), is(43));
    }
}
