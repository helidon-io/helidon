/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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

package io.helidon.metrics;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static java.lang.Math.exp;
import static java.lang.Math.min;

/*
 * This class is heavily inspired by:
 * ExponentiallyDecayingReservoir
 *
 * From Dropwizard Metrics v 3.2.3.
 * Distributed under Apache License, Version 2.0
 *
 */

/**
 * An exponentially-decaying random reservoir of {@code long}s. Uses Cormode et al's
 * forward-decaying priority reservoir sampling method to produce a statistically representative
 * sampling reservoir, exponentially biased towards newer entries.
 *
 * @see <a href="http://dimacs.rutgers.edu/~graham/pubs/papers/fwddecay.pdf">
 * Cormode et al. Forward Decay: A Practical Time Decay Model for Streaming Systems. ICDE '09:
 * Proceedings of the 2009 IEEE International Conference on Data Engineering (2009)</a>
 */
class ExponentiallyDecayingReservoir {
    private static final int DEFAULT_SIZE = 1028;
    private static final double DEFAULT_ALPHA = 0.015;
    private static final long RESCALE_THRESHOLD = TimeUnit.HOURS.toNanos(1);

    /*
     * Avoid computing the current time in seconds during every reservoir update by updating its value on a scheduled basis.
     */
    private static final long CURRENT_TIME_IN_SECONDS_UPDATE_INTERVAL_MS = 250;

    private final ScheduledExecutorService currentTimeUpdaterExecutorService;

    private volatile long currentTimeInSeconds;

    private ScheduledExecutorService initCurrentTimeUpdater() {
        ScheduledExecutorService result = Executors.newSingleThreadScheduledExecutor();
        result.scheduleAtFixedRate(this::updateCurrentTimeInSeconds, CURRENT_TIME_IN_SECONDS_UPDATE_INTERVAL_MS,
                CURRENT_TIME_IN_SECONDS_UPDATE_INTERVAL_MS, TimeUnit.MILLISECONDS);
        return result;
    }

    private void updateCurrentTimeInSeconds() {
        currentTimeInSeconds = computeCurrentTimeInSeconds();
    }

    private long computeCurrentTimeInSeconds() {
        return TimeUnit.MILLISECONDS.toSeconds(clock.milliTime());
    }

    private final ConcurrentSkipListMap<Double, WeightedSnapshot.WeightedSample> values;
    private final ReentrantReadWriteLock lock;
    private final double alpha;
    private final int size;
    private final AtomicLong count;
    private final Clock clock;
    private final AtomicLong nextScaleTime;
    private volatile long startTime;

    /**
     * Creates a new {@link ExponentiallyDecayingReservoir} of 1028 elements, which offers a 99.9%
     * confidence level with a 5% margin of error assuming a normal distribution, and an alpha
     * factor of 0.015, which heavily biases the reservoir to the past 5 minutes of measurements.
     */
    ExponentiallyDecayingReservoir(Clock clock) {
        this(DEFAULT_SIZE, DEFAULT_ALPHA, clock);
    }

    /**
     * Creates a new {@link ExponentiallyDecayingReservoir}.
     *
     * @param size  the number of samples to keep in the sampling reservoir
     * @param alpha the exponential decay factor; the higher this is, the more biased the reservoir
     *              will be towards newer values
     */
    ExponentiallyDecayingReservoir(int size, double alpha, Clock clock) {
        this.clock = clock;
        this.values = new ConcurrentSkipListMap<>();
        this.lock = new ReentrantReadWriteLock();
        this.alpha = alpha;
        this.size = size;
        this.count = new AtomicLong(0);
        currentTimeUpdaterExecutorService = initCurrentTimeUpdater();
        currentTimeInSeconds = computeCurrentTimeInSeconds();
        this.startTime = currentTimeInSeconds;
        this.nextScaleTime = new AtomicLong(clock.nanoTick() + RESCALE_THRESHOLD);
    }

    public int size() {
        return (int) min(size, count.get());
    }

    public void update(long value, String label) {
        update(value, currentTimeInSeconds, label);
    }

