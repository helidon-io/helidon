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
package io.helidon.metrics.api;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.common.media.type.MediaType;

/**
 * Output format, meter selections, and optional hints for a meter registry formatter.
 * Selection collections, including accepted tag values, are immutable snapshots taken when the context is built.
 *
 * @see io.helidon.metrics.spi.MeterRegistryFormatterProvider#formatter(FormatterContext, MeterRegistry)
 * @since 28.0.0
 */
@Prototype.Blueprint(decorator = FormatterContextSupport.BuilderDecorator.class)
@Prototype.CustomMethods(FormatterContextSupport.class)
interface FormatterContextBlueprint {

    /**
     * Media type of the desired output.
     *
     * @return required media type
     */
    @Option.Required
    MediaType mediaType();

    /**
     * Metrics configuration to influence the formatting.
     *
     * @return required metrics configuration
     */
    @Option.Required
    MetricsConfig metricsConfig();

    /**
     * Tag names and accepted values to format.
     * An empty map, the default, means no tag-based restriction.
     *
     * @return immutable tag selections
     */
    @Option.Singular
    Map<String, Collection<String>> tagSelections();

    /**
     * Meter names to format.
     * An empty list, the default, means no name-based restriction.
     *
     * @return immutable meter-name selection
     */
    @Option.Singular("nameSelection")
    List<String> nameSelection();

    /**
     * Optional hint to include quantiles alongside histogram buckets for classic Prometheus consumers.
     * Providers may ignore the hint if their output format or implementation does not support it.
     *
     * @return whether to request histogram quantiles; defaults to {@code false}
     */
    @Option.DefaultBoolean(false)
    boolean includeHistogramQuantiles();
}
