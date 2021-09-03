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

/**
 * Helidon WebClient.
 */
module io.helidon.webclient {

    requires java.logging;
    
    requires transitive io.helidon.common;
    requires transitive io.helidon.common.context;
    requires transitive io.helidon.common.http;
    requires transitive io.helidon.config;
    requires transitive io.helidon.media.common;
    requires io.helidon.common.pki;
    requires io.helidon.common.serviceloader;

    requires io.netty.buffer;
    requires io.netty.codec;
    requires io.netty.codec.http;
    requires io.netty.common;
    requires io.netty.handler;
    requires io.netty.handler.proxy;
    requires io.netty.transport;

    exports io.helidon.webclient;
    exports io.helidon.webclient.spi;

    uses io.helidon.webclient.spi.WebClientServiceProvider;

}