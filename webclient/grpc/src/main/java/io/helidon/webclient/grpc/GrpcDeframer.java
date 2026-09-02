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

package io.helidon.webclient.grpc;

import io.helidon.common.buffers.BufferData;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;

import static io.helidon.webclient.grpc.GrpcBaseClientCall.DATA_PREFIX_LENGTH;

final class GrpcDeframer {
    private final int initialBufferSize;
    private final int maxInboundMessageSize;
    private final BufferData header = BufferData.create(DATA_PREFIX_LENGTH);
    private BufferData frame;
    private int bytesLeft;
    private boolean remainder;

    GrpcDeframer(int initialBufferSize, int maxInboundMessageSize) {
        this.initialBufferSize = initialBufferSize;
        this.maxInboundMessageSize = maxInboundMessageSize;
    }

    BufferData deframe(BufferData data) {
        remainder = false;
        if (frame != null) {
            return appendData(data);
        }

        if (header.available() > 0 || data.available() < DATA_PREFIX_LENGTH) {
            int headerBytes = Math.min(DATA_PREFIX_LENGTH - header.available(), data.available());
            header.write(data, headerBytes);
            if (header.available() < DATA_PREFIX_LENGTH) {
                return null;
            }

            int messageLength = messageLength(header, maxInboundMessageSize);
            frame = newFrame(messageLength);
            frame.write(header);
            header.clear();
            bytesLeft = messageLength;
            return appendData(data);
        }

        int messageLength = messageLength(data, maxInboundMessageSize);
        int frameLength = DATA_PREFIX_LENGTH + messageLength;
        if (frameLength == data.available()) {
            return data;
        }
        if (frameLength < data.available()) {
            BufferData result = newFrame(messageLength);
            result.write(data, frameLength);
            remainder = true;
            return result;
        }

        frame = newFrame(messageLength);
        bytesLeft = messageLength - (data.available() - DATA_PREFIX_LENGTH);
        frame.write(data);
        return null;
    }

    boolean hasPartialFrame() {
        return header.available() > 0 || frame != null;
    }

    boolean hasRemainder() {
        return remainder;
    }

    void endOfStream() {
        endOfStream(Status.INTERNAL
                            .withDescription("Incomplete gRPC message data")
                            .asRuntimeException());
    }

    void endOfStream(StatusRuntimeException failure) {
        if (hasPartialFrame()) {
            header.clear();
            frame = null;
            bytesLeft = 0;
            remainder = false;
            throw failure;
        }
    }

    private static int messageLength(BufferData data, int maxInboundMessageSize) {
        long length = ((long) data.get(1) & 0xFF) << 24
                | ((long) data.get(2) & 0xFF) << 16
                | ((long) data.get(3) & 0xFF) << 8
                | ((long) data.get(4) & 0xFF);
        if (length > maxInboundMessageSize) {
            throw Status.RESOURCE_EXHAUSTED
                    .withDescription("gRPC message exceeds maximum size " + maxInboundMessageSize + ": " + length)
                    .asRuntimeException();
        }
        if (length > Integer.MAX_VALUE - DATA_PREFIX_LENGTH) {
            throw new IllegalStateException("gRPC message is too large");
        }
        return (int) length;
    }

    private BufferData appendData(BufferData data) {
        int length = Math.min(bytesLeft, data.available());
        frame.write(data, length);
        bytesLeft -= length;
        if (bytesLeft > 0) {
            return null;
        }

        BufferData result = frame;
        frame = null;
        remainder = data.available() > 0;
        return result;
    }

    private BufferData newFrame(int messageLength) {
        int frameLength = DATA_PREFIX_LENGTH + messageLength;
        return BufferData.growing(Math.max(DATA_PREFIX_LENGTH, Math.min(initialBufferSize, frameLength)));
    }
}
