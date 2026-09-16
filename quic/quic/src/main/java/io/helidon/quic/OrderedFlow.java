/*
 * Copyright (c) 2021, 2026 Oracle and/or its affiliates.
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

import java.util.Comparator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;

import io.helidon.common.Api;
import io.helidon.quic.QuicTLSEngine.KeySpace;
import io.helidon.quic.frame.CryptoFrame;
import io.helidon.quic.frame.QuicFrame;
import io.helidon.quic.frame.StreamFrame;

import static io.helidon.quic.QuicTransportErrors.CRYPTO_BUFFER_EXCEEDED;
import static io.helidon.quic.QuicTransportErrors.INTERNAL_ERROR;

/**
 * A class to take care of frames reordering in an ordered flow.
 *
 * Frames that are {@linkplain #receive(QuicFrame) received} out of order
 * will be either buffered or dropped, depending on their position
 * with respect to the current ordered flow {@linkplain #offset() offset}.
 * The buffered frames are returned by later calls to {@linkplain #poll()}
 * or made available through {@link #takeAvailable()} when the flow offset
 * matches the frame offset.
 *
 * Frames that are {@linkplain #receive(QuicFrame) received} in order
 * are immediately returned.
 *
 * This class is not thread-safe and concurrent access needs to be serialized
 * externally.
 *
 * @param <T> A frame type that defines a length and an offset indicating its
 *           position in the ordered flow
 */
@Api.Internal
public abstract sealed class OrderedFlow<T extends QuicFrame> {

    private final ConcurrentSkipListSet<T> queue;
    private final ToLongFunction<T> position;
    private final ToIntFunction<T> length;
    private final ReassemblyBudget budget;
    private long offset;
    private long buffered;
    private T availableFrame;
    /**
     * Constructs a new instance of ordered flow to reorder frames in a given
     * flow.
     *
     * @param comparator A comparator to order the frames according to their position in
     *                  the ordered flow. Typically, this will compare the
     *                  frame's offset: the frame with the smaller offset will be sorted
     *                  before the frame with the greater offset
     * @param position   A method reference that returns the position of the frame in the
     *                  flow. For instance, this would be {@link CryptoFrame#offset()
     *                  CryptoFrame::offset} if {@code <T>} is {@code CryptoFrame}, or
     *                  {@link StreamFrame#offset() StreamFrame::offset} if {@code <T>}
     *                  is {@code StreamFrame}
     * @param length     A method reference that returns the number of bytes in the frame data.
     *                  This is used to compute the expected position of the next
     *                  frame in the flow. For instance, this would be {@link CryptoFrame#length()
     *                  CryptoFrame::length} if {@code <T>} is {@code CryptoFrame}, or
     *                  {@link StreamFrame#dataLength() StreamFrame::dataLength} if {@code <T>}
     *                  is {@code StreamFrame}
     * @param budget     budget for retained reassembly nodes
     */
    protected OrderedFlow(Comparator<T> comparator,
                          ToLongFunction<T> position,
                          ToIntFunction<T> length,
                          ReassemblyBudget budget) {
        queue = new ConcurrentSkipListSet<>(comparator);
        this.position = position;
        this.length = length;
        this.budget = Objects.requireNonNull(budget, "budget");
    }

    /**
     * Receives a new frame. If the frame is below the current
     * offset the frame is dropped. If it is above the current offset,
     * it is queued.
     * If the frame is exactly at the current offset, it is
     * returned.
     *
     * @param frame a frame that was received
     * @return the next frame in the flow, or an empty optional if it is not available yet
     * @throws NullPointerException if {@code frame} is {@code null}
     * @throws IllegalStateException if a frame prepared for allocation-free retrieval has not been taken
     */
    public Optional<T> receive(T frame) {
        Objects.requireNonNull(frame, "frame");
        requireNoAvailableFrame();
        return Optional.ofNullable(receive(frame, 0));
    }

    /**
     * Receives a new frame and prepares the first contiguous frame for allocation-free retrieval.
     *
     * @param frame frame that was received
     * @return {@code true} if a contiguous frame is available
     * @throws NullPointerException if {@code frame} is {@code null}
     * @throws IllegalStateException if a previously prepared frame has not been taken
     */
    public boolean receiveAvailable(T frame) {
        Objects.requireNonNull(frame, "frame");
        requireNoAvailableFrame();
        availableFrame = receive(frame, 0);
        return availableFrame != null;
    }

