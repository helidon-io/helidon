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
 *
 */

/**
 * Helidon SE CORS Support.
 * <p>
 * Use {@link io.helidon.cors.CORSSupport} and its {@link io.helidon.cors.CORSSupport.Builder} to add CORS handling to resources
 * in your application.
 * <p>
 * Because Helidon SE does not use annotation processing to identify endpoints, you need to provide the CORS information for
 * your application another way, in three steps:
 * <ol>
 *     <li>Create an instance of {@code CORSSupport.Builder} for your Helidon service (your application):
 * <pre>
 *     CORSSupport.Builder corsBuilder = CORSSupport.builder();
 * </pre>
 *     </li>
 *     <li>Next, give the builder information about how to set up CORS for some or all of the resources in your app. You can use one
 * or more of these approaches:
 * <ul>
 *     <li>using configuration
 *     <p>Often you would add a {@value io.helidon.cors.CORSSupport#CORS_CONFIG_KEY} section to your application's
 *     default configuration file, like this:
 *     <pre>
 *     cors:
 *       - path-prefix: /cors1
 *         allow-origins: ["*"]
 *         allow-methods: ["*"]
 *       - path-prefix: /cors2
 *         allow-origins: ["http://foo.bar", "http://bar.foo"]
 *         allow-methods: ["DELETE", "PUT"]
 *         allow-headers: ["X-bar", "X-foo"]
 *         allow-credentials: true
 *         max-age: -1
 *     </pre>
 *     and add code similar to this to retrieve it and use it:
 *     <pre>
 *         Config corsConfig = Config.create().get(CrossOriginConfig.CORS_CONFIG_KEY);
 *         corsBuilder.config(corsConfig);
 *     </pre>
 *     <li>using the {@link io.helidon.cors.CrossOriginConfig} class
 *     <p>Your code can create {@code CrossOriginConfig} instances and make them known to a {@code CORSSupport.Builder}
 *     using the {@link io.helidon.cors.CORSSupport.Builder#addCrossOrigin(java.lang.String, io.helidon.cors.CrossOriginConfig)}
 *     method. The {@code String} argument is the path <em>within your application's context root</em> to which this CORS
 *     set-up should apply. The following example has the same effect as the {@code /cors2} section from the config example above:
 *     </p>
 *     <pre>
 *         CrossOriginConfig cors2Setup = CrossOriginConfig.Builder.create()
 *                 .allowOrigins("http://foo.bar", "http://bar.foo")
 *                 .allowMethods("DELETE", "PUT")
 *                 .allowHeaders("X-bar", "X-foo")
 *                 .allowCredentials(true),
 *                 .minAge(-1)
 *                 .build();
 *         corsBuilder().addCrossOrigin("/cors2", cors2Setup);
 *     </pre>
 *     </li>
 * </ul>
 *     <li>Finally, create and register the {@code CORSSupport} instance <em>on the same path as</em> and
 *     <em>before</em> your own resources. The following code uses the {@code CORSSupport.Builder} from the earlier examples and
 *     registers it and your actual service on the same path with Helidon:
 * <pre>
 *     Routing.Builder builder = Routing.builder()
 *                 .register("/myapp", corsBuilder.build(), new MyApp());
 * </pre>
 *     </li>
 * </ol>
 *
 * <p>
 *     Note that {@code CrossOriginHelperInternal}, while {@code public}, is <em>not</em> intended for use by developers. It is
 *     reserved for internal Helidon use and might change at any time.
 * </p>
 */
package io.helidon.cors;
