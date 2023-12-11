/*
 * Copyright (c) 2017, 2023 Oracle and/or its affiliates.
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

package io.helidon.examples.webserver.basics;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.HttpException;
import io.helidon.http.ServerRequestHeaders;
import io.helidon.http.Status;
import io.helidon.http.media.EntityReader;
import io.helidon.http.media.MediaContext;
import io.helidon.http.media.MediaContextConfig;
import io.helidon.http.media.ReadableEntity;
import io.helidon.http.media.jsonp.JsonpSupport;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.ErrorHandler;
import io.helidon.webserver.http.Handler;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import io.helidon.webserver.staticcontent.StaticContentService;

import jakarta.json.Json;
import jakarta.json.JsonBuilderFactory;

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
    private static final HeaderName BAR_HEADER = HeaderNames.create("bar");
    private static final HeaderName FOO_HEADER = HeaderNames.create("foo");

    // ---------------- EXAMPLES

    /**
     * True heart of WebServer API is {@link HttpRouting}.
     * It provides fluent way how to assign custom {@link Handler} to the routing rule.
     * The rule consists from two main factors - <i>HTTP method</i> and <i>path pattern</i>.
     * <p>
     * The (route) {@link Handler} is a functional interface which process HTTP {@link ServerRequest request} and
     * writes to the {@link ServerResponse response}.
     *
     * @param routing routing builder
     */
    public static void firstRouting(HttpRouting.Builder routing) {
        routing.post("/firstRouting/post-endpoint", (req, res) -> res.status(Status.CREATED_201)
                        .send())
                .get("/firstRouting/get-endpoint", (req, res) -> res.status(Status.OK_200)
                        .send("Hello World!"));
    }

    /**
     * All routing rules (routes) are evaluated in a definition order. The {@link Handler} assigned with the first valid route
     * for given request is called. It is a responsibility of each handler to process in one of the following ways:
     * <ul>
     *     <li>Respond using one of {@link ServerResponse#send() ServerResponse.send(...)} method.</li>
     *     <li>Continue to next valid route using {@link ServerResponse#next() ServerRequest.next()} method.
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
     *
     * @param routing routing builder
     */
    public static void routingAsFilter(HttpRouting.Builder routing) {
        routing.any("/routingAsFilter/*", (req, res) -> {
                    System.out.println(req.prologue().method() + " " + req.path());
                    // Filters are just routing handlers which calls next()
                    res.next();
                })
                .post("/routingAsFilter/post-endpoint", (req, res) -> res.status(Status.CREATED_201)
                        .send())
                .get("/routingAsFilter/get-endpoint", (req, res) -> res.status(Status.OK_200)
                        .send("Hello World!"));
    }

    /**
     * {@link ServerRequest} provides access to three types of "parameters":
     * <ul>
     *     <li>Headers</li>
     *     <li>Query parameters</li>
     *     <li>Path parameters - <i>Evaluated from provided {@code path pattern}</i></li>
     * </ul>
     * <p>
     * {@link java.util.Optional Optional} API is heavily used to represent parameters optionality.
     * <p>
     * WebServer {@link io.helidon.common.parameters.Parameters Parameters} API is used to represent fact, that <i>headers</i> and
     * <i>query parameters</i> can contain multiple values.
     *
     * @param routing routing builder
     */
    public static void parametersAndHeaders(HttpRouting.Builder routing) {
        routing.get("/parametersAndHeaders/context/{id}", (req, res) -> {
            StringBuilder sb = new StringBuilder();
            // Request headers
            req.headers()
                    .first(FOO_HEADER)
                    .ifPresent(v -> sb.append("foo: ").append(v).append("\n"));
            // Request parameters
            req.query()
                    .first("bar")
                    .ifPresent(v -> sb.append("bar: ").append(v).append("\n"));
            // Path parameters
            sb.append("id: ").append(req.path().pathParameters().get("id"));
            // Response headers
            res.headers().contentType(MediaTypes.TEXT_PLAIN);
            // Response entity (payload)
            res.send(sb.toString());
        });
    }

    /**
     * Routing rules (routes) are limited on two criteria - <i>HTTP method and path</i>.
     *
     * @param routing routing builder
     */
    public static void advancedRouting(HttpRouting.Builder routing) {
        routing.get("/advancedRouting/foo", (req, res) -> {
            ServerRequestHeaders headers = req.headers();
            if (headers.isAccepted(MediaTypes.TEXT_PLAIN)
                && headers.contains(BAR_HEADER)) {

                res.send();
            } else {
                res.next();
            }
        });
    }

    /**
     * Larger applications with many routing rules can cause complicated readability (maintainability) if all rules are
     * defined in a single fluent code. It is possible to register {@link HttpService} and organise
     * the code into services and resources. {@code Service} is an interface which can register more routing rules (routes).
     *
     * @param routing routing builder
     */
    public static void organiseCode(HttpRouting.Builder routing) {
        routing.register("/organiseCode/catalog-context-path", new Catalog());
    }

    /**
     * Request payload (body/entity) is represented by {@link ReadableEntity}.
     * But it is more convenient to process entity in some type specific form. WebServer supports few types which can be
     * used te read the whole entity:
     * <ul>
     *     <li>{@code byte[]}</li>
     *     <li>{@code String}</li>
     *     <li>{@code InputStream}</li>
     * </ul>
     * <p>
     * Similar approach is used for the response entity.
     *
     * @param routing routing builder
     */
    public static void readContentEntity(HttpRouting.Builder routing) {
        routing.post("/readContentEntity/foo", (req, res) -> {
                    try {
                        String data = req.content().as(String.class);
                        System.out.println("/foo DATA: " + data);
                        res.send(data);
                    } catch (Throwable th) {
                        res.status(Status.BAD_REQUEST_400);
                    }
                })
                // It is possible to use Handler.of() method to automatically cover all error states.
                .post("/readContentEntity/bar", Handler.create(String.class, (data, res) -> {
                    System.out.println("/foo DATA: " + data);
                    res.send(data);
                }));
    }

    /**
     * Use a custom {@link EntityReader reader} to convert the request content into an object of a given type.
     *
     * @param routing      routing builder
     * @param mediaContext media context builder
     */
    public static void mediaReader(HttpRouting.Builder routing, MediaContextConfig.Builder mediaContext) {
        routing.post("/mediaReader/create-record", Handler.create(Name.class, (name, res) -> {
            System.out.println("Name: " + name);
            res.status(Status.CREATED_201)
                    .send(name.toString());
        }));

        // add our custom Name reader
        mediaContext.addMediaSupport(NameSupport.create());
    }

    /**
     * Combination of filtering {@link Handler} pattern with {@link HttpService} registration capabilities
     * can be used by other frameworks for the integration. WebServer is shipped with several integrated libraries (supports)
     * including <i>static content</i>, JSON and Jersey. See {@code POM.xml} for requested dependencies.
     *
     * @param routing      routing builder
     * @param mediaContext mediaContext
     */
    public static void supports(HttpRouting.Builder routing, MediaContextConfig.Builder mediaContext) {
        routing.register("/supports", StaticContentService.create("/static"))
                .get("/supports/hello/{what}", (req, res) ->
                        res.send(JSON.createObjectBuilder()
                                .add("message", "Hello " + req.path()
                                        .pathParameters()
                                        .get("what"))
                                .build()));
        mediaContext.addMediaSupport(JsonpSupport.create());
    }

    /**
     * Request processing can cause error represented by {@link Throwable}. It is possible to register custom
     * {@link ErrorHandler ErrorHandlers} for specific processing.
     * <p>
     * If error is not processed by a custom {@link ErrorHandler ErrorHandler} than default one is used.
     * It responds with <i>HTTP 500 code</i> unless error is not represented
     * by {@link HttpException HttpException}. In such case it reflects its content.
     *
     * @param routing routing builder
     */
    public static void errorHandling(HttpRouting.Builder routing) {
        routing.post("/errorHandling/compute", Handler.create(String.class, (str, res) -> {
                    int result = 100 / Integer.parseInt(str);
                    res.send("100 / " + str + " = " + result);
                }))
                .error(Throwable.class, (req, res, ex) -> {
                    ex.printStackTrace(System.out);
                    res.next();
                })
                .error(NumberFormatException.class,
                        (req, res, ex) -> res.status(Status.BAD_REQUEST_400).send())
                .error(ArithmeticException.class,
                        (req, res, ex) -> res.status(Status.PRECONDITION_FAILED_412).send());
    }


    // ---------------- EXECUTION

    private static final String EXAMPLE_NAME_SYS_PROP = "exampleName";
    private static final String EXAMPLE_NAME_ENV_VAR = "EXAMPLE_NAME";

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
        hlp.append("    - -D").append(EXAMPLE_NAME_SYS_PROP).append(" jvm property.\n");
        hlp.append("    - ").append(EXAMPLE_NAME_ENV_VAR).append(" environment variable.\n");
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
        String exampleName;
        if (args.length > 0) {
            exampleName = args[0];
        } else if (System.getProperty(EXAMPLE_NAME_SYS_PROP) != null) {
            exampleName = System.getProperty(EXAMPLE_NAME_SYS_PROP);
        } else if (System.getenv(EXAMPLE_NAME_ENV_VAR) != null) {
            exampleName = System.getenv(EXAMPLE_NAME_ENV_VAR);
        } else {
            System.out.println("Missing example name. It can be provided as a \n"
                               + "    - first command line argument.\n"
                               + "    - -D" + EXAMPLE_NAME_SYS_PROP + " jvm property.\n"
                               + "    - " + EXAMPLE_NAME_ENV_VAR + " environment variable.\n");
            System.exit(1);
            return;
        }
        while (exampleName.startsWith("-")) {
            exampleName = exampleName.substring(1);
        }
        String methodName = exampleName;
        Method method = Arrays.stream(Main.class.getMethods())
                .filter(m -> m.getName().equals(methodName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Missing example method named: " + methodName));
        HttpRouting.Builder routingBuilder = HttpRouting.builder();
        MediaContextConfig.Builder mediaContextBuilder = MediaContext.builder()
                .mediaSupportsDiscoverServices(false);
        List<Object> params = new ArrayList<>();
        for (Parameter param : method.getParameters()) {
            Class<?> paramType = param.getType();
            if (paramType.isAssignableFrom(routingBuilder.getClass())) {
                params.add(routingBuilder);
            } else if (paramType.isAssignableFrom(mediaContextBuilder.getClass())) {
                params.add(mediaContextBuilder);
            } else {
                throw new IllegalStateException("Unsupported parameter type: " + paramType.getName());
            }
        }
        WebServer server;
        try {
            method.invoke(null, params.toArray(new Object[0]));
        } catch (IllegalArgumentException e) {
            System.out.println(e.getMessage());
            System.exit(2);
        } catch (IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace(System.out);
            System.exit(100);
        }
        server = WebServer.builder()
                .routing(routingBuilder)
                .mediaContext(mediaContextBuilder.build())
                .build()
                .start();
        System.out.println("Server is UP: http://localhost:" + server.port());
    }
}