    /**
     * Whether a contiguous frame is available for retrieval.
     *
     * @return {@code true} if a contiguous frame is available
     */
    public boolean hasAvailable() {
        return availableFrame != null;
    }

    /**
     * Returns and removes the current contiguous frame.
     *
     * @return current contiguous frame
     * @throws NoSuchElementException if no contiguous frame is available
     */
    public T takeAvailable() {
        T frame = availableFrame;
        if (frame == null) {
            throw new NoSuchElementException("No contiguous frame is available");
        }
        availableFrame = null;
        return frame;
    }

    /**
     * Prepares the next contiguous buffered frame for allocation-free retrieval.
     *
     * @return {@code true} if another contiguous frame is available
     * @throws IllegalStateException if a previously prepared frame has not been taken
     */
    public boolean pollAvailable() {
        requireNoAvailableFrame();
        availableFrame = poll(offset);
        return availableFrame != null;
    }

    /**
     * Removes and return the head of the queue if it is at the
     * current offset. Otherwise, returns an empty optional.
     *
     * @return the head of the queue if it is at the current offset,
     *        or an empty optional
     * @throws IllegalStateException if a frame prepared for allocation-free retrieval has not been taken
     */
    public Optional<T> poll() {
        requireNoAvailableFrame();
        return Optional.ofNullable(poll(offset));
    }

    /**
     * Returns the number of buffered frames.
     *
     * @return the number of buffered frames.
     */
    public int size() {
        return queue.size();
    }

    /**
     * Returns the number of bytes buffered.
     *
     * @return the number of bytes buffered.
     */
    public long buffered() {
        return buffered;
    }

    /**
     * Returns true if there are no buffered frames.
     *
     * @return true if there are no buffered frames.
     */
    public boolean isEmpty() {
        return queue.isEmpty();
    }

    /**
     * Returns the current offset of this buffer.
     *
     * @return the current offset of this buffer.
     */
    public long offset() {
        return offset;
    }

    /**
     * Drops all buffered frames.
     */
    public void clear() {
        int size = queue.size();
        queue.clear();
        availableFrame = null;
        buffered = 0;
        budget.release(size);
    }

    /**
     * Returns a slice of the given frame.
     *
     * @return a slice of the given frame.
     *
     * @param frame  the frame to slice
     * @param offset the new frame offset
     * @param length the new frame length
     * @throws IndexOutOfBoundsException if the new offset or length
     *                                  fall outside of the frame's bounds
     */
    protected abstract T slice(T frame, long offset, int length);

    /**
     * Creates the transport exception to throw when the reassembly budget is exhausted.
     *
     * @param frame frame which required another retained node
     * @return transport exception to throw
     */
    protected abstract QuicTransportException budgetExceeded(T frame);

    private T receive(T frame, int retainedPermits) {
        long start = this.position.applyAsLong(frame);
        int length = this.length.applyAsInt(frame);
        long end = start + length;
        long offset = this.offset;
        if (end <= offset || length == 0) {
            // late arrival or empty frame. Just drop it; No overlap
            // if we reach here!
            budget.release(retainedPermits);
            return null;
        } else if (start > offset) {
            if (retainedPermits != 0) {
                budget.release(retainedPermits);
                throw new AssertionError("Retained frame is not ready for delivery");
            }
            // the frame is after the offset.
            // insert or slice it, depending on what we
            // have already received.
            enqueue(frame, start, length);
            return null;
        } else {
            // case where the frame is either at offset, or is below
            // offset but has a length that provides bytes that
            // overlap with the current offset. In the later case
            // we will return a slice.
            int todeliver = (int) (end - offset);
            T delivery;
            try {
                delivery = start == offset ? frame : slice(frame, offset, todeliver);
            } catch (RuntimeException | Error failure) {
                budget.release(retainedPermits);
                throw failure;
            }

            // update the offset with the new position
            this.offset = end;
            // cleanup the queue
            int released;
            try {
                released = retainedPermits + dropuntil(end);
            } catch (RuntimeException | Error failure) {
                budget.release(retainedPermits);
                throw failure;
            }
            budget.release(released);
            return delivery;
        }
    }

