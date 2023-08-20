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
 * Convenience class for easing the migration to the Helidon metrics API.
 * @deprecated Use the various meter builders to prepare their characteristics, for example
 * {@link io.helidon.metrics.api.Gauge.Builder}
 */
@Deprecated(since = "4.0.0")
public class MetadataBuilder {

    private String name;
    private String description;
    private String unit;

    MetadataBuilder(Metadata meta) {
        this.name = meta.name();
        this.description = meta.description();
        this.unit = meta.unit();
    }

    MetadataBuilder(Meter meter) {
        name = meter.id().name();
        description = meter.description();
        unit = meter.baseUnit();
    }

    MetadataBuilder() {
    }

    /**
     * Sets the name in the builder.
     *
     * @param name name to store in metadata
     * @return updated builder
     */
    public MetadataBuilder withName(String name) {
        this.name = name;
        return this;
    }

    /**
     * Sets the description in the builder.
     *
     * @param description description to store in metadata
     * @return updated builder
     */
    public MetadataBuilder withDescription(String description) {
        this.description = description;
        return this;
    }

    /**
     * Sets the unit for the builder.
     *
     * @param unit unit for the meter
     * @return updated builder
     */
    public MetadataBuilder withUnit(String unit) {
        this.unit = unit;
        return this;
    }

    /**
     * Creates a new {@link io.helidon.metrics.api.Metadata} instance from the values currently set in the builder.
     *
     * @return new metadata instance
     */
    public Metadata build() {
        return new Metadata(name, description, unit);
    }
}
