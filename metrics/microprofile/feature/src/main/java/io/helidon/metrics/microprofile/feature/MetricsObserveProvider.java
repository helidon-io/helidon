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
package io.helidon.metrics.microprofile.feature;

import io.helidon.common.http.Http;
import io.helidon.config.Config;
import io.helidon.nima.observe.spi.ObserveProvider;
import io.helidon.nima.webserver.http.HttpRouting;

public class MetricsObserveProvider implements ObserveProvider {

    private final MpMetricsFeature feature;

    /**
     * Do not use - required by service loader.
     *
     * @deprecated use {@link #create}
     */
    @Deprecated
    public MetricsObserveProvider() {
        this(null);
    }

    private MetricsObserveProvider(MpMetricsFeature feature) {
        this.feature = feature;
    }

    public static ObserveProvider create() {
        return create(MpMetricsFeature.create());
    }

    public static ObserveProvider create(MpMetricsFeature feature) {
        return new MetricsObserveProvider(feature);
    }

    @Override
    public String configKey() {
        return "mp.metrics";
    }

    @Override
    public String defaultEndpoint() {
        return feature == null ? "metrics" : feature.configuredContext();
    }

    @Override
    public void register(Config config, String componentPath, HttpRouting.Builder routing) {
        MpMetricsFeature observer = feature == null
                ? MpMetricsFeature.builder().webContext(componentPath)
                        .config(config)
                        .build()
                : feature;

        if (observer.enabled()) {
            observer.context(componentPath);
            routing.addFeature(observer);
        } else {
            routing.get(componentPath + "/*", (req, resp) -> resp.status(Http.Status.SERVICE_UNAVAILABLE_503)
                    .send());
        }
    }
}
