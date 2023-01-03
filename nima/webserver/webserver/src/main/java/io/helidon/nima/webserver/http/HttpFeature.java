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

package io.helidon.nima.webserver.http;

import java.util.function.Supplier;

import io.helidon.nima.webserver.ServerLifecycle;

/**
 * Can be registered with {@link io.helidon.nima.webserver.http.HttpRouting.Builder#addFeature(java.util.function.Supplier)}.
 * Encapsulates a set of endpoints, services and/or filters.
 * <p>
 * Feature is similar to {@link io.helidon.nima.webserver.http.HttpService} but gives more freedom in setup.
 * Main difference is that a feature can add {@link io.helidon.nima.webserver.http.Filter Filters} and it cannot be
 * registered on a path (that is left to the discretion of the feature developer).
 * <p>
 * Features are not registered immediately - each feature can define a {@link io.helidon.common.Weight} or implement
 * {@link io.helidon.common.Weighted} to order features according to their weight. Higher weighted features are registered first.
 * This is to allow ordering of features in a meaningful way (e.g. Context should be first, Tracing second, Security third etc.).
 */
public interface HttpFeature extends Supplier<HttpFeature>, ServerLifecycle {
    @Override
    default HttpFeature get() {
        // this is here to allow methods that accept both an instance and a builder
        return this;
    }

    /**
     * Method to set up a feature.
     * @param routing routing builder
     */
    void setup(HttpRouting.Builder routing);
}
