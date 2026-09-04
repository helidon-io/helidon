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

import java.time.Instant;

/**
 * A {@link TimeLine} based on {@link System#nanoTime()} for the
 * purpose of handling timeouts. This time source is intentionally not
 * based on the system clock, in order to not be sensitive to clock skews
 * caused by changes to the wall clock. Consequently, callers should use
 * instants returned by this time source solely for the purpose of
 * comparing them with other instants returned by this same time source.
 * The granularity of instants returned is identical to the granularity
 * of {@link System#nanoTime()}. This time source has the same property
 * of monotonicity than the {@link System#nanoTime()} it is based on.
 */
final class TimeSource implements TimeLine {

    private static final TimeSource SOURCE = new TimeSource();

    private volatile NanoSource nanoSource = new NanoSource();
    private NanoSource localSource = nanoSource;

    private TimeSource() {
    }

    /**
     * Returns the time source.
     *
     * @return the time source.
     */
    public static TimeSource source() {
        return SOURCE;
    }

    /**
     * Returns the current instant obtained from the time source.
     *
     * @return the current instant obtained from the time source.
     * This is equivalent to calling:
     * {@snippet :
     *    TimeSource.source().instant();
     *}
     */
    public static Deadline now() {
        return SOURCE.instant();
    }

    @Override
    public Deadline instant() {
        return instant(System.nanoTime());
    }

    Deadline instant(long nanos) {
        // use localSource if possible to avoid a volatile read
        NanoSource source = localSource;
        long delay = source.delay(nanos);
        if (source.isInWindow(delay)) {
            return source.instant(nanos, delay);
        } else {
            // will cause the time reference to shift forward,
            // at the cost of a volatile write + a volatile read
            var instant = nanoSource.instant(nanos);
            // if this is reordered by the compiler it's not a big deal,
            // will get the new value next time around...
            localSource = nanoSource;
            return instant;
        }
    }

    private final class NanoSource {
        // Duration (in nanoseconds) for which the current nanoSource
        // instance is considered valid.
        // The use of Integer.MAX_VALUE is arbitrary.
        // Any value not too close to Long.MAX_VALUE
        // would do.
        static final long TIME_WINDOW = Integer.MAX_VALUE;

        private final Instant first;
        private final long firstNanos;

        NanoSource() {
            this(Instant.now(), System.nanoTime());
        }

        NanoSource(Instant first, long firstNanos) {
            this.first = first;
            this.firstNanos = firstNanos;
        }

        Deadline instant(long nanos) {
            return instant(nanos, nanos - firstNanos);
        }

        Deadline instant(long nanos, long delay) {
            Instant now = first.plusNanos(delay);
            if (!isInWindow(delay)) {
                // Shifts the time reference (firstNanos) to
                // prevent issues that may be caused by
                // System.nanoTime() - firstNanos wrapping
                // around.
                nanoSource = new NanoSource(now, nanos);
            }
            return Deadline.of(now);
        }

        long delay(long nanos) {
            return nanos - firstNanos;
        }

        boolean isInWindow(long delay) {
            return delay >= -TIME_WINDOW && delay <= TIME_WINDOW;
        }

    }
}
