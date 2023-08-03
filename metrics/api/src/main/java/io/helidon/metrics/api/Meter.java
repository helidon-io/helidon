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

/**
 * Common behavior of all meters.
 */
public interface Meter extends Wrapped {

    /**
     * Common behavior of specific meter builders.
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
         * Sets the description.
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


//        String name();
//
//        Iterable<Tag> tags();
//
//        String description();
//
//        String baseUnit();
    }

    /**
     * Unique idenfier for a meter.
     */
    interface Id {

        /**
         * Creates a {@link io.helidon.metrics.api.Meter.Id} from the specified name.
         *
         * @param name name for the ID
         * @return new meter ID
         */
        static Id of(String name) {
            return MetricsFactoryManager.getInstance().idOf(name);
        }

        /**
         * Creates a {@link io.helidon.metrics.api.Meter.Id} from the specified name and tags.
         *
         * @param name name for the ID
         * @param tags tags for the ID
         * @return new meter ID
         */
        static Id of(String name, Iterable<Tag> tags) {
            return MetricsFactoryManager.getInstance().idOf(name, tags);
        }

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
         * Unwraps the ID as the specified type.
         *
         * @param c {@link Class} to which to cast this ID
         * @return the ID cast as the requested type
         * @param <R> type to cast to
         */
        default <R> R unwrap(Class<? extends R> c) {
            return c.cast(this);
        }
    }

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

    }

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
    String baseUnit();

    /**
     * Returns the meter's description.
     *
     * @return description
     */
    String description();

    /**
     * Returns the meter type.
     *
     * @return meter type
     */
    Type type();
}
