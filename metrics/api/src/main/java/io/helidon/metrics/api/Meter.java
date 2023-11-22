/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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
package io.helidon.metrics.api;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * Common behavior of all meters.
 */
public interface Meter extends Wrapper {

    /**
     * Returns the meter ID.
     *
     * @return meter ID
     */
    Id id();

    /**
     * Returns the meter's base unit.
     *
     * @return base unit
     */
    Optional<String> baseUnit();

    /**
     * Returns the meter's description.
     *
     * @return description
     */
    Optional<String> description();

    /**
     * Returns the meter type.
     *
     * @return meter type
     */
    Type type();

    /**
     * Returns the scope associated with the meter.
     *
     * @return scope
     */
    Optional<String> scope();

    /**
     * Type of meter.
     */
    enum Type {

        /**
         * Counter (monotonically increasing value).
         */
        COUNTER,

        /**
         * Gauge (can increase or decrease).
         */
        GAUGE,

        /**
         * Timer (measures count and distribution of completed events).
         */
        TIMER,

        /**
         * Distribution summary (measures distribution of samples).
         */
        DISTRIBUTION_SUMMARY,

        /**
         * Other.
         */
        OTHER;

        /**
         * Type name suitable for metadata output.
         *
         * @return name of the type formatted for human output
         */
        public String typeName() {
            return name().toLowerCase(Locale.ROOT);
        }

    }

    /**
     * Common behavior of specific meter builders.
     *
     * <p>
     * This builder does not extend the conventional Helidon builder because, typically, "building" a meter involves
     * registering it which is implementation-specific and therefore should be performed only by each implementation.
     * We do not want developers to see a {@code build()} operation that they should not invoke.
     * </p>
     *
     * @param <B> type of the builder
     * @param <M> type of the meter the builder creates
     */
    interface Builder<B extends Builder<B, M>, M extends Meter> {

        /**
         * Returns the type-correct "this".
         *
         * @return properly-typed builder itself
         */
        @SuppressWarnings("unchecked")
        default B identity() {
            return (B) this;
        }

        /**
         * Sets the tags to use in identifying the build meter.
         *
         * @param tags {@link io.helidon.metrics.api.Tag} instances to identify the meter
         * @return updated builder
         */
        B tags(Iterable<Tag> tags);

        /**
         * Adds a single tag to the builder's collection.
         *
         * @param tag the tag to add
         * @return updated builder
         */
        B addTag(Tag tag);

        /**
         * Sets the description.
         *
         * @param description meter description
         * @return updated builder
         */
        B description(String description);

        /**
         * Sets the units.
         *
         * @param baseUnit meter unit
         * @return updated builder
         */
        B baseUnit(String baseUnit);

        /**
         * Sets the scope to be associated with this meter.
         *
         * @param scope scope
         * @return updated builder
         */
        B scope(String scope);

        /**
         * Returns the name the builder will use.
         *
         * @return name
         */
        String name();

        /**
         * Returns the tags the builder will use.
         *
         * @return tags
         */
        Map<String, String> tags();

        /**
         * Returns the description the builder will use.
         *
         * @return description if set; empty otherwise
         */
        Optional<String> description();

        /**
         * Returns the base unit the builder will use.
         *
         * @return base unit if set; empty otherwise
         */
        Optional<String> baseUnit();

        /**
         * Returns the scope set in the builder, if any.
         *
         * @return the assigned scope if set; empty otherwise
         */
        Optional<String> scope();
    }

    /**
     * Unique idenfier for a meter.
     */
    interface Id {

        /**
         * Returns the meter name.
         *
         * @return meter name
         */
        String name();

        /**
         * Returns the tags which further identify the meter.
         *
         * @return meter tags
         */
        Iterable<Tag> tags();

        /**
         * Return the tags as a map.
         *
         * @return map of tag keys to values
         */
        default Map<String, String> tagsMap() {
            Map<String, String> result = new TreeMap<>();
            tags().forEach(tag -> result.put(tag.key(), tag.value()));
            return result;
        }

        /**
         * Unwraps the ID as the specified type.
         *
         * @param c   {@link Class} to which to cast this ID
         * @param <R> type to cast to
         * @return the ID cast as the requested type
         */
        default <R> R unwrap(Class<? extends R> c) {
            return c.cast(this);
        }
    }

    /**
     * Constants for the pre-defined scopes.
     */
    class Scope {

        /**
         * Application scope.
         */
        public static final String APPLICATION = "application";

        /**
         * Base scope.
         */
        public static final String BASE = "base";

        /**
         * Vendor scope.
         */
        public static final String VENDOR = "vendor";

        /**
         * All the predefined scopes.
         */
        public static final Set<String> BUILT_IN_SCOPES = Set.of(BASE, VENDOR, APPLICATION);

        /**
         * Default scope if none is specified for a given meter.
         */
        public static final String DEFAULT = APPLICATION;

        private Scope() {
        }
    }

    /**
     * Common unit declarations (inspired by the list from MicroProfile metrics). Users can use any units they wish.
     */
    class BaseUnits {

        /**
         * No unit.
         */
        public static final String NONE = "none";

        /**
         * Represents bits. Not defined by SI, but by IEC 60027.
         */
        public static final String BITS = "bits";
        /**
         * 1000 {@link #BITS}.
         */
        public static final String KILOBITS = "kilobits";
        /**
         * 1000 {@link #KILOBITS}.
         */
        public static final String MEGABITS = "megabits";
        /**
         * 1000 {@link #MEGABITS}.
         */
        public static final String GIGABITS = "gigabits";
        /**
         * 1024 {@link #BITS}.
         */
        public static final String KIBIBITS = "kibibits";
        /**
         * 1024 {@link #KIBIBITS}.
         */
        public static final String MEBIBITS = "mebibits";
        /**
         * 1024 {@link #MEBIBITS}.
         */
        public static final String GIBIBITS = "gibibits";

        /**
         * 8 {@link #BITS}.
         */
        public static final String BYTES = "bytes";
        /**
         * 1000 {@link #BYTES}.
         */
        public static final String KILOBYTES = "kilobytes";
        /**
         * 1000 {@link #KILOBYTES}.
         */
        public static final String MEGABYTES = "megabytes";
        /**
         * 1000 {@link #MEGABYTES}.
         */
        public static final String GIGABYTES = "gigabytes";

        /**
         * 1/1000 {@link #MICROSECONDS}.
         */
        public static final String NANOSECONDS = "nanoseconds";
        /**
         * 1/1000 {@link #MILLISECONDS}.
         */
        public static final String MICROSECONDS = "microseconds";
        /**
         * 1/1000 {@link #SECONDS}.
         */
        public static final String MILLISECONDS = "milliseconds";
        /**
         * Represents seconds.
         */
        public static final String SECONDS = "seconds";
        /**
         * 60 {@link #SECONDS}.
         */
        public static final String MINUTES = "minutes";
        /**
         * 60 {@link #MINUTES}.
         */
        public static final String HOURS = "hours";
        /**
         * 24 {@link #HOURS}.
         */
        public static final String DAYS = "days";

        /**
         * Represents percentage.
         */
        public static final String PERCENT = "percent";

        /**
         * Represent per second.
         */
        public static final String PER_SECOND = "per_second";

        private BaseUnits() {
        }
    }
}
