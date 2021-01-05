/*
 * Copyright (c) 2017, 2020 Oracle and/or its affiliates.
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

package io.helidon.webserver.examples.basics;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;

import javax.json.Json;
import javax.json.JsonBuilderFactory;

import io.helidon.common.http.DataChunk;
import io.helidon.common.http.Http;
import io.helidon.common.http.MediaType;
import io.helidon.common.http.Parameters;
import io.helidon.media.common.MediaContext;
import io.helidon.media.common.MessageBodyReader;
import io.helidon.media.jsonp.JsonpSupport;
import io.helidon.webserver.Handler;
import io.helidon.webserver.HttpException;
import io.helidon.webserver.RequestPredicate;
import io.helidon.webserver.Routing;
import io.helidon.webserver.StaticContentSupport;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.jersey.JerseySupport;

/**
 * This example consists of few first tutorial steps of WebServer API. Each step is represented by a single method.
 * <p>
 * <b>Principles:</b>
 * <ul>
 *     <li>Reactive principles</li>
 *     <li>Reflection free</li>
 *     <li>Fluent</li>
 *     <li>Integration platform</li>
 * </ul>
 * <p>
 * It is also java executable main class. Use a method name as a command line parameter to execute.
 */
public class Main {

    private static final JsonBuilderFactory JSON = Json.createBuilderFactory(Collections.emptyMap());

    // ---------------- EXAMPLES

    /**
     * True heart of WebServer API is {@link Routing}. It provides fluent way how to assign custom {@link Handler} to the routing
     * rule. The rule consists from two main factors - <i>HTTP method</i> and <i>path pattern</i>.
     * <p>
     * The (route) {@link Handler} is a functional interface which process HTTP {@link io.helidon.webserver.ServerRequest request} and
     * writes to the {@link io.helidon.webserver.ServerResponse response}.
     */
    public void firstRouting() {
        Routing routing = Routing.builder()
                                 .post("/post-endpoint", (req, res) -> res.status(Http.Status.CREATED_201)
                                                                          .send())
                                 .get("/get-endpoint", (req, res) -> res.status(Http.Status.NO_CONTENT_204)
                                                                        .send("Hello World!"))
                                 .build();
        startServer(routing);
    }

    /**
     * {@link Routing} instance can be used to create {@link WebServer} instance.
     * It provides a simple, non-blocking life-cycle API returning
     * {@link java.util.concurrent.CompletionStage CompletionStages} to provide reactive access.
     *
     * @param routing the routing to drive by WebServer instance
     * @param mediaContext media support
     */
    protected void startServer(Routing routing, MediaContext mediaContext) {
        WebServer.builder(routing)
                .mediaContext(mediaContext)
                .build()
                 .start()
                 // All lifecycle operations are non-blocking and provides CompletionStage
                 .whenComplete((ws, thr) -> {
                     if (thr == null) {
                         System.out.println("Server is UP: http://localhost:" + ws.port());
                     } else {
                         System.out.println("Can NOT start WebServer!");
                         thr.printStackTrace(System.out);
                     }
                 });
    }

    /**
     * {@link Routing}
     * can be used to create {@link WebServer} instance.It provides a simple, non-blocking life-cycle API returning
     * {@link java.util.concurrent.CompletionStage CompletionStages} to provide reactive access.
     *
     * @param routing the routing to drive by WebServer instance
     */
    protected void startServer(Routing routing) {
        startServer(routing, MediaContext.create());
    }

    /**
     * All routing rules (routes) are evaluated in a definition order. The {@link Handler} assigned with the first valid route
     * for given request is called. It is a responsibility of each handler to process in one of the following ways:
     * <ul>
     *     <li>Respond using one of {@link io.helidon.webserver.ServerResponse#send() ServerResponse.send(...)} method.</li>
     *     <li>Continue to next valid route using {@link io.helidon.webserver.ServerRequest#next() ServerRequest.next()} method.
     *     <i>It is possible to define filtering handlers.</i></li>
     * </ul>
     * <p>
     * If no valid {@link Handler} is found then routing respond by {@code HTTP 404} code.
     * <p>
     * If selected {@link Handler} doesn't process request than the request <b>stacks</b>!
     * <p>
     * <b>Blocking operations:</b><br>
     * For performance reason, {@link Handler} can be called directly by a selector thread. It is not good idea to block
     * such thread. If request must be processed by a blocking operation then such processing should be deferred to another
     * thread.
     */
    public void routingAsFilter() {
        Routing routing = Routing.builder()
                                 .any((req, res) -> {
                                                  System.out.println(req.method() + " " + req.path());
                                                  // Filters are just routing handlers which calls next()
                                                  req.next();
                                              })
                                 .post("/post-endpoint", (req, res) -> res.status(Http.Status.CREATED_201)
                                                                          .send())
                                 .get("/get-endpoint", (req, res) -> res.status(Http.Status.NO_CONTENT_204)
                                                                        .send("Hello World!"))
                                 .build();
        startServer(routing);
    }

