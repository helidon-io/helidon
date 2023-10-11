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

package io.helidon.webserver.observe.info;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import io.helidon.builder.api.RuntimeType;
import io.helidon.http.HttpException;
import io.helidon.http.Status;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.observe.spi.Observer;
import io.helidon.webserver.spi.ServerFeature;

/**
 * Observer for application information.
 */
@RuntimeType.PrototypedBy(InfoObserverConfig.class)
public class InfoObserver implements Observer, RuntimeType.Api<InfoObserverConfig> {
    private final InfoObserverConfig config;

    private InfoObserver(InfoObserverConfig config) {
        this.config = config;
    }

    /**
     * Create a new builder to configure Info observer.
     *
     * @return a new builder
     */
    public static InfoObserverConfig.Builder builder() {
        return InfoObserverConfig.builder();
    }

    /**
     * Create a new Info observer using the provided configuration.
     *
     * @param config configuration
     * @return a new observer
     */
    public static InfoObserver create(InfoObserverConfig config) {
        return new InfoObserver(config);
    }

    /**
     * Create a new Info observer customizing its configuration.
     *
     * @param consumer configuration consumer
     * @return a new observer
     */
    public static InfoObserver create(Consumer<InfoObserverConfig.Builder> consumer) {
        return builder()
                .update(consumer)
                .build();
    }

    /**
     * Create a new Info observer with default configuration.
     *
     * @return a new observer
     */
    public static InfoObserver create() {
        return builder()
                .build();
    }

    @Override
    public InfoObserverConfig prototype() {
        return config;
    }

    @Override
    public String type() {
        return "info";
    }

    @Override
    public void register(ServerFeature.ServerFeatureContext featureContext,
                         List<HttpRouting.Builder> observeEndpointRouting,
                         UnaryOperator<String> endpointFunction) {
        String endpoint = endpointFunction.apply(config.endpoint());

        if (config.enabled()) {
            for (HttpRouting.Builder routing : observeEndpointRouting) {
                // register the service itself
                routing.register(endpoint, new InfoService(this.config.values()));
            }
        } else {
            for (HttpRouting.Builder builder : observeEndpointRouting) {
                builder.any(endpoint + "/*", (req, res) -> {
                    throw new HttpException("Info endpoint is disabled", Status.SERVICE_UNAVAILABLE_503, true);
                });
            }
        }
    }
}
