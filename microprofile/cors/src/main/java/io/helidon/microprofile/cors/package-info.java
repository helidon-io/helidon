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
 * <h1>Helidon MP CORS Support</h1>
 * Adding the Helidon MP CORS module to your application enables CORS support automatically, implementing the configuration in
 * the {@value io.helidon.microprofile.cors.CrossOriginFilter#CORS_CONFIG_KEY} section of your MicroProfile configuration.
 * <p>
 * Many MP developers will use the {@link io.helidon.microprofile.cors.CrossOrigin} annotation on the endpoint implementations in
 * their code to set up the CORS behavior, but any values in configuration will override the annotations or set up CORS for
 * endpoints without the annotation.
 * </p>
 * <p>
 * Here is an example of the configuration format:
 * </p>
 * <pre>
 *   cors:
 *     enabled: true # this is the default
 *     paths:
 *       - path-expr: /cors1
 *         allow-origins: ["*"]
 *         allow-methods: ["*"]
 *       - path-expr: /cors2
 *         allow-origins: ["http://foo.bar", "http://bar.foo"]
 *         allow-methods: ["DELETE", "PUT"]
 *         allow-headers: ["X-bar", "X-foo"]
 *         allow-credentials: true
 *         max-age: -1
 * </pre>
 *
 */
package io.helidon.microprofile.cors;
