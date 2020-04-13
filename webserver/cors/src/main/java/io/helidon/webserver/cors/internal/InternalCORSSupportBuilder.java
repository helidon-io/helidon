/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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
 *
 */
package io.helidon.webserver.cors.internal;

import java.util.Optional;
import java.util.function.Supplier;

import io.helidon.webserver.cors.CORSSupport;
import io.helidon.webserver.cors.CrossOriginConfig;

/**
 * <em>Not for developer user.</em> Creates a {@code CORSSupport.Builder} that knows about the secondary look-up supplier. Used
 * from MP CORS.
 *
 * @param <T> type of the request wrapped by the adapter
 * @param <U> type of the response wrapped by the adapter
 */
public class InternalCORSSupportBuilder<T, U> extends CORSSupport.Builder<T, U,
        InternalCORSSupportBuilder<T, U>> {

    /**
     * Creates a new instance.
     *
     * @param <T> type of the request wrapped by the adapter
     * @param <U> type of the response wrapped by the adapter
     * @return the new builder
     */
    public static <T, U> InternalCORSSupportBuilder<T, U> create() {
        return new InternalCORSSupportBuilder<>();
    }

    InternalCORSSupportBuilder() {
    }

    /**
     * <em>Not for developer use.</em> Sets a back-up way to provide a {@code CrossOriginConfig} instance if, during
     * look-up for a given request, none is found from the aggregator.
     *
     * @param secondaryLookupSupplier supplier of a CrossOriginConfig
     * @return updated builder
     */
    public InternalCORSSupportBuilder<T, U> secondaryLookupSupplier(
            Supplier<Optional<CrossOriginConfig>> secondaryLookupSupplier) {
        helperBuilder().secondaryLookupSupplier(secondaryLookupSupplier);
        return this;
    }
}
