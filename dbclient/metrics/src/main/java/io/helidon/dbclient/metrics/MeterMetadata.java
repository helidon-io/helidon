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
package io.helidon.dbclient.metrics;

import io.helidon.metrics.api.Meter;

/**
 * Holder for metadata to declare meters.
 */
class MeterMetadata {

    /**
     * Creates a new builder for metadata.
     *
     * @return new builder
     */
    static MeterMetadata.Builder builder() {
        return new MeterMetadata.Builder();
    }

    /**
     * Creates a new instance based on an existing {@link io.helidon.metrics.api.Meter}.
     *
     * @param meter meter to draw metadata from
     * @return new metadata instance
     */
    static MeterMetadata create(Meter meter) {
        return builder(meter).build();
    }

    /**
     * Creates a new builder based on existing meter metadata.
     *
     * @param meta existing meter metadata instance
     * @return new builder preset with values from the metadata instance
     */
    static MeterMetadata.Builder builder(MeterMetadata meta) {
        return new MeterMetadata.Builder(meta);
    }

    /**
     * Creates a new MeterMetadata builder based on an existing {@link io.helidon.metrics.api.Meter} instance.
     *
     * @param meter existing meter instance
     * @return new builder preset with values from the meter
     */
    static MeterMetadata.Builder builder(Meter meter) {
        return new MeterMetadata.Builder(meter);
    }

    private final String name;
    private final String description;
    private final String unit;

    private MeterMetadata(String name, String description, String unit) {
        this.name = name;
        this.description = description;
        this.unit = unit;
    }


    /**
     * Returns the name.
     *
     * @return name
     */
    String name() {
        return name;
    }

    /**
     * Returns the description.
     *
     * @return description
     */
    String description() {
        return description;
    }

    /**
     * Returns the unit.
     *
     * @return unit
     */
    String unit() {
        return unit;
    }

    /**
     * Applies this metadata to the provided {@link Meter.Builder}.
     *
     * @param builder the Meter.Builder to use in constructing a new meter
     * @return the input builder updated with any non-null settings from this metadata instance
     * @param <B> type of the Meter.Builder builder
     * @param <M> type of the Meter to be created by the Meter.Builder builder
     */
    <B extends Meter.Builder<B, M>, M extends Meter> B apply(B builder) {
        if (name != null && !name.equals(builder.name())) {
            throw new IllegalArgumentException("name in Metadata.Builder '" + name
                                                       + "' does not match name in Meter.Builder '" + builder.name() + "'");
        }
        if (description != null) {
            builder.description(description);
        }
        if (unit != null) {
            builder.baseUnit(unit);
        }
        return builder;
    }

    static class Builder implements io.helidon.common.Builder<Builder, MeterMetadata> {

        private String name;
        private String description;
        private String unit;

        Builder(MeterMetadata meta) {
            this.name = meta.name();
            this.description = meta.description();
            this.unit = meta.unit();
        }

        Builder(Meter meter) {
            name = meter.id().name();
            meter.description().ifPresent(d -> description = d);
            meter.baseUnit().ifPresent(u -> unit = u);
        }

        Builder() {
        }

        /**
         * Sets the name in the builder.
         *
         * @param name name to store in metadata
         * @return updated builder
         */
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * Sets the description in the builder.
         *
         * @param description description to store in metadata
         * @return updated builder
         */
        public Builder description(String description) {
            this.description = description;
            return this;
        }

        /**
         * Sets the unit for the builder.
         *
         * @param unit unit for the meter
         * @return updated builder
         */
        public Builder unit(String unit) {
            this.unit = unit;
            return this;
        }

        /**
         * Creates a new meter metadata instance from the values currently set in the builder.
         *
         * @return new metadata instance
         */
        public MeterMetadata build() {
            return new MeterMetadata(name, description, unit);
        }
    }
}
