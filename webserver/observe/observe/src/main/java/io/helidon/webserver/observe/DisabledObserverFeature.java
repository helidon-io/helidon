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

package io.helidon.webserver.observe;

import io.helidon.http.HttpException;
import io.helidon.http.Status;
import io.helidon.webserver.http.HttpFeature;
import io.helidon.webserver.http.HttpRouting;

/**
 * An {@link io.helidon.webserver.http.HttpFeature} that marks endpoint as disabled.
 */
public class DisabledObserverFeature implements HttpFeature {
    private final String observerName;
    private final String endpoint;

    private DisabledObserverFeature(String observerName, String endpoint) {
        this.observerName = observerName;
        this.endpoint = endpoint;
    }

    /**
     * Create a new disabled feature. Any requests to the endpoint will end with
     * {@link io.helidon.http.Status#SERVICE_UNAVAILABLE_503}.
     *
     * @param observerName name of the observer, such as {@code Health}
     * @param endpoint endpoint of the observer, such as {@code /observe/health/*}
     * @return a new feature that returns unavailable for the endpoint for any method
     */
    public static DisabledObserverFeature create(String observerName, String endpoint) {
        return new DisabledObserverFeature(observerName, endpoint);
    }

    @Override
    public void setup(HttpRouting.Builder routing) {
        routing.any(endpoint, (req, res) -> {
            throw new HttpException(observerName + " endpoint is disabled", Status.SERVICE_UNAVAILABLE_503, true);
        });
    }
}
