/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.webserver.concurrency.limits;

import java.util.Optional;

import io.helidon.common.Weighted;
import io.helidon.common.concurrency.limits.Limit;
import io.helidon.common.concurrency.limits.LimitAlgorithm;
import io.helidon.http.HttpException;
import io.helidon.http.Status;
import io.helidon.webserver.http.FilterChain;
import io.helidon.webserver.http.HttpFeature;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http.RoutingRequest;
import io.helidon.webserver.http.RoutingResponse;

class LimitsRoutingFeature implements HttpFeature, Weighted {
    private final double featureWeight;
    private final Limit limits;
    private final boolean enabled;

    LimitsRoutingFeature(LimitsFeatureConfig config, double featureWeight) {
        this.featureWeight = featureWeight;
        this.limits = config.concurrencyLimit().orElse(null);
        this.enabled = config.enabled();
    }

    @Override
    public void setup(HttpRouting.Builder builder) {
        if (enabled && limits != null) {
            builder.addFilter(this::filter);
        }
    }

    @Override
    public double weight() {
        return featureWeight;
    }

    private void filter(FilterChain chain, RoutingRequest req, RoutingResponse res) {
        Optional<LimitAlgorithm.Token> token = limits.tryAcquire();

        if (token.isEmpty()) {
            throw new HttpException("Limit exceeded", Status.SERVICE_UNAVAILABLE_503);
        }

        LimitAlgorithm.Token permit = token.get();
        try {
            chain.proceed();
            permit.success();
        } catch (Throwable e) {
            permit.dropped();
            throw e;
        }
    }
}
