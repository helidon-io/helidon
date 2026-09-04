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

package io.helidon.quic.frame;

import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;

import io.helidon.common.buffers.BufferData;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StreamFrameTest {

    @Test
    void publicDecodeBorrowsPayloadUntilCanonicalized() throws Exception {
        byte[] encoded = encodedFrame();
        StreamFrame borrowed = (StreamFrame) QuicFrame.decode(ByteBuffer.wrap(encoded));

        StreamFrame owned = borrowed.withOwnedPayload();
        encoded[3] = 9;

        assertThat(owned, not(sameInstance(borrowed)));
        assertThat(bytes(borrowed), equalTo(new byte[] {9, 2, 3}));
        assertThat(bytes(owned), equalTo(new byte[] {1, 2, 3}));
        assertThat(owned.withOwnedPayload(), sameInstance(owned));
    }

    @Test
    void transportDecodeAndSlicesRetainOwnership() throws Exception {
        byte[] encoded = encodedFrame();
        encoded[0] |= 1;
        StreamFrame frame = (StreamFrame) QuicFrame.decodeOwned(ByteBuffer.wrap(encoded));
        StreamFrame slice = frame.slice(1, 2);
        StreamFrame prefix = frame.slice(0, 2);

        assertThat(frame.withOwnedPayload(), sameInstance(frame));
        assertThat(slice.withOwnedPayload(), sameInstance(slice));
        assertThat(bytes(slice), equalTo(new byte[] {2, 3}));
        assertThat(slice.isLast(), equalTo(true));
        assertThat(prefix.isLast(), equalTo(false));
    }

    @Test
    void factoriesStateBorrowedAndOwnedPayloadContracts() {
        byte[] bytes = {1, 2, 3};
        StreamFrame borrowed = StreamFrame.create(0, 0, bytes.length, false, ByteBuffer.wrap(bytes));
        StreamFrame owned = StreamFrame.createOwned(0, 0, bytes.length, false, ByteBuffer.wrap(bytes.clone()));
        StreamFrame direct = StreamFrame.create(0, 0, 1, false, ByteBuffer.allocateDirect(1).put((byte) 1).flip());
        StreamFrame borrowedSlice = borrowed.slice(1, 2);
        StreamFrame ownedSlice = owned.slice(1, 2);

        assertThat(borrowed.withOwnedPayload(), not(sameInstance(borrowed)));
        assertThat(owned.withOwnedPayload(), sameInstance(owned));
        assertThat(direct.withOwnedPayload(), not(sameInstance(direct)));
        assertThat(borrowedSlice.withOwnedPayload(), not(sameInstance(borrowedSlice)));
        assertThat(ownedSlice.withOwnedPayload(), sameInstance(ownedSlice));
    }

    @Test
    void payloadViewIsReadOnly() {
        StreamFrame frame = StreamFrame.createOwned(0, 0, 1, false, ByteBuffer.wrap(new byte[] {1}));

        assertThrows(ReadOnlyBufferException.class, () -> frame.payload().put(0, (byte) 2));
    }

    @Test
    void canonicalizationPreservesExplicitZeroOffsetField() throws Exception {
        byte[] encoded = {0x0e, 0, 0, 3, 1, 2, 3};
        StreamFrame frame = ((StreamFrame) QuicFrame.decode(ByteBuffer.wrap(encoded))).withOwnedPayload();
        ByteBuffer output = ByteBuffer.allocate(frame.size());

        frame.encode(output);

        assertThat(frame.typeField(), equalTo(0x0eL));
        assertThat(output.array(), equalTo(encoded));
    }

    @Test
    void ownedHeapPayloadViewsPreserveRangeAndIndependentCursors() {
        byte[] storage = {9, 9, 1, 2, 3, 4, 9};
        ByteBuffer range = ByteBuffer.wrap(storage, 2, 4);
        StreamFrame frame = StreamFrame.createOwned(0, 0, 4, false, range).slice(1, 2);
        BufferData first = frame.ownedPayloadData();
        BufferData second = frame.ownedPayloadData();

        assertThat(first.read(), equalTo(2));
        assertThat(first.available(), equalTo(1));
        assertThat(second.readBytes(), equalTo(new byte[] {2, 3}));
        assertThat(first.readBytes(), equalTo(new byte[] {3}));
        assertThrows(UnsupportedOperationException.class, () -> frame.ownedPayloadData().write(4));
    }

    @Test
    void ownedDirectPayloadUsesReadOnlyFallback() {
        ByteBuffer source = ByteBuffer.allocateDirect(3).put(new byte[] {1, 2, 3}).flip();
        StreamFrame frame = StreamFrame.createOwned(0, 0, 3, false, source);
        BufferData payload = frame.ownedPayloadData();

        assertThat(payload.readBytes(), equalTo(new byte[] {1, 2, 3}));
        assertThrows(UnsupportedOperationException.class, () -> frame.ownedPayloadData().clear());
    }

    @Test
    void ownedReadOnlyAndEmptyPayloadsUseSafeViews() {
        StreamFrame readOnly = StreamFrame.createOwned(0,
                                                       0,
                                                       2,
                                                       false,
                                                       ByteBuffer.wrap(new byte[] {1, 2}).asReadOnlyBuffer());
        StreamFrame empty = StreamFrame.createOwned(0, 0, 0, false, ByteBuffer.allocate(0));

        assertThat(readOnly.ownedPayloadData().readBytes(), equalTo(new byte[] {1, 2}));
        assertThat(empty.ownedPayloadData().available(), equalTo(0));
        assertThat(empty.ownedPayloadData().readBytes(), equalTo(new byte[0]));
    }

    @Test
    void borrowedPayloadCannotTransferToBufferData() {
        StreamFrame frame = StreamFrame.create(0, 0, 1, false, ByteBuffer.wrap(new byte[] {1}));

        assertThrows(IllegalStateException.class, frame::ownedPayloadData);
    }

    private static byte[] encodedFrame() {
        return new byte[] {0x0a, 0, 3, 1, 2, 3};
    }

    private static byte[] bytes(StreamFrame frame) {
        ByteBuffer payload = frame.payload();
        byte[] bytes = new byte[payload.remaining()];
        payload.get(bytes);
        return bytes;
    }
}
