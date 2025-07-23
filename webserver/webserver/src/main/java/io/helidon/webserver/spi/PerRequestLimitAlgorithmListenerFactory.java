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

package io.helidon.webserver.spi;

import io.helidon.common.concurrency.limits.Limit;
import io.helidon.common.concurrency.limits.spi.LimitAlgorithmListener;
import io.helidon.http.Headers;
import io.helidon.http.HttpPrologue;
import io.helidon.service.registry.Service;

/**
 * Behavior common to factories creating per-requests listeners for limit algorithm events.
 */
@Service.Contract
public interface PerRequestLimitAlgorithmListenerFactory {

    /**
     * {@link io.helidon.common.concurrency.limits.Limit} active on the socket listener.
     *
     * @param limit the {@code Limit}
     */
    void init(Limit limit);

    /**
     * Returns {@link io.helidon.common.concurrency.limits.spi.LimitAlgorithmListener} instances based on the provided prologue
     * and headers.
     *
     * @param prologue {@link io.helidon.http.HttpPrologue} for the incoming request
     * @param headers {@link io.helidon.http.Headers} for the incoming request
     *
     * @return limit algorithm listeners from this provider
     */
    Iterable<LimitAlgorithmListener> listeners(HttpPrologue prologue, Headers headers);

}
