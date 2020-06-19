/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates.
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

package io.helidon.security.abac.time;

import java.lang.annotation.Annotation;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import io.helidon.common.Errors;
import io.helidon.config.Config;
import io.helidon.security.EndpointConfig;
import io.helidon.security.ProviderRequest;
import io.helidon.security.SecurityLevel;
import io.helidon.security.providers.abac.AbacAnnotation;
import io.helidon.security.providers.abac.AbacValidatorConfig;
import io.helidon.security.providers.abac.spi.AbacValidator;

/**
 * Attribute validator for time based attribute checks.
 * Currently supports:
 * <ul>
 * <li>between times (of a day - e.g. 8:30 - 17:00</li>
 * <li>days of week (e.g. MONDAY, TUESDAY)</li>
 * </ul>
 */
public final class TimeValidator implements AbacValidator<TimeValidator.TimeConfig> {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    private TimeValidator() {
    }

    /**
     * Return a new instance of this validator with default configuration.
     *
     * @return new time validator
     */
    public static TimeValidator create() {
        return new TimeValidator();
    }

    @Override
    public Class<TimeConfig> configClass() {
        return TimeConfig.class;
    }

    @Override
    public String configKey() {
        return "time";
    }

    @Override
    public TimeConfig fromConfig(Config config) {
        return TimeConfig.create(config);
    }

    @Override
    public TimeConfig fromAnnotations(EndpointConfig endpointConfig) {
        TimeConfig.Builder builder = TimeConfig.builder();

        for (SecurityLevel securityLevel : endpointConfig.securityLevels()) {
            for (EndpointConfig.AnnotationScope scope : EndpointConfig.AnnotationScope.values()) {
                List<Annotation> annotations = new ArrayList<>();
                for (Class<? extends Annotation> annotation : supportedAnnotations()) {
                    annotations.addAll(securityLevel.filterAnnotations(annotation, scope));
                }
                for (Annotation annotation : annotations) {
                    if (annotation instanceof DaysOfWeek) {
                        DaysOfWeek daw = (DaysOfWeek) annotation;
                        for (DayOfWeek dayOfWeek : daw.value()) {
                            builder.addDaysOfWeek(dayOfWeek);
                        }
                    } else if (annotation instanceof TimesOfDay) {
                        TimesOfDay tods = (TimesOfDay) annotation;
                        for (TimeOfDay tod : tods.value()) {
                            builder.addBetween(LocalTime.parse(tod.from()),
                                               LocalTime.parse(tod.to()));
                        }
                    } else if (annotation instanceof TimeOfDay) {
                        TimeOfDay tod = (TimeOfDay) annotation;
                        builder.addBetween(LocalTime.parse(tod.from()),
                                           LocalTime.parse(tod.to()));
                    }
                }
            }
        }
        return builder.build();
    }

    @Override
    public void validate(TimeConfig config, Errors.Collector collector, ProviderRequest request) {
        ZonedDateTime now = request.env().time();
        config.validate(this, now, collector);
    }

    @Override
    public Collection<Class<? extends Annotation>> supportedAnnotations() {
        return Set.of(TimesOfDay.class, TimeOfDay.class, DaysOfWeek.class);
    }

    /**
     * Constraint for a time of day.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD, ElementType.TYPE})
    @Documented
    @Inherited
    @AbacAnnotation
    @Repeatable(TimesOfDay.class)
    public @interface TimeOfDay {
        /**
         * Time after which this resource is accessible within a day.
         *
         * @return String formatted as "HH:mm:ss.SSS" - hours, minutes, seconds and milliseconds, or "HH:mm:ss", or "HH:mm"
         */
        String from() default "00:00:00";

