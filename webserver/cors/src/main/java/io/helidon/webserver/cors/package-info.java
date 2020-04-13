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
 * Use {@link io.helidon.webserver.cors.CORSSupportSE} and its {@link io.helidon.webserver.cors.CORSSupportSE.Builder} to add CORS
 * handling to resources in your application.
 * <p>
 * Because Helidon SE does not use annotation processing to identify endpoints, you need to provide the CORS information for
 * your application another way. You can use Helidon configuration, the Helidon CORS API, or a combination.
 * <h2>Configuration</h2>
 * <h3>Format</h3>
 * CORS configuration has two top-level items:
 * <ul>
 *     <li>{@code enabled} - indicates whether CORS processing should be enabled or not; default {@code true}</li>
 *     <li>{@code paths} - contains a list of sub-items describing the CORS set-up for one path
 *     <ul>
 *         <li>{@code path-prefix} - the path this entry applies to</li>
 *         <li>{@code allow-origins} - array of origin URL strings</li>
 *         <li>{@code allow-methods} - array of method name strings (uppercase)</li>
 *         <li>{@code allow-headers} - array of header strings</li>
 *         <li>{@code expose-headers} - array of header strings</li>
 *         <li>{@code allow-credentials} - boolean</li>
 *         <li>{@code max-age} - long</li>
 *     </ul></li>
 * </ul>
 * <p>
 *     The {@code enabled} setting allows configuration to completely disable CORS processing, regardless of other settings in
 *     config or programmatic set-up of CORS in the application.
 * </p>
 * <h3>Finding and applying CORS configuration</h3>
 * Although Helidon prescribes the CORS config format, you can put it wherever you want in your application's configuration
 * file. Your application code will retrieve the CORS config from its location within your configuration and then use that
 * config node with the {@link io.helidon.webserver.cors.CORSSupportSE.Builder} in preparing CORS support for your app.
 *
 * If you set up this configuration
 * <pre>
 *   my-cors:
 *     paths:
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
 * <p>
 *     in a resource called {@code myApp.yaml} then the following code would apply it to your app:
 * </p>
 *     <pre>
 *         Config myAppConfig = Config.builder().sources(ConfigSources.classpath("myApp.yaml")).build();
 *         Routing.Builder builder = Routing.builder()
 *                 .register("/myapp",
 *                           CORSSupportSE.builder()
 *                                      .config(myAppConfig.get("my-cors"))
 *                                      .build(),
 *                           new MyApp());
 *     </pre>
 * <h2>The Helidon CORS API</h2>
 * You can define your application's CORS behavior programmatically -- together with configuration if you want -- by:
 * <ul>
 *     <li>creating a {@link io.helidon.webserver.cors.CrossOriginConfig.Builder} instance,</li>
 *     <li>operating on it to create the CORS set-up you want,</li>
 *     <li>using the builder's {@code build()} method to create the {@code CrossOriginConfig} instance, and</li>
 *     <li>using the {@code CORSSupportSE.Builder} to associate a path with the {@code CrossOriginConfig} object.</li>
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
 *                           CORSSupportSE.builder()
 *                                 .addCrossOrigin("/cors3", corsForCORS3) // links the CORS info with a path within the app
 *                                 .build(),
 *                           new MyApp());
 * </pre>
 * Notice that you pass <em>two</em> services to the {@code register} method: the {@code CORSSupportSE} instance and your app
 * instance. Helidon will process requests to the path you specify with those services in that order.
 * <p>
 * Invoke {@code addCrossOrigin} multiple times to link more paths with CORS configuration. You can reuse one {@code
 * CrossOriginConfig} object with more than one path if that meets your needs.
 * </p>
 * <p>
 *     The following example shows how you can combine configuration and the API. To help with readability as things get more
 *     complicated, this example saves the {@code CORSSupportSE.Builder} in a variable rather than constructing it in-line when
 *     invoking {@code register}:
 * </p>
 * <pre>
 *         CORSSupportSE.Builder corsBuilder = CORSSupportSE.builder()
 *                  .config(myAppConfig.get("my-cors"))
 *                  .addCrossOrigin("/cors3", corsFORCORS3);
 *
 *         Routing.Builder builder = Routing.builder()
 *                 .register("/myapp",
 *                           corsBuilder.build(),
 *                           new MyApp());
 * </pre>
 *
 * <h3>Convenience API for the "match-all" path</h3>
 * Sometimes you might want to prepare just one set of CORS information to match any path. The Helidon CORS API provides a
 * short-cut for this. The {@code CORSSupportSE.Builder} class supports all the mutator methods from {@code CrossOriginConfig}
 * such as {@code allowOrigins}, and on {@code CORSSupportSE.Builder} these methods implicitly affect the
 * {@value io.helidon.webserver.cors.CrossOriginConfigAggregator#PATHLESS_KEY} path. (See the
 * {@link io.helidon.webserver.PathMatcher} documentation.)
 * <p>
 * The following code
 * <pre>
 *         CORSSupportSE.Builder corsBuilder = CORSSupportSE.builder()
 *             .allowOrigins("http://foo.bar", "http://bar.foo")
 *             .allowMethods("DELETE", "PUT");
 * </pre>
 * has the same effect as this more verbose version:
 * <pre>
 *         CrossOriginConfig configForAll = CrossOriginConfig.builder()
 *             .allowOrigins("http://foo.bar", "http://bar.foo")
 *             .allowMethods("DELETE", "PUT")
 *             .build();
 *         CORSSupportSE.Builder corsBuilder = CORSSupportSE.builder()
 *                 .addCrossOrigin("{+}", configForAll);
 * </pre>
 * <h3>{@code CORSSupportSE} as a handler</h3>
 * The previous examples use a {@code CORSSupportSE} instance as a Helidon {@link io.helidon.webserver.Service} which you can
 * register with the routing rules. You can also use a {@code CORSSupportSE} object as a {@link io.helidon.webserver.Handler} in
 * setting up the routing rules for an HTTP method and path. The next example sets up CORS processing for the {@code PUT} and
 * {@code OPTIONS} HTTP methods on the {@code /cors4} path within the app. The application code for both simply accepts the
 * request graciously and replies with success:
 * <pre>{@code
 *         CORSSupportSE cors4Support = CORSSupportSE.builder()
 *                 .allowOrigins("http://foo.bar", "http://bar.foo")
 *                 .allowMethods("PUT")
 *                 .build();
 *         Routing.Builder builder = Routing.builder()
 *                 .put("/cors4",
 *                      cors4Support,
 *                      (req, resp) -> resp.status(Http.Status.OK_200).send())
 *                 .options("/cors4",
 *                      cors4Support,
 *                      (req, resp) -> resp.status(Http.Status.OK_200).send());
 * }</pre>
 * Remember that the CORS protocol uses the {@code OPTIONS} HTTP method for preflight requests. If you use the handler-based
 * methods on {@code Routing.Builder} be sure to invoke the {@code options} method as well to set up routing for {@code OPTIONS}
 * requests. You could invoke the {@code any} method as a short-cut.
 * <p>
 * You can do this multiple times and even combine it with service registrations:
 * </p>
 * <pre>{@code
 *         Routing.Builder builder = Routing.builder()
 *                 .put("/cors4",
 *                      cors4Support,
 *                      (req, resp) -> resp.status(Http.Status.OK_200).send())
 *                 .options("/cors4",
 *                      cors4Support,
 *                      (req, resp) -> resp.status(Http.Status.OK_200).send())
 *                 .get("/cors4",
 *                      CORSSupportSE.builder()
 *                               .allowOrigins("*")
 *                               .minAge(-1),
 *                      (req, resp) -> resp.send("Hello, World!"))
 *                 .register(CORSSupportSE.fromConfig());
 * }</pre>
 * <h3>Resolving conflicting settings</h3>
 * With so many ways of preparing CORS information, conflicts can arise. The {@code CORSSupportSE.Builder} resolves conflicts in
 * CORS set-up as if using a {@code Map<String, CrossOriginConfig>} to store all the information:
 * <ul>
 *     <li>Multiple invocations of the {@code CORSSupportSE.Builder} {@code config},
 *     {@code addCrossOrigin}, and "match-any" methods (from {@link io.helidon.webserver.cors.Setter}) effectively merge
 *     values which designate <em>different</em> paths into a single, unified group of settings.
 *     The settings provided by the latest invocation of these methods override any previously-set values for a given path.</li>
 *     <li>Use of the convenience {@code CrossOriginConfig}-style methods defined by {@code Setter} affect the map entry with
 *     key {@value io.helidon.webserver.cors.CrossOriginConfigAggregator#PATHLESS_KEY}, <em>updating</em> any existing entry and
 *     <em>creating</em> one if needed. As a result, invoking {@code config} and {@code addCrossOriginConfig} methods with that
 *     path will overwrite any values set by earlier invocations of the convenience methods.</li>
 * </ul>
 * <p>
 * Each {@code CORSSupportSE} instance can be enabled or disabled, either through configuration or using the API.
 * By default, when an application creates a new {@code CORSSupportSE.Builder} instance that builder's {@code build()} method will
 * create an enabled {@code CORSSupportSE} object. Any subsequent explicit setting on the builder, either expressly set by an
 * {@code enabled} entry in configuration passed to {@code CORSSupportSE.Builder.config} or set by invoking
 * {@code CORSSupportSE.Builder.enabled} follows the familiar "latest-wins" approach.
 * </p>
 * <p>
 *     If the application uses a single {@code CORSSupportSE} instance, then the enabled setting for that instance governs the
 *     entire CORS implementation for the app's endpoints.
 * </p>
 */
package io.helidon.webserver.cors;
