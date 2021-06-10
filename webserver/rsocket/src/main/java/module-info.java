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
 * RSocket integration.
 */
module io.helidon.webserver.rsocket {
    requires java.logging;
    requires transitive jakarta.websocket.api;
    requires transitive java.annotation;
    requires transitive io.helidon.webserver;

    requires io.helidon.common.context;
    requires io.helidon.common.mapper;
    requires io.helidon.common.reactive;
    requires rsocket.core;
    requires reactor.core;
    requires io.netty.buffer;
    requires org.reactivestreams;
    requires io.helidon.webserver.tyrus;
    requires tyrus.spi;
    requires jakarta.enterprise.cdi.api;
    requires jakarta.interceptor.api;
    requires io.helidon.microprofile.server;

    exports io.helidon.webserver.rsocket;
}
