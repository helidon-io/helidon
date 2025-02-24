/*
 * Copyright (c) 2022, 2025 Oracle and/or its affiliates.
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
package io.helidon.microprofile.testing;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.inject.Qualifier;

/**
 * CDI qualifier to inject a JAX-RS client or URI for a named socket.
 * <p>
 * The supported types are:
 * <ul>
 *     <li>{@link jakarta.ws.rs.client.WebTarget WebTarget} a JAXRS client target</li>
 *     <li>{@link java.net.URI URI} a URI</li>
 *     <li>{@link String} a raw URI</li>
 * </ul>
 * <p>
 * This annotation can be used on constructor parameters, or class fields.
 * Test method parameter injection may be supported depending on the test framework integration.
 * <p>
 * Also note that the default socket name is {@code "@default"}.
 * <p>
 * E.g. constructor injection:
 * <pre>
 * class MyTest {
 *     private final WebTarget target;
 *
 *     &#64;Inject
 *     MyTest(&#64;Socket("@default") URI uri) {
 *         target = ClientBuilder.newClient().target(uri);
 *     }
 * }
 * </pre>
 * <p>
 * E.g. field injection:
 * <pre>
 * class MyTest {
 *
 *     &#64;Inject // optional
 *     &#64;Socket("@default")
 *     private WebTarget target;
 * }
 * </pre>
 */
@Qualifier
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.PARAMETER})
public @interface Socket {

    /**
     * Name of the socket.
     *
     * @return socket name
     */
    String value();
}