    /**
     * {@link io.helidon.webserver.ServerRequest ServerRequest} provides access to three types of "parameters":
     * <ul>
     *     <li>Headers</li>
     *     <li>Query parameters</li>
     *     <li>Path parameters - <i>Evaluated from provided {@code path pattern}</i></li>
     * </ul>
     * <p>
     * {@link java.util.Optional Optional} API is heavily used to represent parameters optionality.
     * <p>
     * WebServer {@link Parameters Parameters} API is used to represent fact, that <i>headers</i> and
     * <i>query parameters</i> can contain multiple values.
     */
    public void parametersAndHeaders() {
        Routing routing = Routing.builder()
                .get("/context/{id}", (req, res) -> {
                    StringBuilder sb = new StringBuilder();
                    // Request headers
                    req.headers()
                       .first("foo")
                       .ifPresent(v -> sb.append("foo: ").append(v).append("\n"));
                    // Request parameters
                    req.queryParams()
                       .first("bar")
                       .ifPresent(v -> sb.append("bar: ").append(v).append("\n"));
                    // Path parameters
                    sb.append("id: ").append(req.path().param("id"));
                    // Response headers
                    res.headers().contentType(MediaType.TEXT_PLAIN);
                    // Response entity (payload)
                    res.send(sb.toString());
                })
                .build();
        startServer(routing);
    }

    /**
     * Routing rules (routes) are limited on two criteria - <i>HTTP method and path</i>. {@link RequestPredicate} can be used
     * to specify more complex criteria.
     */
    public void advancedRouting() {
        Routing routing = Routing.builder()
                                 .get("/foo", RequestPredicate.create()
                                                              .accepts(MediaType.TEXT_PLAIN)
                                                              .containsHeader("bar")
                                                              .thenApply((req, res) -> res.send()))
                                 .build();
        startServer(routing);
    }

    /**
     * Larger applications with many routing rules can cause complicated readability (maintainability) if all rules are
     * defined in a single fluent code. It is possible to register {@link io.helidon.webserver.Service Service} and organise
     * the code into services and resources. {@code Service} is an interface which can register more routing rules (routes).
     */
    public void organiseCode() {
        Routing routing = Routing.builder()
                                 .register("/catalog-context-path", new Catalog())
                                 .build();
        startServer(routing);
    }

    /**
     * Request payload (body/entity) is represented by {@link java.util.concurrent.Flow.Publisher Flow.Publisher}
     * of {@link DataChunk RequestChunks} to enable reactive processing of the content of any size.
     * But it is more convenient to process entity in some type specific form. WebServer supports few types which can be
     * used te read the whole entity:
     * <ul>
     *     <li>{@code byte[]}</li>
     *     <li>{@code String}</li>
     *     <li>{@code InputStream}</li>
     * </ul>
     * <p>
     * Similar approach is used for the response entity.
     */
    public void readContentEntity() {
        Routing routing = Routing.builder()
                                 .post("/foo", (req, res) -> {
                                     req.content()
                                        .as(String.class)
                                        // The whole entity can be read when all request chunks are processed - CompletionStage
                                        .whenComplete((data, thr) -> {
                                            if (thr == null) {
                                                System.out.println("/foo DATA: " + data);
                                                res.send(data);
                                            } else {
                                                res.status(Http.Status.BAD_REQUEST_400);
                                            }
                                        });
                                 })
                                 // It is possible to use Hanlder.of() method to automatically cover all error states.
                                 .post("/bar", Handler.create(String.class, (req, res, data) -> {
                                     System.out.println("/foo DATA: " + data);
                                     res.send(data);
                                 }))
                                 .build();
        startServer(routing);
    }

    /**
     * Use a custom {@link MessageBodyReader reader} to convert the request content into an object of a given type.
     */
    public void mediaReader() {
        Routing routing = Routing.builder()
                                 .post("/create-record", Handler.create(Name.class, (req, res, name) -> {
                                     System.out.println("Name: " + name);
                                     res.status(Http.Status.CREATED_201)
                                        .send(name.toString());
                                 }))
                                 .build();

        // Create a media support that contains the defaults and our custom Name reader
        MediaContext mediaContext = MediaContext.builder()
                .addReader(NameReader.create())
                .build();

        startServer(routing, mediaContext);
    }

