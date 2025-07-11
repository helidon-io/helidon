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

package io.helidon.webserver.http.spi;

import io.helidon.common.concurrency.limits.spi.LimitAlgorithmListener;
import io.helidon.http.Headers;
import io.helidon.http.HttpPrologue;
import io.helidon.service.registry.Service;

/**
 * Provider for {@linkplain io.helidon.common.concurrency.limits.spi.LimitAlgorithmListener concurrency limit listeners} for
 * HTTP 1 or 2 requests.
 */
@Service.Contract
public interface HttpLimitListenerProvider {

    /**
     * Creates a listener using the provided prolog and headers.
     *
     * @param prologue {@link io.helidon.http.HttpPrologue} being processed
     * @param headers {@link io.helidon.http.Headers} being processed
     *
     * @return new listener
     */
    LimitAlgorithmListener create(HttpPrologue prologue, Headers headers);

}


