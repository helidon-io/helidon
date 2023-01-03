/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.integrations.micrometer.nima;

import java.util.Optional;
import java.util.function.Function;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.Config;
import io.helidon.config.ConfigValue;
import io.helidon.integrations.micrometer.BuiltInRegistryType;
import io.helidon.integrations.micrometer.MicrometerPrometheusRegistrySupport;
import io.helidon.nima.webserver.http.Handler;
import io.helidon.nima.webserver.http.ServerRequest;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.config.MeterRegistryConfig;
import io.micrometer.prometheus.PrometheusConfig;

class NimaMicrometerPrometheusRegistrySupport extends MicrometerPrometheusRegistrySupport<ServerRequest, Handler> {

    NimaMicrometerPrometheusRegistrySupport(MeterRegistryConfig meterRegistryConfig) {
        super(meterRegistryConfig);
    }

    @Override
    public Function<ServerRequest, Optional<Handler>> requestToHandlerFn(MeterRegistry registry) {
        /*
         * Deal with a request if the MediaType is text/plain or the query parameter "type" specifies "prometheus".
         */
        return (ServerRequest req) -> {
            if (req.headers()
                    .bestAccepted(MediaTypes.TEXT_PLAIN).isPresent()
                    || req.query()
                    .first("type")
                    .orElse("")
                    .equals("prometheus")) {
                return Optional.of(NimaPrometheusHandler.create(registry));
            } else {
                return Optional.empty();
            }
        };
    }

    static NimaMicrometerPrometheusRegistrySupport create(BuiltInRegistryType type,
            ConfigValue<Config> node) {
        switch (type) {
            case PROMETHEUS:
                return create(type, MicrometerPrometheusRegistrySupport.PrometheusConfigImpl.registryConfig(node));

            default:
                throw new IllegalArgumentException(unrecognizedMessage(type));
        }
    }

    static NimaMicrometerPrometheusRegistrySupport create(BuiltInRegistryType type,
            MeterRegistryConfig meterRegistryConfig) {
        switch (type) {
            case PROMETHEUS:
                return new NimaMicrometerPrometheusRegistrySupport(meterRegistryConfig);

            default:
                throw new IllegalArgumentException(unrecognizedMessage(type));
        }
    }

    static NimaMicrometerPrometheusRegistrySupport create(BuiltInRegistryType type) {
        MeterRegistryConfig meterRegistryConfig;
        switch (type) {
            case PROMETHEUS:
                meterRegistryConfig = PrometheusConfig.DEFAULT;
                break;

            default:
                throw new IllegalArgumentException(unrecognizedMessage(type));
        }
        return create(type, meterRegistryConfig);
    }
}
