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

package io.helidon.webclient.api;

import io.helidon.webclient.spi.WebClientService;

class ServiceChainImpl implements WebClientService.Chain {
    private final WebClientService.Chain next;
    private final WebClientService service;

    ServiceChainImpl(WebClientService.Chain next, WebClientService service) {
        this.next = next;
        this.service = service;
    }

    @Override
    public WebClientServiceResponse proceed(WebClientServiceRequest clientRequest) {
        return service.handle(next, clientRequest);
    }
}