    private void requireNoAvailableFrame() {
        if (availableFrame != null) {
            throw new IllegalStateException("Contiguous frame has not been retrieved");
        }
    }

    private T peekFirst() {
        if (queue.isEmpty()) {
            return null;
        }
        // why is there no peekFirst?
        try {
            return queue.first();
        } catch (NoSuchElementException nse) {
            return null;
        }
    }

    private void enqueue(T frame, long pos, int length) {
        long newpos = pos;
        int newlen = length;
        long limit = Math.addExact(pos, length);
        int removed = 0;

        // look at the closest frame, if any, whose offset is <= to
        // the new frame offset. Try to see if the new frame overlaps
        // with that frame, and if so, drops the part that overlaps
        // in the new frame.
        T floor = queue.floor(frame);
        if (floor != null) {
            long foffset = position.applyAsLong(floor);
            long flen = this.length.applyAsInt(floor);
            if (limit <= foffset + flen) {
                // bytes already all buffered!
                // just drop the frame
                return;
            }
            // foffset == pos case handled as ceiling below
            if (foffset < pos && pos - foffset < flen) {
                // reduce the frame if it overlaps with the
                // one that sits just before in the queue
                newpos = foffset + flen;
                newlen = length - (int) (newpos - pos);
            }
        }

        // Look at the frames that have an offset higher or equal to
        // the new frame offset, and see if any overlap with the new
        // frame. Remove frames that are entirely contained in the new one,
        // slice the current frame if the frames overlap.
        while (true) {
            T ceil = queue.ceiling(frame);
            if (ceil != null) {
                long coffset = position.applyAsLong(ceil);
                if (coffset < limit) {
                    long clen = this.length.applyAsInt(ceil);
                    if (clen <= limit - coffset) {
                        // ceiling frame completely contained in the new frame:
                        // remove the ceiling frame
                        if (queue.remove(ceil)) {
                            buffered -= clen;
                            removed++;
                        } else {
                            throw new AssertionError("Concurrent modification");
                        }
                        continue;
                    }
                    // safe cast, since newlen <= len
                    newlen = (int) (coffset - newpos);
                }
            }
            break;
        }

        if (newlen <= 0) {
            budget.release(removed);
            return;
        }

        boolean acquired = false;
        if (removed == 0) {
            if (!budget.tryAcquire()) {
                throw budgetExceeded(frame);
            }
            acquired = true;
        }

        boolean added;
        try {
            T toAdd = newlen == length ? frame : slice(frame, newpos, newlen);
            added = queue.add(toAdd);
        } catch (RuntimeException | Error failure) {
            if (acquired) {
                budget.release();
            }
            budget.release(removed);
            throw failure;
        }

        if (!added) {
            if (acquired) {
                budget.release();
            }
            budget.release(removed);
            return;
        }

        buffered += newlen;
        // One permit from a contained node is transferred to the replacement.
        budget.release(Math.max(0, removed - 1));
    }

    /**
     * Drop all frames in the buffer whose position is strictly
     * below offset.
     *
     * @param offset the offset below which frames should be dropped
     * @return the number of retained frame nodes removed
     */
    private int dropuntil(long offset) {
        T head;
        long pos;
        int removed = 0;
        try {
            do {
                head = peekFirst();
                if (head == null) {
                    break;
                }
                pos = position.applyAsLong(head);
                if (pos < offset) {
                    var length = this.length.applyAsInt(head);
                    var consumed = offset - pos;
                    if (length <= consumed) {
                        // drop it
                        if (head == queue.pollFirst()) {
                            buffered -= length;
                            removed++;
                        } else {
                            throw new AssertionError("Concurrent modification");
                        }
                    } else {
                        // safe cast: consumed < length if we reach here
                        int newlen = length - (int) consumed;
                        var newhead = slice(head, offset, newlen);
                        if (head == queue.pollFirst()) {
                            boolean added;
                            try {
                                added = queue.add(newhead);
                            } catch (RuntimeException | Error failure) {
                                buffered -= length;
                                removed++;
                                throw failure;
                            }
                            if (!added) {
                                buffered -= length;
                                removed++;
                                throw new AssertionError("Failed to replace sliced frame");
                            }
                            buffered -= consumed;
                        } else {
                            throw new AssertionError("Concurrent modification");
                        }
                    }
                }
            } while (pos < offset);
            return removed;
        } catch (RuntimeException | Error failure) {
            budget.release(removed);
            throw failure;
        }
    }