    /**
     * Combination of filtering {@link Handler} pattern with {@link io.helidon.webserver.Service Service} registration capabilities
     * can be used by other frameworks for the integration. WebServer is shipped with several integrated libraries (supports)
     * including <i>static content</i>, JSON and Jersey. See {@code POM.xml} for requested dependencies.
     */
    public void supports() {
        Routing routing = Routing.builder()
                                 .register(StaticContentSupport.create("/static"))
                                 .get("/hello/{what}", (req, res) -> res.send(JSON.createObjectBuilder()
                                                                                  .add("message",
                                                                                       "Hello " + req.path()
                                                                                                     .param("what"))
                                                                                  .build()))
                                 .register("/api", JerseySupport.builder()
                                                                .register(HelloWorldResource.class)
                                                                .build())
                                 .build();

        MediaContext mediaContext = MediaContext.builder()
                .addWriter(JsonpSupport.writer())
                .build();

        startServer(routing, mediaContext);
    }

    /**
     * Request processing can cause error represented by {@link Throwable}. It is possible to register custom
     * {@link io.helidon.webserver.ErrorHandler ErrorHandlers} for specific processing.
     * <p>
     * If error is not processed by a custom {@link io.helidon.webserver.ErrorHandler ErrorHandler} than default one is used.
     * It respond with <i>HTTP 500 code</i> unless error is not represented
     * by {@link HttpException HttpException}. In such case it reflects its content.
     */
    public void errorHandling() {
        Routing routing = Routing.builder()
                                 .post("/compute", Handler.create(String.class, (req, res, str) -> {
                                     int result = 100 / Integer.parseInt(str);
                                     res.send(String.valueOf("100 / " + str + " = " + result));
                                 }))
                                 .error(Throwable.class, (req, res, ex) -> {
                                     ex.printStackTrace(System.out);
                                     req.next();
                                 })
                                 .error(NumberFormatException.class,
                                        (req, res, ex) -> res.status(Http.Status.BAD_REQUEST_400).send())
                                 .error(ArithmeticException.class,
                                        (req, res, ex) -> res.status(Http.Status.PRECONDITION_FAILED_412).send())
                                 .build();
        startServer(routing);
    }


    // ---------------- EXECUTION

    private static final String SYSPROP_EXAMPLE_NAME = "exampleName";
    private static final String ENVVAR_EXAMPLE_NAME = "EXAMPLE_NAME";

    /**
     * Prints usage instructions.
     */
    public void help() {
        StringBuilder hlp = new StringBuilder();
        hlp.append("java -jar example-basics.jar <exampleMethodName>\n");
        hlp.append("Example method names:\n");
        Method[] methods = Main.class.getDeclaredMethods();
        for (Method method : methods) {
            if (Modifier.isPublic(method.getModifiers()) && !Modifier.isStatic(method.getModifiers())) {
                hlp.append("    ").append(method.getName()).append('\n');
            }
        }
        hlp.append('\n');
        hlp.append("Example method name can be also provided as a\n");
        hlp.append("    - -D").append(SYSPROP_EXAMPLE_NAME).append(" jvm property.\n");
        hlp.append("    - ").append(ENVVAR_EXAMPLE_NAME).append(" environment variable.\n");
        System.out.println(hlp);
    }

    /**
     * Prints usage instructions. (Shortcut to {@link #help()} method.
     */
    public void h() {
        help();
    }

    /**
     * Java main method.
     *
     * @param args Command line arguments.
     */
    public static void main(String[] args) {
        String exampleName = null;
        if (args.length > 0) {
            exampleName = args[0];
        } else if (System.getProperty(SYSPROP_EXAMPLE_NAME) != null) {
            exampleName = System.getProperty(SYSPROP_EXAMPLE_NAME);
        } else if (System.getenv(ENVVAR_EXAMPLE_NAME) != null) {
            exampleName = System.getenv(ENVVAR_EXAMPLE_NAME);
        } else {
            System.out.println("Missing example name. It can be provided as a \n"
                                + "    - first command line argument.\n"
                                + "    - -D" + SYSPROP_EXAMPLE_NAME + " jvm property.\n"
                                + "    - " + ENVVAR_EXAMPLE_NAME + " environment variable.\n");
            System.exit(1);
        }
        while (exampleName.startsWith("-")) {
            exampleName = exampleName.substring(1);
        }
        Main m = new Main();
        try {
            Method method = Main.class.getMethod(exampleName);
            method.invoke(m);
        } catch (NoSuchMethodException e) {
            System.out.println("Missing example method named: " + exampleName);
            System.exit(2);
        } catch (IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace(System.out);
            System.exit(100);
        }
    }
}
