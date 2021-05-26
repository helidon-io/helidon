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

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Path of a {@link io.helidon.webserver.Service} to register with routing.
 * If a service is not annotated with this annotation, it would be registered without a path using
 * {@link io.helidon.webserver.Routing.Rules#register(io.helidon.webserver.Service...)}.
 *
 * Configuration can be overridden using configuration:
 * <ul>
 *     <li>{@code fully.qualified.ClassName.routing-path.path} to change the path.</li>
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
 * com.example.MyService.routing-path:
 *  path: "/myservice-customized"
 * </pre>
 * <p><b>Limitations</b><br>
 * <ul>
 *     <li>{@link javax.enterprise.context.RequestScoped} beans are NOT available for injection. Reactive services are
 *     designed to be built without request scoped injection. You can still use beans in
 *     {@link javax.enterprise.context.ApplicationScoped} and {@link javax.enterprise.context.Dependent} scopes</li>
 * </ul>
 */
@Target({TYPE, METHOD})
@Retention(RUNTIME)
@Documented
public @interface RoutingPath {
    /**
     * Configuration key of the routing path, appended after the fully qualified class name (does not contain the leading dot).
     */
    String CONFIG_KEY_PATH = "routing-path.path";

    /**
     * Path of this WebServer service. Use the same path as would be used with {@link io.helidon.webserver.Routing.Rules}.
     *
     * @return path to register the service on.
     */
    String value();
}
