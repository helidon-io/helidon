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

import io.helidon.microprofile.rsocket.server.RSocketCdiExtension;

/**
 * RSocket server integration.
 */
module io.helidon.microprofile.rsocket.server {
    requires java.logging;

    requires static jakarta.enterprise.cdi.api;
    requires static jakarta.inject.api;
    requires static jakarta.activation;

    requires io.helidon.common.context;
    requires io.helidon.common.mapper;
    requires io.helidon.common.reactive;
    requires rsocket.core;
    requires reactor.core;
    requires io.netty.buffer;
    requires org.reactivestreams;
    requires io.helidon.webserver.tyrus;
    requires tyrus.spi;
    requires io.helidon.microprofile.server;
    requires jakarta.interceptor.api;
    requires io.helidon.rsocket.server;

    exports io.helidon.microprofile.rsocket.server;

    provides javax.enterprise.inject.spi.Extension with RSocketCdiExtension;
}
