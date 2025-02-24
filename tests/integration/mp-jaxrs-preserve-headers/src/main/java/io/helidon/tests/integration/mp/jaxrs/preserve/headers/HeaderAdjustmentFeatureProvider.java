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
package io.helidon.tests.integration.mp.jaxrs.preserve.headers;

import io.helidon.common.Weight;
import io.helidon.config.Config;
import io.helidon.http.Header;
import io.helidon.http.HeaderValues;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.HttpFeature;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.observe.health.HealthObserverConfig;
import io.helidon.webserver.spi.ServerFeature;
import io.helidon.webserver.spi.ServerFeatureProvider;

/**
 * Server feature to insert headers for the health responses using an SE filter. The header so added
 * is removed by JaxRsService#doHandle without the fix to save headers and, if Jersey does not handle the
 * request, restoring the saved headers before handing the request off to SE to see if it can deal with it.
 */
@Weight(HeaderAdjustmentFeatureProvider.WEIGHT)
public class HeaderAdjustmentFeatureProvider
        implements ServerFeatureProvider<HeaderAdjustmentFeatureProvider.HeaderAdjustmentFeature> {

    static final Header ADDED_HEADER = HeaderValues.create("X-Helidon-Test", "example");

    static final double WEIGHT = 90D;

    @Override
    public String configKey() {
        return "header-adjustment";
    }

    @Override
    public HeaderAdjustmentFeature create(io.helidon.common.config.Config config, String name) {
        return new HeaderAdjustmentFeature();
    }

    @Weight(WEIGHT)
    public static class HeaderAdjustmentFeature implements ServerFeature {

        public HeaderAdjustmentFeature() {
        }

        @Override
        public void setup(ServerFeatureContext serverFeatureContext) {
            serverFeatureContext.socket(WebServer.DEFAULT_SOCKET_NAME)
                    .httpRouting()
                    .addFeature(new HeaderAdjustmentHttpFeature(Config.global()));
        }

        @Override
        public String name() {
            return "header-adjustment";
        }

        @Override
        public String type() {
            return "header-adjustment";
        }

        @Weight(WEIGHT)
        private static class HeaderAdjustmentHttpFeature implements HttpFeature {

            private final io.helidon.common.config.Config config;

            private HeaderAdjustmentHttpFeature(io.helidon.common.config.Config config) {
                this.config = config;
            }

            @Override
            public void setup(HttpRouting.Builder builder) {

                HealthObserverConfig healthObserverConfig =
                        HealthObserverConfig.create(config.root().get("server.features.observe.observers.health"));
                builder.addFilter((chain, req, resp) -> {
                    if (req.path().path().endsWith(healthObserverConfig.endpoint())) {
                        resp.header(ADDED_HEADER);
                    }
                    chain.proceed();
                });
            }
        }
    }
}
