/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.integrations.micrometer.reactive;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.Config;
import io.helidon.config.ConfigValue;
import io.helidon.integrations.micrometer.BuiltInRegistryType;
import io.helidon.integrations.micrometer.MicrometerPrometheusRegistrySupport;
import io.helidon.reactive.webserver.Handler;
import io.helidon.reactive.webserver.ServerRequest;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.config.MeterRegistryConfig;
import io.micrometer.prometheus.PrometheusConfig;
import java.util.Optional;
import java.util.function.Function;

class ReactiveMicrometerPrometheusRegistrySupport extends MicrometerPrometheusRegistrySupport<ServerRequest, Handler> {

    ReactiveMicrometerPrometheusRegistrySupport(MeterRegistryConfig meterRegistryConfig) {
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
                    || req.queryParams()
                    .first("type")
                    .orElse("")
                    .equals("prometheus")) {
                return Optional.of(ReactivePrometheusHandler.create(registry));
            } else {
                return Optional.empty();
            }
        };
    }
    
    static ReactiveMicrometerPrometheusRegistrySupport create(BuiltInRegistryType type,
            ConfigValue<Config> node) {
        switch (type) {
            case PROMETHEUS:
                return create(type, MicrometerPrometheusRegistrySupport.PrometheusConfigImpl.registryConfig(node));

            default:
                throw new IllegalArgumentException(unrecognizedMessage(type));
        }
    }

    static ReactiveMicrometerPrometheusRegistrySupport create(BuiltInRegistryType type,
            MeterRegistryConfig meterRegistryConfig) {
        switch (type) {
            case PROMETHEUS:
                return new ReactiveMicrometerPrometheusRegistrySupport(meterRegistryConfig);

            default:
                throw new IllegalArgumentException(unrecognizedMessage(type));
        }
    }

    static ReactiveMicrometerPrometheusRegistrySupport create(BuiltInRegistryType type) {
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
