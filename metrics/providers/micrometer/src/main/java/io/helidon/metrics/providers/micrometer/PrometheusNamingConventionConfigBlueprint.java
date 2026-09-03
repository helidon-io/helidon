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

package io.helidon.metrics.providers.micrometer;

import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

/**
 * Settings controlling Prometheus naming conventions.
 */
@Prototype.Configured
@Prototype.Blueprint
interface PrometheusNamingConventionConfigBlueprint {

    /**
     * Suffix which identifies a timer name before Prometheus adds {@code _seconds}.
     *
     * @return timer suffix
     */
    @Option.Configured
    @Option.Default("")
    String timerSuffix();

    /**
     * Prefix to add to metric names and tag keys which do not begin with a letter; configuring this setting enables
     * legacy simpleclient-compatible normalization, with {@code m_} reproducing the naming from earlier Helidon releases,
     * and preserves user-supplied reserved suffixes such as {@code _total}, {@code _created}, {@code _bucket}, and
     * {@code _info}, whereas leaving it unset uses the new Prometheus client's normalization.
     *
     * @return non-letter prefix
     */
    @Option.Configured
    Optional<String> nonLetterPrefix();
}
