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
 */
package io.helidon.webclient.spi;

import io.helidon.common.reactive.Single;
import io.helidon.webclient.WebClientRequestBuilder;
import io.helidon.webclient.WebClientServiceRequest;
import io.helidon.webclient.WebClientServiceResponse;

/**
 * Extension that can modify outgoing request.
 */
@FunctionalInterface
public interface WebClientService {

    /**
     * Method which is called before send actual request.
     *
     * @param request client service request
     * @return completion stage of the client service request
     */
    Single<WebClientServiceRequest> request(WebClientServiceRequest request);

    /**
     * Method which is called when the last byte of the response is processed.
     *
     * @param request client service request
     * @param response client service response
     * @return completion stage of the client service response
     */
    default Single<WebClientServiceResponse> response(WebClientRequestBuilder.ClientRequest request,
                                                               WebClientServiceResponse response) {
        return Single.just(response);
    }
}
