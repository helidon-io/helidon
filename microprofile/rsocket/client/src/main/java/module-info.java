/*
 * Copyright (c) 2021 Oracle and/or its affiliates. All rights reserved.
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

/**
 * RSocket client integration.
 */
module io.helidon.microprofile.rsocket.client {
    requires java.logging;

    requires static jakarta.enterprise.cdi.api;
    requires static jakarta.inject.api;

    requires io.helidon.common.mapper;
    requires io.helidon.common.reactive;
    requires rsocket.core;
    requires reactor.core;
    requires io.netty.buffer;
    requires org.reactivestreams;
    requires jakarta.interceptor.api;
    requires io.helidon.rsocket.client;
    requires io.helidon.config;
    requires microprofile.config.api;
    requires io.helidon.config.mp;

    exports io.helidon.microprofile.rsocket.client;

}
