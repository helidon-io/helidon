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

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QuicTlsPreSharedKeysTest {
    @Test
    void shouldRejectEmptyPeerIdentityAsDecodeError() {
        QuicTransportException thrown = assertThrows(
                QuicTransportException.class,
                () -> QuicTlsPreSharedKeys.decodeClientHello(offeredPsks(0, 32)));

        assertThat(thrown.errorCode(), is(QuicTransportErrors.CRYPTO_ERROR.from() + 50));
    }

    @Test
    void shouldRejectShortPeerBinderAsDecodeError() {
        QuicTransportException thrown = assertThrows(
                QuicTransportException.class,
                () -> QuicTlsPreSharedKeys.decodeClientHello(offeredPsks(1, 31)));

        assertThat(thrown.errorCode(), is(QuicTransportErrors.CRYPTO_ERROR.from() + 50));
    }

    private static ByteBuffer offeredPsks(int identityLength, int binderLength) {
        int identitiesLength = Short.BYTES + identityLength + Integer.BYTES;
        int bindersLength = Byte.BYTES + binderLength;
        ByteBuffer buffer = ByteBuffer.allocate(Short.BYTES + identitiesLength + Short.BYTES + bindersLength);
        buffer.putShort((short) identitiesLength);
        buffer.putShort((short) identityLength);
        buffer.put(new byte[identityLength]);
        buffer.putInt(0);
        buffer.putShort((short) bindersLength);
        buffer.put((byte) binderLength);
        buffer.put(new byte[binderLength]);
        return buffer.flip();
    }
}
