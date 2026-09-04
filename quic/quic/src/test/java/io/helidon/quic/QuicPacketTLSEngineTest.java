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

import io.helidon.quic.spi.QuicPacketTLSEngine;

import org.junit.jupiter.api.Test;

import static io.helidon.quic.QuicTLSEngine.KeySpace.INITIAL;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QuicPacketTLSEngineTest {
    @Test
    void defaultPackedHeaderMaskAdaptsLegacyImplementations() throws Exception {
        QuicPacketTLSEngine engine = mock(QuicPacketTLSEngine.class, CALLS_REAL_METHODS);
        ByteBuffer legacyMask = ByteBuffer.wrap(new byte[] {0x11, 0x22, 0x33, 0x44, 0x55, 0x66});
        when(engine.computeHeaderProtectionMaskBuffer(eq(INITIAL), eq(true), any(ByteBuffer.class)))
                .thenReturn(legacyMask);

        long packed = engine.computeHeaderProtectionMaskBits(INITIAL, true, ByteBuffer.allocate(16));

        assertThat(packed, is(0x1122_3344_55L));
        assertThat(legacyMask.position(), is(5));
    }
}
