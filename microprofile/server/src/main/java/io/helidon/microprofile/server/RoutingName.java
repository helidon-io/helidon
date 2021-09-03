/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.microprofile.server;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Binds an {@link javax.ws.rs.core.Application} or {@link io.helidon.webserver.Service} to a specific (named) routing
 *  on {@link io.helidon.webserver.WebServer}. The routing should have a corresponding named socket configured on the
 *  WebServer to run the routing on.
 *
 * Configuration can be overridden using configuration:
 * <ul>
 *     <li>Name of routing: {@code fully.qualified.ClassName.routing-name.name} to change the name of the named routing.
 *      Use {@value #DEFAULT_NAME} to revert to default named routing.
 *      </li>
 *     <li>Whether routing is required: {@code fully.qualified.ClassName.routing-name.required}</li>
 * </ul>
 *
 * Example class:
 * <pre>
 * {@literal @}ApplicationScoped
 * {@literal @}RoutingPath("/myservice")
 * {@literal @}RoutingName(value = "admin", required = true)
 * public class MyService implements Service {
 *     {@literal @}Override
 *     public void update(Routing.Rules rules) {
 *         {@code rules.get("/hello", (req, res) -> res.send("Hello WebServer"));}
 *     }
 * }
 * </pre>
 * Example configuration (yaml):
 * <pre>
 * com.example.MyService.routing-name:
 *  name: "@default"
 *  required: false
 * </pre>
 */
@Target({TYPE, METHOD, FIELD})
@Retention(RUNTIME)
@Documented
public @interface RoutingName {
    /**
     * Configuration key of the routing name, appended after the fully qualified class name (does not contain the leading dot).
     */
    String CONFIG_KEY_NAME = "routing-name.name";
    /**
     * Configuration key of the routing name required flag,
     * appended after the fully qualified class name (does not contain the leading dot).
     */
    String CONFIG_KEY_REQUIRED = "routing-name.required";

    /**
     * Name (reserved) for the default listener of WebServer.
     */
    String DEFAULT_NAME = "@default";

    /**
     * Name of a routing to bind this application/service to.
     * @return name of a routing (or listener host/port) on WebServer
     */
    String value();

    /**
     * Set to true if the {@link #value()} MUST be configured.
     *
     * @return {@code true} to enforce existence of the named routing
     */
    boolean required() default false;
}
