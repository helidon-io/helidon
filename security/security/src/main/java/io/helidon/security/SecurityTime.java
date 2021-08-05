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

package io.helidon.security;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import io.helidon.config.Config;
import io.helidon.config.metadata.Configured;
import io.helidon.config.metadata.ConfiguredOption;

/**
 * Time used in security, configurable.
 * Configuration may either shift time (to past or future) or explicitly set a value on one of the time fields (e.g. year, month)
 */
public class SecurityTime {
    private final long shiftSeconds;
    private final ZoneId timeZone;
    private final List<ChronoValues> chronoValues = new ArrayList<>();

    private SecurityTime(Builder builder) {
        this.shiftSeconds = builder.shiftBySeconds;
        this.timeZone = builder.timeZone;
        this.chronoValues.addAll(builder.values);
    }

    /**
     * A new builder for this class.
     *
     * @return builder to build a new instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a new security time based on default time zone and current time.
     *
     * @return security time
     */
    public static SecurityTime create() {
        return SecurityTime.builder().build();
    }

    /**
     * Load an instance from configuration.
     * <p>
     * Example:
     * <pre>
     * # server-time
     * server-time:
     *   # can shift time if needed (before explicit values are applied
     *   # shift-by-seconds: -1020
     *   #
     *   # All of the following settings:
     *   #   if configured, will override actual value, if not set, current value is used
     *   #
     *   # definition of a time zone (that is valid for ZoneId.of())
     *   # this will move the time to the specific timezone (same instant)
     *   # Time zone is applied first, everything else after
     *   # time-zone: Europe/Prague
     *   time-zone: "Australia/Darwin"
     *   year: 2017
     *   # 1 for January, 12 for December
     *   month: 9
     *   # day of month (1 - 31)
     *   day-of-month: 6
     *   # hour of day (0 - 23)
     *   hour-of-day: 13
     *   # minute of hour (0 - 59)
     *   minute: 0
     *   # second of minute (0-59)
     *   second: 0
     *   # millisecond of minute (0-999)
     *   # millisecond: 0
     * </pre>
     *
     * @param config configuration located on the key "server-time" in example above (the key name can differ, the content is
     *               important)
     * @return a new instance of time configured from this configuration
     */
    public static SecurityTime create(Config config) {
        return builder().config(config).build();
    }

    /**
     * Get current (or as configured) time.
     *
     * @return a date time with a time-zone information as configured for this instance
     */
    public ZonedDateTime get() {
        ZonedDateTime zdt = ZonedDateTime.now();
        zdt = zdt.withZoneSameInstant(timeZone);

        zdt = zdt.plus(shiftSeconds, ChronoUnit.SECONDS);

        for (ChronoValues chronoValues : this.chronoValues) {
            zdt = zdt.with(chronoValues.field, chronoValues.value);
        }

        return zdt;
    }

    /**
     * Fluent API builder for {@link SecurityTime}.
     */
    @Configured
    public static final class Builder implements io.helidon.common.Builder<SecurityTime> {
        private final List<ChronoValues> values = new ArrayList<>();
        private ZoneId timeZone = ZoneId.systemDefault();
        private long shiftBySeconds = 0;

        private Builder() {
        }

        @Override
        public SecurityTime build() {
            return new SecurityTime(this);
        }

        /**
         * Override current time zone. The time will represent the SAME instant, in an explicit timezone.
         * <p>
         * If we are in a UTC time zone and you set the timezone to "Europe/Prague", the time will be shifted by the offset
         * of Prague (e.g. if it is noon right now in UTC, you would get 14:00).
         *
         * @param zoneId zone id to use for the instance being built
         * @return updated builder instance
         */
        @ConfiguredOption
        public Builder timeZone(ZoneId zoneId) {
            this.timeZone = zoneId;
            return this;
        }

        /**
         * Configure a time-shift in seconds, to move the current time to past or future.
         *
         * @param seconds number of seconds by which we want to shift the system time, may be negative
         * @return updated builder instance
         */
        @ConfiguredOption(defaultValue = "0")
        public Builder shiftBySeconds(long seconds) {
            this.shiftBySeconds = seconds;
            return this;
        }

        /**
         * Set an explicit value for one of the time fields (such as {@link ChronoField#YEAR}).
         *
         * @param field field to set
         * @param value value to set on the field, see javadoc of each field to see possible values
         * @return updated builder instance
         */
        @ConfiguredOption(value = "year", type = Long.class)
        @ConfiguredOption(value = "month", type = Long.class)
        @ConfiguredOption(value = "day-of-month", type = Long.class)
        @ConfiguredOption(value = "hour-of-day", type = Long.class)
        @ConfiguredOption(value = "minute", type = Long.class)
        @ConfiguredOption(value = "second", type = Long.class)
        @ConfiguredOption(value = "millisecond", type = Long.class)
        public Builder value(ChronoField field, long value) {
            this.values.add(new ChronoValues(field, value));
            return this;
        }

        /**
         * Update this builder from configuration. The config should be located on parent key of the following keys
         * (all of them are optional):
         * <ul>
         * <li>time-zone: set time zone id (such as Europe/Prague) of desired time zone</li>
         * <li>shift-by-seconds: {@link #shiftBySeconds(long)}</li>
         * <li>year: set an explicit year value</li>
         * <li>month: set an explicit month value (1-12)</li>
         * <li>day-of-month: set an explicit day of month value (1-31)</li>
         * <li>hour-of-day: set an explicit hour of day value (0-23)</li>
         * <li>minute: set an explicit minute value (0-59)</li>
         * <li>second: set an explicit second value (0-59)</li>
         * <li>millisecond: set an explicit millisecond value (0-999)</li>
         * </ul>
         *
         * @param config configuration to read data from
         * @return updated builder instance
         */
        public Builder config(Config config) {
            // modification, time flows as usual
            config.get("time-zone").asString().map(ZoneId::of).ifPresent(this::timeZone);
            config.get("shift-by-seconds").asLong().ifPresent(this::shiftBySeconds);

            // explicit values, specific value is fixed in time
            config.get("year").asLong().ifPresent(it -> value(ChronoField.YEAR, it));
            config.get("month").asLong().ifPresent(it -> value(ChronoField.MONTH_OF_YEAR, it));
            config.get("day-of-month").asLong().ifPresent(it -> value(ChronoField.DAY_OF_MONTH, it));
            config.get("hour-of-day").asLong().ifPresent(it -> value(ChronoField.HOUR_OF_DAY, it));
            config.get("minute").asLong().ifPresent(it -> value(ChronoField.MINUTE_OF_HOUR, it));
            config.get("second").asLong().ifPresent(it -> value(ChronoField.SECOND_OF_MINUTE, it));
            config.get("millisecond").asLong().ifPresent(it -> value(ChronoField.MILLI_OF_SECOND, it));

            return this;
        }
    }

    private static final class ChronoValues {
        private final ChronoField field;
        private final long value;

        private ChronoValues(ChronoField field, long delta) {
            this.field = field;
            this.value = delta;
        }
    }
}

