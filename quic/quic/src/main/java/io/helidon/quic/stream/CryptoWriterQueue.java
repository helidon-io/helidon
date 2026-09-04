/*
 * Copyright (c) 2022, 2026 Oracle and/or its affiliates.
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

package io.helidon.quic.stream;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.locks.ReentrantLock;

import io.helidon.common.Api;
import io.helidon.quic.VariableLengthEncoder;
import io.helidon.quic.frame.CryptoFrame;

/**
 * Class that buffers crypto data received from QuicTLSEngine.
 * Generates CryptoFrames of requested size.
 *
 * Normally the frames are produced sequentially. However, when the client
 * receives a Retry packet or a Version Negotiation packet, the client hello
 * needs to be replayed. In that case we need to keep the processed data
 * in the queues.
 */
@Api.Internal
public class CryptoWriterQueue {
    private final Queue<ByteBuffer> queue = new ArrayDeque<>();
    private final ReentrantLock stateLock = new ReentrantLock();
    private long position = 0;
    // amount of bytes remaining across all the enqueued buffers
    private int totalRemaining = 0;
    private boolean keepReplayData;

    /**
     * Create an empty crypto writer queue.
     */
    private CryptoWriterQueue() {
    }

    /**
     * Create an empty crypto writer queue.
     *
     * @return new crypto writer queue
     */
    public static CryptoWriterQueue create() {
        return new CryptoWriterQueue();
    }

    /**
     * Notify the writer to start keeping processed data. Can only be called on a fresh writer.
     *
     * @throws IllegalStateException if some data was processed already
     */
    public void keepReplayData() {
        stateLock.lock();
        try {
            if (position > 0) {
                throw new IllegalStateException("Some data was processed already");
            }
            keepReplayData = true;
        } finally {
            stateLock.unlock();
        }
    }

    /**
     * Notify the writer to stop keeping processed data.
     */
    public void discardReplayData() {
        stateLock.lock();
        try {
            if (!keepReplayData) {
                return;
            }
            keepReplayData = false;
            for (Iterator<ByteBuffer> iterator = queue.iterator(); iterator.hasNext();) {
                ByteBuffer next = iterator.next();
                if (next.remaining() == 0) {
                    iterator.remove();
                } else {
                    return;
                }
            }
        } finally {
            stateLock.unlock();
        }
    }

    /**
     * Rewinds the enqueued buffer positions to allow for replaying the data.
     *
     * @throws IllegalStateException if replay data is not available
     */
    public void replayData() {
        stateLock.lock();
        try {
            if (!keepReplayData) {
                throw new IllegalStateException("Replay data not available");
            }
            if (position == 0) {
                return;
            }
            int rewound = 0;
            for (Iterator<ByteBuffer> iterator = queue.iterator(); iterator.hasNext();) {
                ByteBuffer next = iterator.next();
                if (next.position() != 0) {
                    rewound += next.position();
                    next.position(0);
                } else {
                    break;
                }
            }
            position = 0;
            totalRemaining += rewound;
        } finally {
            stateLock.unlock();
        }
    }

    /**
     * Clears the queue and resets position back to zero.
     */
    public void reset() {
        stateLock.lock();
        try {
            position = 0;
            totalRemaining = 0;
            queue.clear();
        } finally {
            stateLock.unlock();
        }
    }

    /**
     * Enqueues the provided crypto data.
     *
     * @param buffer data to enqueue
     */
    public void enqueue(ByteBuffer buffer) {
        Objects.requireNonNull(buffer, "buffer");
        stateLock.lock();
        try {
            queue.add(buffer.slice());
            totalRemaining += buffer.remaining();
        } finally {
            stateLock.unlock();
        }
    }

    /**
     * Stores the next portion of queued crypto data in a frame.
     * Returns an empty optional if there's no data to enqueue or if
     * {@code maxSize} is too small to fit at least one byte of data.
     * The produced frame may be shorter than maxSize even if there are
     * remaining bytes.
     *
     * @param maxSize maximum size of the returned frame, in bytes
     * @return frame with next portion of crypto data, or an empty optional
     * @throws IllegalArgumentException if {@code maxSize < 0}
     */
    public Optional<CryptoFrame> produceFrame(int maxSize) {
        stateLock.lock();
        try {
            if (maxSize < 0) {
                throw new IllegalArgumentException("negative maxSize");
            }
            if (totalRemaining == 0) {
                return Optional.empty();
            }
            int posLength = VariableLengthEncoder.encodedSize(position);
            // 1 (type) + posLength (position) + 1 (length) + 1 (payload)
            if (maxSize < 3 + posLength) {
                return Optional.empty();
            }
            int maxPayloadPlusLen = maxSize - 1 - posLength;
            int maxPayload;
            if (maxPayloadPlusLen <= 64) { //63 bytes + 1 byte for length
                maxPayload = maxPayloadPlusLen - 1;
            } else if (maxPayloadPlusLen <= 16385) { // 16383 bytes + 2 bytes for length
                maxPayload = maxPayloadPlusLen - 2;
            } else { // 4 bytes for length
                maxPayload = maxPayloadPlusLen - 4;
            }
            // the frame length that we decide upon
            int computedFrameLength = Math.min(maxPayload, totalRemaining);
            ByteBuffer frameData = null;
            for (Iterator<ByteBuffer> iterator = queue.iterator(); iterator.hasNext();) {
                ByteBuffer buffer = iterator.next();
                // amount of remaining bytes in the current bytebuffer being processed
                int numRemainingInBuffer = buffer.remaining();
                if (numRemainingInBuffer == 0) {
                    if (!keepReplayData) {
                        iterator.remove();
                    }
                    continue;
                }
                if (frameData == null) {
                    frameData = ByteBuffer.allocate(computedFrameLength);
                }
                if (frameData.remaining() >= numRemainingInBuffer) {
                    // frame data can accommodate the entire buffered data, so copy it over
                    frameData.put(buffer);
                    if (!keepReplayData) {
                        iterator.remove();
                    }
                } else {
                    // target frameData buffer cannot accommodate the entire buffered data,
                    // so we copy over only that much that the target buffer can accommodate

                    // amount of data available in the target buffer
                    int spaceAvail = frameData.remaining();
                    // copy over the buffered data into the target frameData buffer
                    frameData.put(frameData.position(), buffer, buffer.position(), spaceAvail);
                    // manually move the position of the target buffer to account for the copied data
                    frameData.position(frameData.position() + spaceAvail);
                    // manually move the position of the (input) buffered data to account for
                    // data that we just copied
                    buffer.position(buffer.position() + spaceAvail);
                    // target frameData buffer is fully populated, no more processing of available
                    // input buffer necessary in this round
                    break;
                }
            }
            frameData.flip();
            long oldPosition = position;
            position += computedFrameLength;
            totalRemaining -= computedFrameLength;
            return Optional.of(CryptoFrame.create(oldPosition, computedFrameLength, frameData));
        } finally {
            stateLock.unlock();
        }
    }

    /**
     * Returns the current number of buffered bytes.
     *
     * @return current number of buffered bytes
     */
    public int remaining() {
        stateLock.lock();
        try {
            return totalRemaining;
        } finally {
            stateLock.unlock();
        }
    }
}
