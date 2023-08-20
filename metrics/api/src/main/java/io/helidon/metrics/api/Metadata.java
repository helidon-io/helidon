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
 * Holder for metadata to declare meters, compatible with that from Microprofile, to ease the transition to the current
 * Helidon metrics API.
 *
 * @param name name for the meter
 * @param description description for the meter
 * @param unit unit for the meter
 *
 * @deprecated Use the various meter builders to prepare their characteristics, for example
 * {@link io.helidon.metrics.api.Gauge.Builder}
 */
@Deprecated(since = "4.0.0")
public record Metadata(String name, String description, String unit) {

    /**
     * Creates a new {@linkplain MetadataBuilder builder} for metadata.
     *
     * @return new builder
     */
    public static MetadataBuilder builder() {
        return new MetadataBuilder();
    }

    /**
     * Creates a new instance based on an existing {@link io.helidon.metrics.api.Meter}.
     *
     * @param meter meter to draw metadata from
     * @return new metadata instance
     */
    public static Metadata create(Meter meter) {
        return builder(meter).build();
    }

    /**
     * Creates a new {@linkplain io.helidon.metrics.api.MetadataBuilder buider} based on existing
     * {@link io.helidon.metrics.api.Metadata} instance.
     *
     * @param meta existing metadata instance
     * @return new builder preset with values from the metadata instance
     */
    public static MetadataBuilder builder(Metadata meta) {
        return new MetadataBuilder(meta);
    }

    /**
     * Creates a new {@linkplain io.helidon.metrics.api.MetadataBuilder builder} based on an existing
     * {@link io.helidon.metrics.api.Meter} instance.
     *
     * @param meter existing meter instance
     * @return new builder preset with values from the meter
     */
    public static MetadataBuilder builder(Meter meter) {
        return new MetadataBuilder(meter);
    }


    /**
     * Returns the name.
     *
     * @return name
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the description.
     *
     * @return description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Returns the unit.
     *
     * @return unit
     */
    public String getUnit() {
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
    public <B extends Meter.Builder<B, M>, M extends Meter> B apply(B builder) {
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
}
