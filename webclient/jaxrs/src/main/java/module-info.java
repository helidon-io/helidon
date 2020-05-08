/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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

import org.glassfish.jersey.internal.spi.AutoDiscoverable;

/**
 * Basic integration with JAX-RS client.
 */
module io.helidon.webclient.jaxrs {
    requires java.logging;
    requires java.annotation;

    requires java.ws.rs;
    requires io.helidon.jersey.client;
    requires io.helidon.jersey.common;

    requires io.helidon.common;
    requires io.helidon.common.configurable;
    requires io.helidon.common.context;

    exports io.helidon.webclient.jaxrs;

    provides AutoDiscoverable with io.helidon.webclient.jaxrs.JerseyClientAutoDiscoverable;
}
