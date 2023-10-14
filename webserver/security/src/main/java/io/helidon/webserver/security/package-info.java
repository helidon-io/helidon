/*
 * Copyright (c) 2018, 2023 Oracle and/or its affiliates.
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
 * Helidon WebServer Security Support.
 * <p>
 * Example of integration (expects an instance of {@link io.helidon.security.Security}):
 * <pre>
 * // WebServer routing builder - this is our integration point
 * {@link io.helidon.webserver.http.HttpRouting} routing = HttpRouting.builder()
 * // register the WebSecurity to create context (shared by all routes)
 * .register({@link io.helidon.webserver.security.SecurityHttpFeature}.{@link
 * io.helidon.webserver.security.SecurityHttpFeature#create(io.helidon.security.Security) from(security)})
 * // authenticate all paths under /user and require role "user"
 * .get("/user[/{*}]", WebSecurity.{@link io.helidon.webserver.security.SecurityFeature#authenticate() authenticate()}
 * .{@link io.helidon.webserver.security.SecurityFeature#rolesAllowed(String...) rolesAllowed("user")})
 * // authenticate "/admin" path and require role "admin"
 * .get("/admin", WebSecurity.rolesAllowed("admin")
 * .authenticate()
 * )
 * // build a routing instance to start {@link io.helidon.webserver.WebServer} with.
 * .build();
 * </pre>
 *
 * <p>
 * The main security methods are duplicate - first as static methods on {@link io.helidon.webserver.security.SecurityHttpFeature} and
 * then as instance methods on {@link io.helidon.webserver.security.SecurityHandler} that is returned by the static methods
 * above. This is to provide a single starting point for security integration ({@link io.helidon.webserver.security.SecurityHttpFeature})
 * and fluent API to build the "gate" to each route that is protected.
 *
 * @see io.helidon.webserver.security.SecurityHttpFeature#create(io.helidon.security.Security)
 * @see io.helidon.webserver.security.SecurityHttpFeature#create(io.helidon.config.Config)
 */
package io.helidon.webserver.security;
