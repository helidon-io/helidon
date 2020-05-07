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
 * <h1>Helidon SE CORS Support</h1>
 * <p>
 * Use {@link io.helidon.webserver.cors.CorsSupport} and its {@link io.helidon.webserver.cors.CorsSupport.Builder} to add CORS
 * handling to resources in your application.
 * <p>
 * Because Helidon SE does not use annotation processing to identify endpoints, you need to provide the CORS information for
 * your application another way. You can use Helidon configuration, the Helidon CORS API, or a combination.
 * <h2>Configuration</h2>
 * <h3>Format</h3>
 * CORS configuration looks like this:
 * <pre>
 * enabled: true    # the default
 * allow-origins: ["http://foo.bar", "http://bar.foo"]
 * allow-methods: ["DELETE", "PUT"]
 * allow-headers: ["X-bar", "X-foo"]
 * allow-credentials: true
 * max-age: -1
 * </pre>
 * <h3>Finding and applying CORS configuration</h3>
 * Although Helidon prescribes the CORS config format, you can put it wherever you want in your application's configuration
 * file. Your application code will retrieve the CORS config from its location within your configuration and then use that
 * config node to prepare CORS support for your app.
 *
 * For example, if you set up this configuration
 * <pre>
 * narrow:
 *   allow-origins: ["http://foo.bar", "http://bar.foo"]
 *   allow-methods: ["DELETE", "PUT"]
 *   allow-headers: ["X-bar", "X-foo"]
 *   allow-credentials: true
 *   max-age: -1
 *
 * wide:
 *   enabled: false
 *   allow-origins: ["*"]
 *   allow-methods: ["*"]
 *
 * just-disabled:
 *   enabled: false
 * </pre>
 * <p>
 *     in a resource called {@code myApp.yaml} then the following code would apply it to your app:
 * </p>
 *     <pre>{@code
 *         Config myAppConfig = Config.builder().sources(ConfigSources.classpath("myApp.yaml")).build();
 *         Routing.Builder builder = Routing.builder();
 *         myAppConfig.get("narrow").ifPresent(c -> builder.any(
 *                          "/greet", CorsSupport.create(c),
 *                          (req, resp) -> resp.status(Http.Status.OK_200).send()));
 *         myAppConfig.get("wide".ifPresent(c -> builder.get(
 *                          "/greet", CorsSupport.create(c),
 *                          (req, resp) -> resp.status(Http.Status.OK_200).send("Hello, World!")));
 *     }</pre>
 * This sets up more restrictive CORS behavior for more sensitive HTTP methods ({@code PUT} for example) and more liberal CORS
 * behavior for {@code GET}.
 *
 * <h2>The Helidon CORS API</h2>
 * You can define your application's CORS behavior programmatically -- together with configuration if you want -- by:
 * <ul>
 *     <li>creating a {@link io.helidon.webserver.cors.CrossOriginConfig.Builder} instance,</li>
 *     <li>operating on the builder to prepare the CORS set-up you want,</li>
 *     <li>using the builder's {@code build()} method to create the {@code CrossOriginConfig} instance, and</li>
 * </ul>
 * <p>
 * The next example shows creating CORS information and associating it with the path {@code /cors3} within the app.
 * <pre>
 *         CrossOriginConfig corsForCORS3= CrossOriginConfig.builder()
 *             .allowOrigins("http://foo.bar", "http://bar.foo")
 *             .allowMethods("DELETE", "PUT")
 *             .build();
 *
 *         Routing.Builder builder = Routing.builder()
 *                 .register("/myapp",
 *                           CorsSupport.builder()
 *                                 .addCrossOrigin("/cors3", corsForCORS3) // links the CORS info with a path within the app
 *                                 .build(),
 *                           new MyApp());
 * </pre>
 * Notice that you pass <em>two</em> services to the {@code register} method: the {@code CorsSupport} instance and your app
 * instance. Helidon will process requests to the path you specify with those services in that order. Also, note that you have
 * to make sure you use the same path in this API call and in your {@code MyApp} service if you adjust the routing there.
 * <p>
 * Invoke {@code addCrossOrigin} multiple times to link more paths with CORS configuration. You can reuse one {@code
 * CrossOriginConfig} object with more than one path if that meets your needs.
 * </p>
 * <p>
 * Remember that the CORS protocol uses the {@code OPTIONS} HTTP method for preflight requests. If you use the handler-based
 * methods on {@code Routing.Builder} be sure to invoke the {@code options} method as well (or {code any}) to set up routing for
 * {@code OPTIONS} requests.
 * </p>
 * <p>
 * Each {@code CorsSupport} instance can be enabled or disabled, either through configuration or using the API.
 * By default, when an application creates a new {@code CorsSupport.Builder} instance that builder's {@code build()} method will
 * create an enabled {@code CorsSupport} object. Any subsequent explicit setting on the builder, either expressly set by an
 * {@code enabled} entry in configuration passed to {@code CorsSupport.Builder.config} or set by invoking
 * {@code CorsSupport.Builder.enabled} follows the familiar "latest-wins" approach.
 * </p>
 */
package io.helidon.webserver.cors;