    /**
     * Adds an old value with a fixed timestamp to the reservoir.
     *
     * @param value     the value to be added
     * @param timestamp the epoch timestamp of {@code value} in seconds
     * @param label     the optional label associated with the sample
     */
    public void update(long value, long timestamp, String label) {
        rescaleIfNeeded();
        lockForRegularUsage();
        try {
            final double itemWeight = weight(timestamp - startTime);
            final WeightedSnapshot.WeightedSample sample = new WeightedSnapshot.WeightedSample(value, itemWeight, label);
            final double priority = itemWeight / ThreadLocalRandom.current().nextDouble();

            final long newCount = count.incrementAndGet();
            if (newCount <= size) {
                values.put(priority, sample);
            } else {
                Double first = values.firstKey();
                if ((first < priority) && (values.putIfAbsent(priority, sample) == null)) {
                    // ensure we always remove an item
                    while (values.remove(first) == null) {
                        first = values.firstKey();
                    }
                }
            }
        } finally {
            unlockForRegularUsage();
        }
    }

    private void rescaleIfNeeded() {
        final long now = clock.nanoTick();
        final long next = nextScaleTime.get();
        if (now >= next) {
            rescale(now, next);
        }
    }

    public WeightedSnapshot getSnapshot() {
        rescaleIfNeeded();
        lockForRegularUsage();
        try {
            return new WeightedSnapshot(values.values());
        } finally {
            unlockForRegularUsage();
        }
    }

    private double weight(long t) {
        return exp(alpha * t);
    }

    /* "A common feature of the above techniques—indeed, the key technique that
     * allows us to track the decayed weights efficiently—is that they maintain
     * counts and other quantities based on g(ti − L), and only scale by g(t − L)
     * at query time. But while g(ti −L)/g(t−L) is guaranteed to lie between zero
     * and one, the intermediate values of g(ti − L) could become very large. For
     * polynomial functions, these values should not grow too large, and should be
     * effectively represented in practice by floating point values without loss of
     * precision. For exponential functions, these values could grow quite large as
     * new values of (ti − L) become large, and potentially exceed the capacity of
     * common floating point types. However, since the values stored by the
     * algorithms are linear combinations of g values (scaled sums), they can be
     * rescaled relative to a new landmark. That is, by the analysis of exponential
     * decay in Section III-A, the choice of L does not affect the final result. We
     * can therefore multiply each value based on L by a factor of exp(−α(L′ − L)),
     * and obtain the correct value as if we had instead computed relative to a new
     * landmark L′ (and then use this new L′ at query time). This can be done with
     * a linear pass over whatever data structure is being used."
     */
    private void rescale(long now, long next) {
        lockForRescale();
        try {
            if (nextScaleTime.compareAndSet(next, now + RESCALE_THRESHOLD)) {
                final long oldStartTime = startTime;
                this.startTime = currentTimeInSeconds;
                final double scalingFactor = exp(-alpha * (startTime - oldStartTime));
                if (Double.compare(scalingFactor, 0) == 0) {
                    values.clear();
                } else {
                    final ArrayList<Double> keys = new ArrayList<Double>(values.keySet());
                    for (Double key : keys) {
                        final WeightedSnapshot.WeightedSample sample = values.remove(key);
                        final WeightedSnapshot.WeightedSample newSample = new WeightedSnapshot.WeightedSample(sample.getValue(),
                                                                                                              sample.getWeight() * scalingFactor,
                                                                                                              sample.label());
                        if (Double.compare(newSample.getWeight(), 0) == 0) {
                            continue;
                        }
                        values.put(key * scalingFactor, newSample);
                    }
                }

                // make sure the counter is in sync with the number of stored samples.
                count.set(values.size());
            }
        } finally {
            unlockForRescale();
        }
    }

    private void unlockForRescale() {
        lock.writeLock().unlock();
    }

    private void lockForRescale() {
        lock.writeLock().lock();
    }

    private void lockForRegularUsage() {
        lock.readLock().lock();
    }

    private void unlockForRegularUsage() {
        lock.readLock().unlock();
    }
}
