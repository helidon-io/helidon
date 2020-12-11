/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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
 *
 */
package io.helidon.metrics.micrometer;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Enumeration;
import java.util.Optional;
import java.util.function.Function;

import io.helidon.common.http.Http;
import io.helidon.common.http.MediaType;
import io.helidon.config.Config;
import io.helidon.config.ConfigValue;
import io.helidon.webserver.Handler;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.config.MeterRegistryConfig;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.prometheus.client.Collector;

/**
 * Support for built-in Prometheus meter registry type.
 */
class PrometheusRegistrySupport extends BuiltInRegistrySupport {

    static class PrometheusConfigImpl extends AbstractMeterRegistryConfig implements PrometheusConfig {

        static PrometheusConfig registryConfig(ConfigValue<Config> node) {
            return node
                    .filter(Config::exists)
                    .map(PrometheusConfigImpl::registryConfig)
                    .orElse(PrometheusConfig.DEFAULT);
        }

        private static PrometheusConfig registryConfig(Config config) {
            return new PrometheusConfigImpl(config);
        }

        PrometheusConfigImpl(Config config) {
            super(config);
        }
    }

    PrometheusRegistrySupport(MeterRegistryConfig meterRegistryConfig) {
        super(meterRegistryConfig);
    }

    @Override
    PrometheusMeterRegistry createRegistry(MeterRegistryConfig meterRegistryConfig) {
        return new PrometheusMeterRegistry(PrometheusConfig.class.cast(meterRegistryConfig));
    }

    @Override
    public Function<ServerRequest, Optional<Handler>> requestToHandlerFn(MeterRegistry registry) {
        /*
         * Deal with a request if the MediaType is text/plain or the query parameter "type" specifies "prometheus".
         */
        return (ServerRequest req) -> {
            if (req.headers()
                    .acceptedTypes()
                    .contains(MediaType.TEXT_PLAIN)
                    || req.queryParams()
                    .first("type")
                    .orElse("")
                    .equals("prometheus")) {
                return Optional.of(PrometheusHandler.create(registry));
            } else {
                return Optional.empty();
            }
        };
    }

    /**
     * Handler for dealing with HTTP requests to the Micrometer endpoint that specify prometheus as the registry type.
     */
    static class PrometheusHandler implements Handler {

        private final PrometheusMeterRegistry registry;

        private PrometheusHandler(PrometheusMeterRegistry registry) {
            this.registry = registry;
        }

        static PrometheusHandler create(MeterRegistry registry) {
            return new PrometheusHandler(PrometheusMeterRegistry.class.cast(registry));
        }

        @Override
        public void accept(ServerRequest req, ServerResponse res) {
            res.headers().contentType(MediaType.TEXT_PLAIN);
            switch (Http.Method.valueOf(req.method()
                    .name())) {
                case GET:
                    res.send(registry.scrape());
                    break;
                case OPTIONS:
                    StringWriter writer = new StringWriter();
                    try {
                        metadata(writer, registry);
                        res.send(writer.toString());
                    } catch (IOException e) {
                        res.status(Http.Status.INTERNAL_SERVER_ERROR_500)
                                .send(e);
                    }
                    break;
                default:
                    res.status(Http.Status.NOT_IMPLEMENTED_501)
                            .send();
            }
        }
    }

    static void metadata(Writer writer, PrometheusMeterRegistry registry) throws IOException {
        Enumeration<Collector.MetricFamilySamples> mfs = registry.getPrometheusRegistry()
                .metricFamilySamples();
        while (mfs.hasMoreElements()) {
            Collector.MetricFamilySamples metricFamilySamples = mfs.nextElement();
            writer.write("# HELP ");
            writer.write(metricFamilySamples.name);
            writer.write(' ');
            writeEscapedHelp(writer, metricFamilySamples.help);
            writer.write('\n');

            writer.write("# TYPE ");
            writer.write(metricFamilySamples.name);
            writer.write(' ');
            writer.write(typeString(metricFamilySamples.type));
            writer.write('\n');
        }
    }

    private static void writeEscapedHelp(Writer writer, String s) throws IOException {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\':
                    writer.append("\\\\");
                    break;
                case '\n':
                    writer.append("\\n");
                    break;
                default:
                    writer.append(c);
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
}
