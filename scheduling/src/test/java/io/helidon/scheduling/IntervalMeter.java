/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
 *
 */

package io.helidon.scheduling;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import org.hamcrest.Matchers;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

public class IntervalMeter extends CopyOnWriteArrayList<IntervalMeter.Interval> {

    volatile boolean active = true;
    List<Consumer<Interval>> endCallbacks = new ArrayList<>();
    volatile int finishedIntervalCount = 0;
    ReentrantLock lock = new ReentrantLock();

    Interval start() {
        if (!active) {
            return new NoopInterval();
        }
        Interval interval = new IntervalImpl(this);
        this.add(interval);
        return interval;
    }

    IntervalMeter awaitTill(int intervalCount, long timeout, TimeUnit timeUnit) {
        CountDownLatch latch;
        try {
            lock.lock();
            latch = new CountDownLatch(intervalCount - finishedIntervalCount);
            endCallbacks.add(i -> latch.countDown());
        } finally {
            lock.unlock();
        }
        try {
            if (!latch.await(timeout, timeUnit)) {
                fail("Interval meter await timeout");
            }
            active = false;
            assertThat(this.finishedIntervalCount, Matchers.greaterThanOrEqualTo(intervalCount));
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return this;
    }

    IntervalMeter assertNonConcurrent() {
        try {
            lock.lock();
            this.forEach(interval1 -> {
                this.forEach(interval2 -> {
                    if (interval1 == interval2) {
                        return;
                    }

                    if (interval1.startTime().isAfter(interval2.startTime())
                            && interval1.startTime().isBefore(interval2.endTime())) {
                        fail("Interval ovelap");
                    }

                    if (interval1.startTime().isBefore(interval2.startTime())
                            && interval1.endTime().isAfter(interval2.endTime())) {
                        fail("Interval ovelap");
                    }
                });
            });
            return this;
        } finally {
            lock.unlock();
        }
    }

    IntervalMeter assertAverageDuration(Duration expectedDuration, Duration errorMargin) {
        try {
            lock.lock();

            Duration sum = Duration.ZERO;
            Instant lastStart = null;
            for (Interval interval : this) {
                if (lastStart != null) {
                    sum = sum.plus(Duration.between(lastStart, interval.startTime()));
                }
                lastStart = interval.startTime();
            }
            Duration average = sum.dividedBy(this.finishedIntervalCount - 1);
            int comparisonResult = average.compareTo(expectedDuration);
            if (comparisonResult == 0) {
                return this;
            }

            Duration difference;

            if (comparisonResult < 0) {
                difference = expectedDuration.minus(average);
            } else {
                difference = average.minus(average);
            }

            assertThat("Average duration " + average
                    + " between invocations is not within the error margin "
                    + errorMargin
                    + " from expected " + expectedDuration, difference.compareTo(errorMargin) <= 0);
            return this;
        } finally {
            lock.unlock();
        }
    }

    public interface Interval {

        Instant startTime();

        Instant endTime();

        Duration duration();

        Interval sleep(long delay, TimeUnit timeUnit);

        Interval doSomething(Runnable runnable);

        void end();
    }

    public class IntervalImpl implements Interval {
        private final Instant start;
        private final IntervalMeter meter;

        public Duration duration;
        private Instant end;

        public IntervalImpl(IntervalMeter meter) {
            start = Instant.now();
            this.meter = meter;
        }

        @Override
        public Instant startTime() {
            return start;
        }

        @Override
        public Instant endTime() {
            return end;
        }

        @Override
        public Duration duration() {
            return null;
        }

        public Interval sleep(long delay, TimeUnit timeUnit) {
            try {
                timeUnit.sleep(delay);
            } catch (InterruptedException e) {
                //ignore
            }
            return this;
        }

        @Override
        public Interval doSomething(final Runnable runnable) {
            runnable.run();
            return this;
        }

        public void end() {
            try {
                IntervalMeter.this.lock.lock();
                end = Instant.now();
                duration = Duration.between(start, end);
                meter.finishedIntervalCount++;
                meter.endCallbacks.forEach(c -> c.accept(this));
            } finally {
                IntervalMeter.this.lock.unlock();
            }
        }
    }

    public static class NoopInterval implements Interval {

        @Override
        public Instant startTime() {
            return null;
        }

        @Override
        public Instant endTime() {
            return null;
        }

        @Override
        public Duration duration() {
            return Duration.ZERO;
        }

        @Override
        public Interval sleep(final long delay, final TimeUnit timeUnit) {
            return this;
        }

        @Override
        public Interval doSomething(final Runnable runnable) {
            return this;
        }

        @Override
        public void end() {

        }
    }
}
