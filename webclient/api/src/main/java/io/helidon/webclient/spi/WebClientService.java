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

package io.helidon.webclient.spi;


import io.helidon.common.config.NamedService;
import io.helidon.webclient.api.WebClientServiceRequest;
import io.helidon.webclient.api.WebClientServiceResponse;

/**
 * Extension that can modify web client behavior.
 * This is now only designed for HTTP/1
 */
@FunctionalInterface
public interface WebClientService extends NamedService {
    @Override
    default String name() {
        return type();
    }

    @Override
    default String type() {
        return "inlined-service";
    }

    /**
     * Invoke a service, call {@link Chain#proceed(io.helidon.webclient.api.WebClientServiceRequest)} to call the
     * next service in the chain.
     *
     * @param chain to invoke next web client service, or the HTTP call if this is the last service
     * @param clientRequest request from the client, or previous services
     * @return response to be returned to the client
     */
    WebClientServiceResponse handle(Chain chain, WebClientServiceRequest clientRequest);

    /**
     * Chain of services.
     */
    interface Chain {
        /**
         * Proceed with invocation of the next service, or the HTTP call.
         * This method is always fully blocking.
         *
         * @param clientRequest request
         * @return response from the next service or HTTP call
         */
        WebClientServiceResponse proceed(WebClientServiceRequest clientRequest);
    }
}