    /**
     * Pretends to {@linkplain #receive(QuicFrame) receive} the head of the queue,
     * if it is at the provided offset.
     *
     * @param offset the minimal offset
     * @return a received frame at the current flow offset, or {@code null}
     */
    private T poll(long offset) {
        budget.release(dropuntil(offset));
        T head = peekFirst();
        if (head != null) {
            long pos = position.applyAsLong(head);
            if (pos == offset) {
                // the frame we wanted was in the queue!
                //   well, let's handle it...
                if (head == queue.pollFirst()) {
                    long length = this.length.applyAsInt(head);
                    buffered -= length;
                } else {
                    throw new AssertionError("Concurrent modification");
                }
                return receive(head, 1);
            }
        }
        return null;
    }

    /**
     * Budget for nodes retained while reassembling an ordered QUIC flow.
     *
     * <p>One permit represents one frame node currently retained by an
     * {@link OrderedFlow}. Implementations can combine a flow-local limit with
     * a connection-wide limit. Acquisition and release methods must be safe to
     * invoke concurrently when budgets are shared by multiple flow owners.
     */
    @Api.Internal
    public interface ReassemblyBudget {
        /**
         * Attempts to acquire one retained-node permit.
         *
         * @return {@code true} if the permit was acquired
         */
        boolean tryAcquire();

        /**
         * Releases one retained-node permit.
         */
        default void release() {
            release(1);
        }

        /**
         * Releases retained-node permits.
         *
         * @param count non-negative number of permits to release; zero is a no-op
         */
        void release(int count);
    }

    /**
     * A subclass of {@link OrderedFlow} used to reorder instances of
     * {@link CryptoFrame}.
     */
    public static final class CryptoDataFlow extends OrderedFlow<CryptoFrame> {
        private final KeySpace keySpace;

        CryptoDataFlow(ReassemblyBudget budget, KeySpace keySpace) {
            super(CryptoFrame::compareOffsets,
                  CryptoFrame::offset,
                  CryptoFrame::length,
                  budget);
            this.keySpace = Objects.requireNonNull(keySpace, "keySpace");
        }

        /**
         * Creates a budgeted crypto-data flow.
         *
         * @param budget  reassembly-node budget
         * @param keySpace TLS key space containing the CRYPTO frames
         * @return new crypto-data flow
         */
        public static CryptoDataFlow create(ReassemblyBudget budget, KeySpace keySpace) {
            return new CryptoDataFlow(budget, keySpace);
        }

        @Override
        protected CryptoFrame slice(CryptoFrame frame, long offset, int length) {
            if (length == 0) {
                return null;
            }
            return frame.slice(offset, length);
        }

        @Override
        protected QuicTransportException budgetExceeded(CryptoFrame frame) {
            return new QuicTransportException("Crypto reassembly fragment limit exceeded",
                                              keySpace,
                                              frame.typeField(),
                                              CRYPTO_BUFFER_EXCEEDED);
        }
    }

    /**
     * A subclass of {@link OrderedFlow} used to reorder instances of
     * {@link StreamFrame}.
     */
    public static final class StreamDataFlow extends OrderedFlow<StreamFrame> {
        private final long streamId;

        StreamDataFlow(ReassemblyBudget budget, long streamId) {
            super(StreamFrame::compareOffsets,
                  StreamFrame::offset,
                  StreamFrame::dataLength,
                  budget);
            this.streamId = streamId;
        }

        /**
         * Creates a budgeted stream-data flow.
         *
         * @param budget   reassembly-node budget
         * @param streamId QUIC stream identifier
         * @return new stream-data flow
         */
        public static StreamDataFlow create(ReassemblyBudget budget, long streamId) {
            return new StreamDataFlow(budget, streamId);
        }

        @Override
        protected StreamFrame slice(StreamFrame frame, long offset, int length) {
            if (length == 0) {
                return null;
            }
            return frame.slice(offset, length);
        }

        @Override
        protected QuicTransportException budgetExceeded(StreamFrame frame) {
            return new QuicTransportException("Excessive stream fragmentation",
                                              KeySpace.ONE_RTT,
                                              frame.typeField(),
                                              INTERNAL_ERROR,
                                              streamId);
        }
    }
}
