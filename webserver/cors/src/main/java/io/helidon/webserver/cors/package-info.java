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
 * Use {@link io.helidon.webserver.cors.CORSSupport} and its {@link io.helidon.webserver.cors.CORSSupport.Builder} to add CORS
 * handling to resources in your application.
 * <p>
 * Because Helidon SE does not use annotation processing to identify endpoints, you need to provide the CORS information for
 * your application another way. You can use Helidon configuration, the Helidon CORS API, or a combination.
 * <h2>Configuration</h2>
 * <h3>Using the application default configuration</h3>
 * You can add a {@value io.helidon.webserver.cors.CORSSupport#CORS_CONFIG_KEY} section to your application's default config
 * file to define the CORS behavior for your application endpoints.
 * <pre>
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
 * </pre>
 * This defines CORS behavior for two paths, {@code /cors1} and {@code /cors2}, within your application's context root.
 * <p>
 *     Assuming you have written your application class {@code MyApp} to extend {@link io.helidon.webserver.Service}, the
 *     following code applies the CORS configuration above to it:
 * </p>
 *     <pre>
 *         Routing.Builder builder = Routing.builder()
 *                 .register("/myapp", CORSSupport.fromConfig(), new MyApp());
 *     </pre>
 *     Helidon will perform no CORS processing for any paths in your app other than {@code /cors1} and {@code /cors2}.
 * <h3>Using an explicit configuration object</h3>
 * You can create your own Helidon {@link io.helidon.config.Config} object that contains CORS information and use it instead of
 * the application default config.
 * The config node you create and give to {@code CORSSupport} <em>should not</em> nest the CORS information inside a
 * {@value io.helidon.webserver.cors.CORSSupport#CORS_CONFIG_KEY} section. Instead, it would look like this:
 * <pre>
 *     - path-prefix: /cors1
 *       allow-origins: ["*"]
 *       allow-methods: ["*"]
 *     - path-prefix: /cors2
 *       allow-origins: ["http://foo.bar", "http://bar.foo"]
 *       allow-methods: ["DELETE", "PUT"]
 *       allow-headers: ["X-bar", "X-foo"]
 *       allow-credentials: true
 *       max-age: -1
 * </pre>
 * <p>
 *     If the above config were stored in a resource in your app called {@code myAppCORS.yaml} then the following code would
 *     apply it to your app:
 * </p>
 *     <pre>
 *         Config myAppConfig = Config.builder().sources(ConfigSources.classpath("myAppCORS.yaml")).build();
 *         Routing.Builder builder = Routing.builder()
 *                 .register("/myapp", CORSSupport.fromConfig(myAppConfig), new MyApp());
 *     </pre>
 * <h2>The Helidon CORS API</h2>
 * You can define your application's CORS behavior programmatically -- together with configuration if you want -- by:
 * <ul>
 *     <li>creating a {@link io.helidon.webserver.cors.CrossOriginConfig.Builder} instance,</li>
 *     <li>operating on it to create the CORS set-up you want,</li>
 *     <li>using the builder's {@code build()} method to create the {@code CrossOriginConfig} instance, and</li>
 *     <li>using the {@code CORSSupport.Builder} to associate a path with the {@code CrossOriginConfig} object.</li>
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
 *                 .register("/myapp", CORSSupport.builder()
 *                                 .addCrossOrigin("/cors3", corsForCORS3) // links the CORS info with a path within the app
 *                                 .build(), new MyApp());
 * </pre>
 * Invoke {@code addCrossOrigin} multiple times to link more paths with CORS configuration. You can reuse one {@code
 * CrossOriginConfig} object with more than one path if that meets your needs.
 * <p>
 *     The following example shows how you can combine configuration and the API to prepare the {@code CORSSupport.Builder}
 *     which you could then pass to the {@code register} method. To help with readability as things get more complicated, this
 *     example saves the {@code CORSSupport.Builder} in a variable rather than constructing it in-line when invoking
 *     {@code register}:
 * </p>
 * <pre>
 *         CORSSupport.Builder corsBuilder = CORSSupport.builder()
 *                  .config(myAppConfig)
 *                  .addCrossOrigin("/cors3", corsFORCORS3);
 *
 *         Routing.Builder builder = Routing.builder()
 *                 .register("/myapp", corsBuilder.build(), new MyApp());
 * </pre>
 *
 * <h3>Convenience API for the "/" path</h3>
 * Sometimes you might want to prepare just one set of CORS information, for the "/" path. The Helidon CORS API provides a
 * short-cut for this. The {@code CORSSupport.Builder} class supports all the mutator methods from {@code CrossOriginConfig}
 * such as {@code allowOrigins}, and on {@code CORSSupport.Builder} these methods implicitly affect the "/" path.
 * The following code
 * <pre>
 *         CORSSupport.Builder corsBuilder = CORSSupport.builder()
 *             .allowOrigins("http://foo.bar", "http://bar.foo")
 *             .allowMethods("DELETE", "PUT");
 * </pre>
 * has the same effect as this more verbose version:
 * <pre>
 *         CrossOriginConfig corsForCORS3= CrossOriginConfig.builder()
 *             .allowOrigins("http://foo.bar", "http://bar.foo")
 *             .allowMethods("DELETE", "PUT")
 *             .build();
 *         CORSSupport.Builder corsBuilder = CORSSupport.builder()
 *                 .addCrossOrigin("/", corsForCORS3);
 * </pre>
 * <h3>{@code CORSSupport} as a handler</h3>
 * The previous examples use a {@code CORSSupport} instance as a Helidon {@link io.helidon.webserver.Service} which you can
 * register with the routing rules. You can also use a {@code CORSSupport} object as a {@link io.helidon.webserver.Handler} in
 * setting up the routing rules for an HTTP method and path. The next example sets up CORS processing for the {@code PUT}
 * HTTP method on the {@code /cors4} path within the app. The application code simply accepts the request graciously and
 * replies with success:
 * <pre>{@code
 *         Routing.Builder builder = Routing.builder()
 *                 .put("/cors4", CORSSupport.builder()
 *                               .allowOrigins("http://foo.bar", "http://bar.foo")
 *                               .allowMethods("DELETE", "PUT"),
 *                      (req, resp) -> resp.status(Http.Status.OK_200));
 * }</pre>
 * You can do this multiple times and even combine it with service registrations:
 * <pre>{@code
 *         Routing.Builder builder = Routing.builder()
 *                 .put("/cors4", CORSSupport.builder()
 *                                   .allowOrigins("http://foo.bar", "http://bar.foo")
 *                                   .allowMethods("DELETE", "PUT"),
 *                      (req, resp) -> resp.status(Http.Status.OK_200))
 *                 .get("/cors4", CORSSupport.builder()
 *                                   .allowOrigins("*")
 *                                   .minAge(-1),
 *                      (req, resp) -> resp.send("Hello, World!"))
 *                 .register(CORSSupport.fromConfig());
 * }</pre>
 * <h3>Resolving conflicting settings</h3>
 * With so many ways of preparing CORS information, conflicts can arise. The {@code CORSSupport.Builder} resolves conflicts CORS
 * set-up this way:
 * <ul>
 *     <li>Multiple invocations of {@code CORSSupport.Builder.config} effectively merge the configured values which designate
 *     <em>different</em> paths into a single, unified configuration.
 *     The configured values provided by the latest invocation of {@code CORSSupport.Builder.config} will override any
 *     previously-set configuration values for a given path.</li>
 *     <li>Multiple uses of the CORS API <em>other than</em> the {@code config} method) for different paths are merged among
 *     themselves. The last invocation of the non-config API for a given path wins.</li>
 *     <li>Use of the convenience {@code CrossOriginConfig}-style methods on {@code CORSSupport.Builder} act as non-config
 *     programmatic settings for the "/" path.</li>
 *     <li>Configured values override ones set programmatically for a given path.</li>
 * </ul>
 * <h2>Warning about internal classes</h2>
 * <p>
 *     Note that {@code CrossOriginHelper}, while {@code public}, is <em>not</em> intended for use by developers. It is
 *     reserved for internal Helidon use and might change at any time.
 * </p>
 */
package io.helidon.webserver.cors;
