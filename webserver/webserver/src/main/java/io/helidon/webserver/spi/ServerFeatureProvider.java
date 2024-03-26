/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

package io.helidon.webserver.spi;

import io.helidon.common.config.ConfiguredProvider;

/**
 * Server features provider is a {@link java.util.ServiceLoader} provider API to discover server wide features.
 * <p>
 * When creating a custom feature, one of the aspects important for how it is configured with the server, and with routing,
 * is the {@link io.helidon.common.Weight}.
 * <p>
 * There are three places where weight is taken into account:
 * <ol>
 *     <li>When discovering server features from the service loader, the weight of this provider implementation is used</li>
 *     <li>When setting up server features within the server (and as a default for {@link io.helidon.webserver.http.HttpFeature}
 *     added by a server feature), the {@link io.helidon.common.Weight} or {@link io.helidon.common.Weighted} of the
 *     {@link io.helidon.webserver.spi.ServerFeature} implementation is used to order the features</li>
 *     <li>When configuring {@link io.helidon.webserver.http.HttpFeature} from a server feature, the weight of that
 *     implementation can override the default from the server feature, and the weight is used to order all HTTP features,
 *     including user routing (which has the default weight of {@value io.helidon.common.Weighted#DEFAULT_WEIGHT})</li>
 * </ol>
 * The following weights are used by features of Helidon:
 * <ul>
 *     <li>Context: 1100</li>
 *     <li>Access Log: 1000</li>
 *     <li>Tracing: 900</li>
 *     <li>CORS: 950</li>
 *     <li>Security: 800</li>
 *     <li>User routing (all handlers): 100</li>
 *     <li>OpenAPI: 90</li>
 *     <li>Observe (metrics, health, etc.): 80</li>
 * </ul>
 * For some features the weight can be modified using configuration, key is always {@code weight}.
 *
 * @param <T> type of the server feature the provider provides
 */
public interface ServerFeatureProvider<T extends ServerFeature> extends ConfiguredProvider<T> {
}
