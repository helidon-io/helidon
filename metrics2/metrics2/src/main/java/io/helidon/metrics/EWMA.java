/*
 * Copyright (c) 2018, 2019 Oracle and/or its affiliates. All rights reserved.
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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

import static java.lang.StrictMath.exp;

/*
 * This class is heavily inspired by:
 * EWMA
 *
 * From Dropwizard Metrics v 3.2.3.
 * Distributed under Apache License, Version 2.0
 *
 */

/**
 * An exponentially-weighted moving average.
 *
 * @see <a href="http://www.teamquest.com/pdfs/whitepaper/ldavg1.pdf">UNIX Load Average Part 1: How
 * It Works</a>
 * @see <a href="http://www.teamquest.com/pdfs/whitepaper/ldavg2.pdf">UNIX Load Average Part 2: Not
 * Your Average Average</a>
 * @see <a href="http://en.wikipedia.org/wiki/Moving_average#Exponential_moving_average">EMA</a>
 */
final class EWMA {
    private static final int INTERVAL = 5;
    private static final double SECONDS_PER_MINUTE = 60.0;
    private static final int ONE_MINUTE = 1;
    private static final int FIVE_MINUTES = 5;
    private static final int FIFTEEN_MINUTES = 15;
    private static final double M1_ALPHA = 1 - exp(-INTERVAL / SECONDS_PER_MINUTE / ONE_MINUTE);
    private static final double M5_ALPHA = 1 - exp(-INTERVAL / SECONDS_PER_MINUTE / FIVE_MINUTES);
    private static final double M15_ALPHA = 1 - exp(-INTERVAL / SECONDS_PER_MINUTE / FIFTEEN_MINUTES);
    private final LongAdder uncounted = new LongAdder();
    private final double alpha;
    private final double interval;
    private volatile boolean initialized = false;
    private volatile double rate = 0.0;

    /**
     * Create a new EWMA with a specific smoothing constant.
     *
     * @param alpha        the smoothing constant
     * @param interval     the expected tick interval
     * @param intervalUnit the time unit of the tick interval
     */
    private EWMA(double alpha, long interval, TimeUnit intervalUnit) {
        this.interval = intervalUnit.toNanos(interval);
        this.alpha = alpha;
    }

    /**
     * Creates a new EWMA which is equivalent to the UNIX one minute load average and which expects
     * to be ticked every 5 seconds.
     *
     * @return a one-minute EWMA
     */
    static EWMA oneMinuteEWMA() {
        return new EWMA(M1_ALPHA, INTERVAL, TimeUnit.SECONDS);
    }

    /**
     * Creates a new EWMA which is equivalent to the UNIX five minute load average and which expects
     * to be ticked every 5 seconds.
     *
     * @return a five-minute EWMA
     */
    static EWMA fiveMinuteEWMA() {
        return new EWMA(M5_ALPHA, INTERVAL, TimeUnit.SECONDS);
    }

    /**
     * Creates a new EWMA which is equivalent to the UNIX fifteen minute load average and which
     * expects to be ticked every 5 seconds.
     *
     * @return a fifteen-minute EWMA
     */
    static EWMA fifteenMinuteEWMA() {
        return new EWMA(M15_ALPHA, INTERVAL, TimeUnit.SECONDS);
    }

    /**
     * Update the moving average with a new value.
     *
     * @param n the new value
     */
    void update(long n) {
        uncounted.add(n);
    }

    /**
     * Mark the passage of time and decay the current rate accordingly.
     */
    void tick() {
        final long count = uncounted.sumThenReset();
        final double instantRate = count / interval;
        if (initialized) {
            double currentRate = rate;
            currentRate += (alpha * (instantRate - currentRate));
            // we may lose changes that happen at the very same moment as the previous two lines, though better than being
            // inconsistent
            rate = currentRate;
        } else {
            rate = instantRate;
            initialized = true;
        }
    }

    /**
     * Returns the rate in the given units of time.
     *
     * @param rateUnit the unit of time
     * @return the rate
     */
    double getRate(TimeUnit rateUnit) {
        return rate * (double) rateUnit.toNanos(1);
    }
}
