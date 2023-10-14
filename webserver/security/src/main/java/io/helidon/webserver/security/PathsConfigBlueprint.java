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

package io.helidon.webserver.security;

import java.util.List;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.common.config.Config;
import io.helidon.http.Method;
import io.helidon.webserver.WebServer;

@Prototype.Blueprint
@Prototype.Configured
interface PathsConfigBlueprint {
    @Prototype.FactoryMethod
    static Method createMethods(Config config) {
        return config.asString().map(Method::create).orElseThrow();
    }

    @Option.Configured
    @Option.Singular
    List<Method> methods();

    @Option.Configured
    String path();

    @Option.Configured
    @Option.Default(WebServer.DEFAULT_SOCKET_NAME)
    List<String> sockets();

    @Option.Configured(merge = true)
    SecurityHandler handler();
}
