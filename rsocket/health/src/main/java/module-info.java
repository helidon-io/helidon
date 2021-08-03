/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
 * RSocket health support.
 */
module io.helidon.rsocket.health {
    requires java.logging;

    requires rsocket.core;
    requires reactor.core;
    requires io.netty.buffer;
    requires org.reactivestreams;
    requires io.helidon.common.reactive;
    requires io.helidon.common;
    requires io.helidon.config;
    requires jakarta.enterprise.cdi.api;
    requires microprofile.health.api;
    requires io.helidon.rsocket.server;


    exports io.helidon.rsocket.health;

    opens io.helidon.rsocket.health to weld.core.impl, io.helidon.microprofile.cdi;
}