        /**
         * Time before which this resource is accessible within a day.
         *
         * @return String formatted as "HH:mm:ss.SSS" - hours, minutes, seconds and milliseconds, or "HH:mm:ss", or "HH:mm"
         */
        String to() default "23:59:59.999";
    }

    /**
     * Constraint for a time of day - container for repeating {@link TimeOfDay}.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD, ElementType.TYPE})
    @Documented
    @Inherited
    @AbacAnnotation
    public @interface TimesOfDay {
        /**
         * Repeatable annotation holder.
         *
         * @return repeatable annotation
         */
        TimeOfDay[] value();
    }

    /**
     * Attribute annotation that can limit the days of week the resource is accessible.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD, ElementType.TYPE})
    @Documented
    @Inherited
    @AbacAnnotation
    public @interface DaysOfWeek {
        /**
         * Return days of week this resource should be accessible on.
         *
         * @return array of {@link DayOfWeek}
         */
        DayOfWeek[] value();
    }

    /**
     * Configuration for time attribute validator.
     */
    public static final class TimeConfig implements AbacValidatorConfig {
        private final List<BetweenTime> betweenTimes = new ArrayList<>();
        private final Set<DayOfWeek> daysOfWeek = EnumSet.noneOf(DayOfWeek.class);

        private TimeConfig(Builder builder) {
            this.betweenTimes.addAll(builder.betweenTimes);
            this.daysOfWeek.addAll(builder.daysOfWeek);
        }

        /**
         * Builder for this class.
         *
         * @return a new builder instance
         */
        public static Builder builder() {
            return new Builder();
        }

        /**
         * Create a time config for a check between times within a day.
         *
         * @param from from time (ignoring date part)
         * @param to   to time (ignoring date part)
         * @return time configuration for this between config
         */
        public static TimeConfig between(LocalTime from, LocalTime to) {
            return builder()
                    .addBetween(from, to)
                    .build();
        }

        /**
         * Create a time config for a check for days of week.
         *
         * @param days days the endpoint should be accessible on
         * @return time configuration for this week day config
         */
        public static TimeConfig daysOfWeek(DayOfWeek... days) {
            return builder()
                    .addDaysOfWeek(days)
                    .build();
        }

        /**
         * Create an time config from configuration.
         *
         * @param config configuration located on this validator config key
         * @return time configuration based on the config
         */
        public static TimeConfig create(Config config) {
            Builder builder = TimeConfig.builder();

            config.get("time-of-day").asList(Config.class)
                    .get()
                    .forEach(tod -> builder.addBetween(LocalTime.parse(tod.get("from").asString().orElse("00:00:00")),
                                                       LocalTime.parse(tod.get("to").asString().orElse("24:00:00"))));

            config.get("days-of-week").asList(DayOfWeek.class)
                    .ifPresent(builder::addDaysOfWeek);

            return builder.build();
        }

        void validate(TimeValidator validator, ZonedDateTime now, Errors.Collector collector) {
            // between times - it must fit at least one
            boolean valid = false;

            LocalTime nowTime = now.toLocalTime();
            for (BetweenTime betweenTime : betweenTimes) {
                if (betweenTime.isValid(nowTime)) {
                    valid = true;
                }
            }
            if (!valid) {
                collector.fatal(validator, nowTime.format(TIME_FORMATTER) + " is in neither of allowed times: " + betweenTimes);
            }

            DayOfWeek dayOfWeek = now.getDayOfWeek();
            if (!daysOfWeek.contains(dayOfWeek)) {
                collector.fatal(validator, dayOfWeek + " is not in allowed days: " + daysOfWeek);
            }
        }

        TimeConfig combineWith(TimeConfig child) {
            return TimeConfig.builder().from(this).from(child).build();
        }

        private static class BetweenTime {
            private final LocalTime from;
            private final LocalTime to;

            BetweenTime(LocalTime from, LocalTime to) {
                this.from = from;
                this.to = to;
            }

            boolean isValid(LocalTime nowTime) {
                return from.isBefore(nowTime) && to.isAfter(nowTime);
            }

            @Override
            public String toString() {
                return from + " - " + to;
            }
        }

        /**
         * Fluent API builder for {@link TimeConfig}.
         */
        public static final class Builder implements io.helidon.common.Builder<TimeConfig> {
            private final List<BetweenTime> betweenTimes = new ArrayList<>();
            private final Set<DayOfWeek> daysOfWeek = EnumSet.noneOf(DayOfWeek.class);

            private Builder() {
            }

            @Override
            public TimeConfig build() {
                return new TimeConfig(this);
            }

            /**
             * Add a new "between time" configuration.
             *
             * @param from from when
             * @param to   until when
             * @return updated builder instance
             */
            public Builder addBetween(LocalTime from, LocalTime to) {
                this.betweenTimes.add(new BetweenTime(from, to));
                return this;
            }

            /**
             * Add a new "day of week" configuration.
             *
             * @param daysOfWeek days to add
             * @return updated builder instance
             */
            public Builder addDaysOfWeek(DayOfWeek... daysOfWeek) {
                this.daysOfWeek.addAll(Arrays.asList(daysOfWeek));
                return this;
            }

            /**
             * Add a new "day of week" configuration.
             *
             * @param daysOfWeek days to add
             * @return updated builder instance
             */
            public Builder addDaysOfWeek(List<DayOfWeek> daysOfWeek) {
                this.daysOfWeek.addAll(daysOfWeek);
                return this;
            }

            /**
             * Update this builder from an existing configuration instance.
             *
             * @param timeConfig time configuration to add to this builder
             * @return updated builder instance
             */
            public Builder from(TimeConfig timeConfig) {
                betweenTimes.addAll(timeConfig.betweenTimes);
                daysOfWeek.addAll(timeConfig.daysOfWeek);
                return this;
            }
        }
    }
}
