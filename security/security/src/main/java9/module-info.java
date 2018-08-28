/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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
 * Java for cloud security module.
 *
 * @see io.helidon.security.Security
 * @see io.helidon.security.SecurityContext
 */
module io.helidon.security {
    requires java.logging;

    requires transitive io.helidon.common;
    requires transitive io.helidon.common.reactive;
    requires transitive io.helidon.config;
    requires transitive opentracing.api;
    // noop and api expose the same package :(
    // requires opentracing.noop;
    requires opentracing.util;

    requires io.helidon.security.util;
    requires opentracing.noop;

    exports io.helidon.security;
    exports io.helidon.security.spi;

    exports io.helidon.security.internal to io.helidon.security.adapter.jersey, io.helidon.security.adapter.webserver;

    uses io.helidon.security.spi.SecurityProviderService;
}
