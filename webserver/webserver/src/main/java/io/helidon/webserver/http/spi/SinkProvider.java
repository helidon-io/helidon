/*
 * Copyright (c) 2023, 2026 Oracle and/or its affiliates.
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

import io.helidon.common.GenericType;
import io.helidon.webserver.http.ServerRequest;

/**
 * {@link java.util.ServiceLoader} provider interface for {@link Sink} providers.
 *
 * @param <T> event type
 */
public interface SinkProvider<T> {

    /**
     * Checks if a provider supports the type.
     *
     * @param type the type
     * @param request the current request
     * @return outcome of test
     */
    boolean supports(GenericType<? extends Sink<?>> type, ServerRequest request);

    /**
     * Creates a sink using this provider.
     *
     * @param context a context for a sync provider
     * @param <X> type of sink
     * @return newly created sink
     */
    <X extends Sink<T>> X create(SinkProviderContext context);
}
