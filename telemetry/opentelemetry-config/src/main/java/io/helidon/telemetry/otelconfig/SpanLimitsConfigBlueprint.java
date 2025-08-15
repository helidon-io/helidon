/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

import java.util.Optional;

import io.helidon.builder.api.Prototype;

/**
 * Tracing span limits settings.
 *
 * @see io.opentelemetry.sdk.trace.SpanLimitsBuilder
 */
@Prototype.Blueprint
@Prototype.Configured
interface SpanLimitsConfigBlueprint {

    /**
     * Maximum number of attributes.
     *
     * @return max attributes
     */
    Optional<Integer> maxAttributes();

    /**
     * Maximum number of events.
     *
     * @return max events
     */
    Optional<Integer> maxEvents();

    /**
     * Maximum number of links.
     *
     * @return max links
     */
    Optional<Integer> maxLinks();

    /**
     * Maximum number of attributes per event.
     *
     * @return max attributes per event
     */
    Optional<Integer> maxAttributesPerEvent();

    /**
     * Maximum number of attributes per link.
     *
     * @return max attributes per link
     */
    Optional<Integer> maxAttributesPerLink();

    /**
     * Maximum attribute value length.
     *
     * @return max attribute value length
     */
    Optional<Integer> maxAttributeValueLength();

}
