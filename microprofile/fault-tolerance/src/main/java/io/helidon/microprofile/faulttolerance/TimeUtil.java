/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.microprofile.faulttolerance;

import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Class TimeUtil.
 */
public class TimeUtil {

    /**
     * Converts a {@code ChronoUnit} to the equivalent {@code TimeUnit}.
     *
     * @param chronoUnit the ChronoUnit to convert
     * @return the converted equivalent TimeUnit
     * @throws IllegalArgumentException if {@code chronoUnit} has no equivalent TimeUnit
     * @throws NullPointerException if {@code chronoUnit} is null
     */
    public static TimeUnit chronoUnitToTimeUnit(ChronoUnit chronoUnit) {
        switch (Objects.requireNonNull(chronoUnit, "chronoUnit")) {
            case NANOS:
                return TimeUnit.NANOSECONDS;
            case MICROS:
                return TimeUnit.MICROSECONDS;
            case MILLIS:
                return TimeUnit.MILLISECONDS;
            case SECONDS:
                return TimeUnit.SECONDS;
            case MINUTES:
                return TimeUnit.MINUTES;
            case HOURS:
                return TimeUnit.HOURS;
            case DAYS:
                return TimeUnit.DAYS;
            default:
                throw new IllegalArgumentException("No TimeUnit equivalent for ChronoUnit");
        }
    }

    /**
     * Converts this {@code TimeUnit} to the equivalent {@code ChronoUnit}.
     *
     * @param timeUnit The TimeUnit
     * @return the converted equivalent ChronoUnit
     * @throws IllegalArgumentException if {@code chronoUnit} has no equivalent TimeUnit
     * @throws NullPointerException if {@code chronoUnit} is null
     */
    public static ChronoUnit timeUnitToChronoUnit(TimeUnit timeUnit) {
        switch (Objects.requireNonNull(timeUnit, "chronoUnit")) {
            case NANOSECONDS:
                return ChronoUnit.NANOS;
            case MICROSECONDS:
                return ChronoUnit.MICROS;
            case MILLISECONDS:
                return ChronoUnit.MILLIS;
            case SECONDS:
                return ChronoUnit.SECONDS;
            case MINUTES:
                return ChronoUnit.MINUTES;
            case HOURS:
                return ChronoUnit.HOURS;
            case DAYS:
                return ChronoUnit.DAYS;
            default:
                throw new IllegalArgumentException("No ChronoUnit equivalent for TimeUnit");
        }
    }

    /**
     * Converts a duration and its chrono unit to millis.
     *
     * @param duration The duration.
     * @param chronoUnit The unit of the duration.
     * @return Milliseconds.
     */
    public static long convertToMillis(long duration, ChronoUnit chronoUnit) {
        final TimeUnit timeUnit = chronoUnitToTimeUnit(chronoUnit);
        return TimeUnit.MILLISECONDS.convert(duration, timeUnit);
    }

    private TimeUtil() {
    }
}
