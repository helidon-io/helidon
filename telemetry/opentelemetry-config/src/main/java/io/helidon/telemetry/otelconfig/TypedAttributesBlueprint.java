/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.telemetry.otelconfig;

import java.util.Map;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

/**
 * Abstraction of typed attributes settable on OpenTelemetry elements.
 */
@Prototype.Blueprint
@Prototype.Configured
interface TypedAttributesBlueprint {

    /**
     * String attributes.
     *
     * @return string attributes
     */
    @Option.Configured("strings")
    @Option.Singular
    Map<String, String> stringAttributes();

    /**
     * Boolean attributes.
     *
     * @return boolean attributes
     */
    @Option.Configured("booleans")
    @Option.Singular
    Map<String, Boolean> booleanAttributes();

    /**
     * Long attributes.
     *
     * @return long attributes
     */
    @Option.Configured("longs")
    @Option.Singular
    Map<String, Long> longAttributes();

    /**
     * Double attributes.
     *
     * @return double attributes
     */
    @Option.Configured("doubles")
    @Option.Singular
    Map<String, Double> doubleAttributes();

}
