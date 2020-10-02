/*
 * Copyright (c) 2017, 2020 Oracle and/or its affiliates.
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

package io.helidon.metrics.prometheus;

import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

import io.helidon.common.http.MediaType;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.Service;

import io.prometheus.client.Collector;
import io.prometheus.client.CollectorRegistry;

/**
 * Support for Prometheus client endpoint.
 * <p>
 * Default and simplest use on {@link Routing} creates {@code /metrics} endpoint
 * for {@link CollectorRegistry default CollectorRegistry}.
 * <pre>{@code
 * Routing.builder()
 *        .register(PrometheusSupport.create())
 * }</pre>
 * <p>
 * It is possible to use
 */
public final class PrometheusSupport implements Service {

    /**
     * Standard path of Prometheus client resource: {@code /metrics}.
     */
    private static final String DEFAULT_PATH = "/metrics";

    private static final MediaType CONTENT_TYPE = MediaType.parse("text/plain; version=0.0.4; charset=utf-8");

    private final CollectorRegistry collectorRegistry;
    private final String path;

    private PrometheusSupport(CollectorRegistry collectorRegistry, String path) {
        this.collectorRegistry = collectorRegistry == null ? CollectorRegistry.defaultRegistry : collectorRegistry;
        this.path = path == null ? DEFAULT_PATH : path;
    }

    @Override
    public void update(Routing.Rules rules) {
        rules.get(path, this::process);
    }

    private void process(ServerRequest req, ServerResponse res) {
        Set<String> filters = new HashSet<>(req.queryParams().all("name[]"));
        Enumeration<Collector.MetricFamilySamples> mfs = collectorRegistry.filteredMetricFamilySamples(filters);
        res.headers().contentType(CONTENT_TYPE);
        res.send(compose(mfs));
    }

    /**
     * Compose the text version 0.0.4 of the given MetricFamilySamples.
     */
    private static String compose(Enumeration<Collector.MetricFamilySamples> mfs) {
        /* See http://prometheus.io/docs/instrumenting/exposition_formats/
         * for the output format specification. */
        StringBuilder result = new StringBuilder();
        while (mfs.hasMoreElements()) {
            Collector.MetricFamilySamples metricFamilySamples = mfs.nextElement();
            result.append("# HELP ")
                  .append(metricFamilySamples.name)
                  .append(' ');
            appendEscapedHelp(result, metricFamilySamples.help);
            result.append('\n');

            result.append("# TYPE ")
                  .append(metricFamilySamples.name)
                  .append(' ')
                  .append(typeString(metricFamilySamples.type))
                  .append('\n');

            for (Collector.MetricFamilySamples.Sample sample: metricFamilySamples.samples) {
                result.append(sample.name);
                if (!sample.labelNames.isEmpty()) {
                    result.append('{');
                    for (int i = 0; i < sample.labelNames.size(); ++i) {
                        result.append(sample.labelNames.get(i))
                              .append("=\"");
                        appendEscapedLabelValue(result, sample.labelValues.get(i));
                        result.append("\",");
                    }
                    result.append('}');
                }
                result.append(' ')
                      .append(Collector.doubleToGoString(sample.value))
                      .append('\n');
            }
        }
        return result.toString();
    }

    private static void appendEscapedHelp(StringBuilder sb, String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
            case '\\':
                sb.append("\\\\");
                break;
            case '\n':
                sb.append("\\n");
                break;
            default:
                sb.append(c);
            }
        }
    }

    private static void appendEscapedLabelValue(StringBuilder sb, String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
            case '\\':
                sb.append("\\\\");
                break;
            case '\"':
                sb.append("\\\"");
                break;
            case '\n':
                sb.append("\\n");
                break;
            default:
                sb.append(c);
            }
        }
    }

    private static String typeString(Collector.Type t) {
        switch (t) {
        case GAUGE:
            return "gauge";
        case COUNTER:
            return "counter";
        case SUMMARY:
            return "summary";
        case HISTOGRAM:
            return "histogram";
        default:
            return "untyped";
        }
    }

    /**
     * Creates new instance using specified Prometheus {@link CollectorRegistry}.
     *
     * @param collectorRegistry a registry to use
     * @return new instance
     * @see #create()
     * @see #builder()
     */
    public static PrometheusSupport create(CollectorRegistry collectorRegistry) {
        return new PrometheusSupport(collectorRegistry, DEFAULT_PATH);
    }

    /**
     * Creates new instance using default Prometheus {@link CollectorRegistry}.
     *
     * @return new instance
     * @see CollectorRegistry
     * @see #create(CollectorRegistry)
     * @see #builder()
     */
    public static PrometheusSupport create() {
        return create(null);
    }

    /**
     * Creates new {@code Builder} instance.
     *
     * @return the new instance
     * @see #create()
     * @see #create(CollectorRegistry)
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * A builder of {@link PrometheusSupport}.
     */
    public static final class Builder implements io.helidon.common.Builder<PrometheusSupport> {

        private CollectorRegistry registry = CollectorRegistry.defaultRegistry;
        private String path;

        private Builder() {
        }

        /**
         * Sets collector registry to use, default is {@link CollectorRegistry#defaultRegistry}.
         *
         * @param registry a registry to use
         * @return updated builder
         */
        public Builder collectorRegistry(CollectorRegistry registry) {
            this.registry = registry;
            return this;
        }

        /**
         * Sets path of the metrics resource, default is {@code /metrics}.
         *
         * @param path a resource path
         * @return updated builder
         */
        public Builder path(String path) {
            if (path == null || path.isEmpty()) {
                this.path = "/";
            } else {
                this.path = path;
            }
            return this;
        }

        @Override
        public PrometheusSupport build() {
            return new PrometheusSupport(registry, path == null ? DEFAULT_PATH : path);
        }
    }
}
