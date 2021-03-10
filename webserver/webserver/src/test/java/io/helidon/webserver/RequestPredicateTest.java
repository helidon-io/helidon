/*
 * Copyright (c) 2019, 2021 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.webserver;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
import io.helidon.common.http.Http;
import io.helidon.common.http.MediaType;
import io.helidon.webserver.RoutingTest.RoutingChecker;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static io.helidon.webserver.RoutingTest.mockResponse;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests {@link RequestPredicate}.
 */
public class RequestPredicateTest {

    @Test
    public void isOfMethod1() {
        final RoutingChecker checker = new RoutingChecker();
        Routing routing = Routing.builder()
                .any("/getOrPost", RequestPredicate.create()
                        .isOfMethod("GET", "POST")
                        .thenApply((req, resp) -> {
                            checker.handlerInvoked("methodFound");
                        }).otherwise((req, res) -> {
                            checker.handlerInvoked("methodNotFound");
                        }))
                .build();

        assertThrows(NullPointerException.class, () -> {
            RequestPredicate.create()
                    .isOfMethod((String[]) null);
        });

        Contexts.runInContext(Context.create(), () -> {
            routing.route(mockRequest("/getOrPost"), mockResponse());
            assertThat(checker.handlersInvoked(), is("methodFound"));

            checker.reset();
            routing.route(mockRequest("/getOrPost", Http.Method.PUT), mockResponse());
            assertThat(checker.handlersInvoked(), is("methodNotFound"));
        });
    }

    @Test
    public void isOfMethod2() {
        final RoutingChecker checker = new RoutingChecker();
        Routing routing = Routing.builder()
                .any("/getOrPost", RequestPredicate.create()
                        .isOfMethod(Http.Method.GET, Http.Method.POST)
                        .thenApply((req, resp) -> {
                            checker.handlerInvoked("methodFound");
                        }).otherwise((req, res) -> {
                            checker.handlerInvoked("methodNotFound");
                        }))
                .build();

        assertThrows(NullPointerException.class, () -> {
            RequestPredicate.create()
                    .isOfMethod((Http.Method[]) null);
        });

        Contexts.runInContext(Context.create(), () -> {
            routing.route(mockRequest("/getOrPost"), mockResponse());
            assertThat(checker.handlersInvoked(), is("methodFound"));

            checker.reset();
            routing.route(mockRequest("/getOrPost", Http.Method.PUT), mockResponse());
            assertThat(checker.handlersInvoked(), is("methodNotFound"));
        });
    }

    @Test
    public void containsHeader() {
        final RoutingChecker checker = new RoutingChecker();
        Routing routing = Routing.builder()
                .get("/exists", RequestPredicate.create()
                        .containsHeader("my-header")
                        .thenApply((req, resp) -> {
                            checker.handlerInvoked("headerFound");
                        }).otherwise((req, res) -> {
                            checker.handlerInvoked("headerNotFound");
                        }))
                .get("/valid", RequestPredicate.create()
                        .containsHeader("my-header", "abc"::equals)
                        .thenApply((req, resp) -> {
                            checker.handlerInvoked("headerIsValid");
                        }).otherwise((req, res) -> {
                            checker.handlerInvoked("headerIsNotValid");
                        }))
                .get("/equals", RequestPredicate.create()
                        .containsHeader("my-header", "abc")
                        .thenApply((req, resp) -> {
                            checker.handlerInvoked("headerIsEqual");
                        }).otherwise((req, res) -> {
                            checker.handlerInvoked("headerIsNotEqual");
                        }))
                .build();

        assertThrows(NullPointerException.class, () -> {
            RequestPredicate.create()
                    .containsHeader(null);
        });

        Contexts.runInContext(Context.create(), () -> {
            routing.route(mockRequest("/exists", Map.of("my-header", List.of("abc"))),
                          mockResponse());
            assertThat(checker.handlersInvoked(), is("headerFound"));

            checker.reset();
            routing.route(mockRequest("/exists", Map.of()), mockResponse());
            assertThat(checker.handlersInvoked(), is("headerNotFound"));
        });
    }

    @Test
    public void containsValidHeader() {
        final RoutingChecker checker = new RoutingChecker();
        Routing routing = Routing.builder()
                .get("/valid", RequestPredicate.create()
                        .containsHeader("my-header", "abc"::equals)
                        .thenApply((req, resp) -> {
                            checker.handlerInvoked("headerIsValid");
                        }).otherwise((req, res) -> {
                            checker.handlerInvoked("headerIsNotValid");
                        }))
                .build();

        assertThrows(NullPointerException.class, () -> {
            RequestPredicate.create()
                    .containsHeader("my-header", (Predicate<String>) null);
        });

        assertThrows(NullPointerException.class, () -> {
            RequestPredicate.create()
                    .containsHeader(null, "abc"::equals);
        });

        Contexts.runInContext(Context.create(), () -> {
            routing.route(mockRequest("/valid", Map.of("my-header", List.of("abc"))),
                          mockResponse());
            assertThat(checker.handlersInvoked(), is("headerIsValid"));

            checker.reset();
            routing.route(mockRequest("/valid", Map.of("my-header", List.of("def"))),
                          mockResponse());
            assertThat(checker.handlersInvoked(), is("headerIsNotValid"));
        });
    }

    @Test
    public void containsExactHeader() {
        final RoutingChecker checker = new RoutingChecker();
        Routing routing = Routing.builder()
                .get("/equals", RequestPredicate.create()
                        .containsHeader("my-header", "abc")
                        .thenApply((req, resp) -> {
                            checker.handlerInvoked("headerIsEqual");
                        }).otherwise((req, res) -> {
                            checker.handlerInvoked("headerIsNotEqual");
                        }))
                .build();

        assertThrows(NullPointerException.class, () -> {
            RequestPredicate.create()
                    .containsHeader("my-header", (String) null);
        });

        assertThrows(NullPointerException.class, () -> {
            RequestPredicate.create()
                    .containsHeader(null, "abc");
        });

        Contexts.runInContext(Context.create(), () -> {
            routing.route(mockRequest("/equals", Map.of("my-header", List.of("abc"))),
                          mockResponse());
            assertThat(checker.handlersInvoked(), is("headerIsEqual"));

            checker.reset();
            routing.route(mockRequest("/equals", Map.of("my-header", List.of("def"))),
                          mockResponse());
            assertThat(checker.handlersInvoked(), is("headerIsNotEqual"));
        });
    }

    @Test
    public void containsQueryParam() {
        final RoutingChecker checker = new RoutingChecker();
        Routing routing = Routing.builder()
                .get("/exists", RequestPredicate.create()
                        .containsQueryParameter("my-param")
                        .thenApply((req, resp) -> {
                            checker.handlerInvoked("queryParamFound");
                        }).otherwise((req, res) -> {
                            checker.handlerInvoked("queryParamNotFound");
                        }))
                .get("/valid", RequestPredicate.create()
                        .containsQueryParameter("my-param", "abc"::equals)
                        .thenApply((req, resp) -> {
                            checker.handlerInvoked("queryParamIsValid");
                        }).otherwise((req, res) -> {
                            checker.handlerInvoked("queryParamIsNotValid");
                        }))
                .get("/equals", RequestPredicate.create()
                        .containsQueryParameter("my-param", "abc")
                        .thenApply((req, resp) -> {
                            checker.handlerInvoked("queryParamIsEqual");
                        }).otherwise((req, res) -> {
                            checker.handlerInvoked("queryParamIsNotEqual");
                        }))
                .build();

        assertThrows(NullPointerException.class, () -> {
            RequestPredicate.create()
                    .containsQueryParameter(null);
        });

        Contexts.runInContext(Context.create(), () -> {
            routing.route(mockRequest("/exists?my-param=abc"), mockResponse());
            assertThat(checker.handlersInvoked(), is("queryParamFound"));

            checker.reset();
            routing.route(mockRequest("/exists"), mockResponse());
            assertThat(checker.handlersInvoked(), is("queryParamNotFound"));
        });
    }

    @Test
    public void containsValidQueryParam() {
        final RoutingChecker checker = new RoutingChecker();
        Routing routing = Routing.builder()
                .get("/valid", RequestPredicate.create()
                        .containsQueryParameter("my-param", "abc"::equals)
                        .thenApply((req, resp) -> {
                            checker.handlerInvoked("queryParamIsValid");
                        }).otherwise((req, res) -> {
                            checker.handlerInvoked("queryParamIsNotValid");
                        }))
                .build();

        assertThrows(NullPointerException.class, () -> {
            RequestPredicate.create()
                    .containsQueryParameter("my-param", (Predicate<String>) null);
        });

        assertThrows(NullPointerException.class, () -> {
            RequestPredicate.create()
                    .containsQueryParameter(null, "abc");
        });

        Contexts.runInContext(Context.create(), () -> {
            routing.route(mockRequest("/valid?my-param=abc"), mockResponse());
            assertThat(checker.handlersInvoked(), is("queryParamIsValid"));

            checker.reset();
            routing.route(mockRequest("/valid?my-param=def"), mockResponse());
            assertThat(checker.handlersInvoked(), is("queryParamIsNotValid"));
        });
    }

    @Test
    public void containsExactQueryParam() {
        final RoutingChecker checker = new RoutingChecker();
        Routing routing = Routing.builder()
                .get("/equals", RequestPredicate.create()
                        .containsQueryParameter("my-param", "abc")
                        .thenApply((req, resp) -> {
                            checker.handlerInvoked("queryParamIsEqual");
                        }).otherwise((req, res) -> {
                            checker.handlerInvoked("queryParamIsNotEqual");
                        }))
                .build();

        assertThrows(NullPointerException.class, () -> {
            RequestPredicate.create()
                    .containsQueryParameter("my-param", (String) null);
        });

        assertThrows(NullPointerException.class, () -> {
            RequestPredicate.create()
                    .containsQueryParameter(null, "abc");
        });

        Contexts.runInContext(Context.create(), () -> {
            routing.route(mockRequest("/equals?my-param=abc"), mockResponse());
            assertThat(checker.handlersInvoked(), is("queryParamIsEqual"));

            checker.reset();
            routing.route(mockRequest("/equals?my-param=def"), mockResponse());
            assertThat(checker.handlersInvoked(), is("queryParamIsNotEqual"));
        });
    }

    @Test
    public void containsCookie() {
        final RoutingChecker checker = new RoutingChecker();
        Routing routing = Routing.builder()
                .get("/exists", RequestPredicate.create()
                        .containsCookie("my-cookie")
                        .thenApply((req, resp) -> {
                            checker.handlerInvoked("cookieFound");
                        }).otherwise((req, res) -> {
                            checker.handlerInvoked("cookieNotFound");
                        }))
                .build();

        assertThrows(NullPointerException.class, () -> {
            RequestPredicate.create()
                    .containsCookie(null);
        });

        Contexts.runInContext(Context.create(), () -> {
            routing.route(mockRequest("/exists", Map.of("cookie",
                                                        List.of("my-cookie=abc"))), mockResponse());
            assertThat(checker.handlersInvoked(), is("cookieFound"));

            checker.reset();
            routing.route(mockRequest("/exists", Map.of("cookie",
                                                        List.of("other-cookie=abc"))), mockResponse());
            assertThat(checker.handlersInvoked(), is("cookieNotFound"));
        });
    }

    @Test
    public void containsValidCookie() {
        final RoutingChecker checker = new RoutingChecker();
        Routing routing = Routing.builder()
                .get("/valid", RequestPredicate.create()
                        .containsCookie("my-cookie", "abc"::equals)
                        .thenApply((req, resp) -> {
                            checker.handlerInvoked("cookieIsValid");
                        }).otherwise((req, res) -> {
                            checker.handlerInvoked("cookieIsNotValid");
                        }))
                .build();

        assertThrows(NullPointerException.class, () -> {
            RequestPredicate.create()
                    .containsCookie("my-cookie", (Predicate<String>) null);
        });

        assertThrows(NullPointerException.class, () -> {
            RequestPredicate.create()
                    .containsCookie(null, "abc");
        });

        Contexts.runInContext(Context.create(), () -> {
            routing.route(mockRequest("/valid", Map.of("cookie",
                                                       List.of("my-cookie=abc"))), mockResponse());
            assertThat(checker.handlersInvoked(), is("cookieIsValid"));

            checker.reset();
            routing.route(mockRequest("/valid", Map.of("cookie",
                                                       List.of("my-cookie=def"))), mockResponse());
            assertThat(checker.handlersInvoked(), is("cookieIsNotValid"));

            checker.reset();
            routing.route(mockRequest("/valid", Map.of("cookie",
                                                       List.of("my-cookie="))), mockResponse());
            assertThat(checker.handlersInvoked(), is("cookieIsNotValid"));
        });
    }

    @Test
    public void containsExactCookie() {
        final RoutingChecker checker = new RoutingChecker();
        Routing routing = Routing.builder()
                .get("/equals", RequestPredicate.create()
                        .containsCookie("my-cookie", "abc")
                        .thenApply((req, resp) -> {
                            checker.handlerInvoked("cookieIsEqual");
                        }).otherwise((req, res) -> {
                            checker.handlerInvoked("cookieIsNotEqual");
                        }))
                .build();

        assertThrows(NullPointerException.class, () -> {
            RequestPredicate.create()
                    .containsCookie("my-cookie", (String) null);
        });

        assertThrows(NullPointerException.class, () -> {
            RequestPredicate.create()
                    .containsCookie(null, "abc");
        });

        Contexts.runInContext(Context.create(), () -> {
            routing.route(mockRequest("/equals", Map.of("cookie",
                                                        List.of("my-cookie=abc"))), mockResponse());
            assertThat(checker.handlersInvoked(), is("cookieIsEqual"));

            checker.reset();
            routing.route(mockRequest("/equals", Map.of("cookie",
                                                        List.of("my-cookie=def"))), mockResponse());
            assertThat(checker.handlersInvoked(), is("cookieIsNotEqual"));
        });
    }

    @Test
    public void accepts1() {
        final RoutingChecker checker = new RoutingChecker();
        Routing routing = Routing.builder()
                .get("/accepts1", RequestPredicate.create()
                        .accepts("text/plain", "application/json")
                        .thenApply((req, resp) -> {
                            checker.handlerInvoked("acceptsMediaType");
                        }).otherwise((req, res) -> {
                            checker.handlerInvoked("doesNotAcceptMediaType");
                        }))
                .build();

        assertThrows(NullPointerException.class, () -> {
            RequestPredicate.create()
                    .accepts((String[]) null);
        });

        Contexts.runInContext(Context.create(), () -> {
            routing.route(mockRequest("/accepts1", Map.of("Accept",
                                                          List.of("application/json"))), mockResponse());
            assertThat(checker.handlersInvoked(), is("acceptsMediaType"));

            checker.reset();
            routing.route(mockRequest("/accepts1", Map.of("Accept",
                                                          List.of("text/plain"))), mockResponse());
            assertThat(checker.handlersInvoked(), is("acceptsMediaType"));

            checker.reset();
            routing.route(mockRequest("/accepts1", Map.of("Accept",
                                                          List.of("text/plain", "application/xml"))), mockResponse());
            assertThat(checker.handlersInvoked(), is("acceptsMediaType"));

            checker.reset();
            routing.route(mockRequest("/accepts1", Map.of("Accept", List.of())),
                          mockResponse());
            assertThat(checker.handlersInvoked(), is("acceptsMediaType"));

            checker.reset();
            routing.route(mockRequest("/accepts1", Map.of()), mockResponse());
            assertThat(checker.handlersInvoked(), is("acceptsMediaType"));

            checker.reset();
            routing.route(mockRequest("/accepts1", Map.of("Accept",
                                                          List.of("application/xml"))), mockResponse());
            assertThat(checker.handlersInvoked(), is("doesNotAcceptMediaType"));
        });
    }

    @Test
    public void accepts2() {
        final RoutingChecker checker = new RoutingChecker();
        Routing routing = Routing.builder()
                .get("/accepts2", RequestPredicate.create()
                        .accepts(MediaType.TEXT_PLAIN,
                                 MediaType.APPLICATION_JSON)
                        .thenApply((req, resp) -> {
                            checker.handlerInvoked("acceptsMediaType");
                        }).otherwise((req, res) -> {
                            checker.handlerInvoked("doesNotAcceptMediaType");
                        }))
                .build();

        assertThrows(NullPointerException.class, () -> {
            RequestPredicate.create()
                    .accepts((MediaType[]) null);
        });

        Contexts.runInContext(Context.create(), () -> {
            routing.route(mockRequest("/accepts2", Map.of("Accept",
                                                          List.of("application/json"))), mockResponse());
            assertThat(checker.handlersInvoked(), is("acceptsMediaType"));

            checker.reset();
            routing.route(mockRequest("/accepts2", Map.of("Accept",
                                                          List.of("text/plain"))), mockResponse());
            assertThat(checker.handlersInvoked(), is("acceptsMediaType"));

            checker.reset();
            routing.route(mockRequest("/accepts2", Map.of("Accept",
                                                          List.of("text/plain", "application/xml"))), mockResponse());
            assertThat(checker.handlersInvoked(), is("acceptsMediaType"));

            checker.reset();
            routing.route(mockRequest("/accepts2", Map.of("Accept", List.of())),
                          mockResponse());
            assertThat(checker.handlersInvoked(), is("acceptsMediaType"));

            checker.reset();
            routing.route(mockRequest("/accepts2", Map.of()), mockResponse());
            assertThat(checker.handlersInvoked(), is("acceptsMediaType"));

            checker.reset();
            routing.route(mockRequest("/accepts2", Map.of("Accept",
                                                          List.of("application/xml"))), mockResponse());
            assertThat(checker.handlersInvoked(), is("doesNotAcceptMediaType"));
        });
    }

    @Test
    public void hasContentType1() {
        final RoutingChecker checker = new RoutingChecker();
        Routing routing = Routing.builder()
                .get("/contentType1", RequestPredicate.create()
                        .hasContentType("text/plain", "application/json")
                        .thenApply((req, resp) -> {
                            checker.handlerInvoked("hasContentType");
                        }).otherwise((req, res) -> {
                            checker.handlerInvoked("doesNotHaveContentType");
                        }))
                .build();

        assertThrows(NullPointerException.class, () -> {
            RequestPredicate.create()
                    .hasContentType((String[]) null);
        });

        Contexts.runInContext(Context.create(), () -> {
            routing.route(mockRequest("/contentType1", Map.of("Content-Type",
                                                              List.of("text/plain"))), mockResponse());
            assertThat(checker.handlersInvoked(), is("hasContentType"));

            checker.reset();
            routing.route(mockRequest("/contentType1", Map.of("Content-Type",
                                                              List.of("text/plain"))), mockResponse());
            assertThat(checker.handlersInvoked(), is("hasContentType"));

            checker.reset();
            routing.route(mockRequest("/contentType1", Map.of("Content-Type",
                                                              List.of("application/json"))), mockResponse());
            assertThat(checker.handlersInvoked(), is("hasContentType"));

            checker.reset();
            routing.route(mockRequest("/contentType1", Map.of("Content-Type",
                                                              List.of("application/xml"))), mockResponse());
            assertThat(checker.handlersInvoked(), is("doesNotHaveContentType"));

            checker.reset();
            routing.route(mockRequest("/contentType1", Map.of("Content-Type",
                                                              List.of())), mockResponse());
            assertThat(checker.handlersInvoked(), is("doesNotHaveContentType"));

            checker.reset();
            routing.route(mockRequest("/contentType1", Map.of()), mockResponse());
            assertThat(checker.handlersInvoked(), is("doesNotHaveContentType"));
        });
    }

    @Test
    public void hasContentType2() {
        final RoutingChecker checker = new RoutingChecker();
        Routing routing = Routing.builder()
                .get("/contentType2", RequestPredicate.create()
                        .hasContentType(MediaType.TEXT_PLAIN,
                                        MediaType.APPLICATION_JSON)
                        .thenApply((req, resp) -> {
                            checker.handlerInvoked("hasContentType");
                        }).otherwise((req, res) -> {
                            checker.handlerInvoked("doesNotHaveContentType");
                        }))
                .build();

        assertThrows(NullPointerException.class, () -> {
            RequestPredicate.create()
                    .hasContentType((MediaType[]) null);
        });

        Contexts.runInContext(Context.create(), () -> {
            routing.route(mockRequest("/contentType2", Map.of("Content-Type",
                                                              List.of("text/plain"))), mockResponse());
            assertThat(checker.handlersInvoked(), is("hasContentType"));

            checker.reset();
            routing.route(mockRequest("/contentType2", Map.of("Content-Type",
                                                              List.of("text/plain"))), mockResponse());
            assertThat(checker.handlersInvoked(), is("hasContentType"));

            checker.reset();
            routing.route(mockRequest("/contentType2", Map.of("Content-Type",
                                                              List.of("application/json"))), mockResponse());
            assertThat(checker.handlersInvoked(), is("hasContentType"));

            checker.reset();
            routing.route(mockRequest("/contentType2", Map.of("Content-Type",
                                                              List.of("application/xml"))), mockResponse());
            assertThat(checker.handlersInvoked(), is("doesNotHaveContentType"));

            checker.reset();
            routing.route(mockRequest("/contentType2", Map.of("Content-Type",
                                                              List.of())), mockResponse());
            assertThat(checker.handlersInvoked(), is("doesNotHaveContentType"));

            checker.reset();
            routing.route(mockRequest("/contentType2", Map.of()), mockResponse());
            assertThat(checker.handlersInvoked(), is("doesNotHaveContentType"));
        });
    }

    @Test
    public void multipleConditions() {
        final RoutingChecker checker = new RoutingChecker();
        Routing routing = Routing.builder()
                .any("/multiple", RequestPredicate.create()
                        .accepts(MediaType.TEXT_PLAIN)
                        .hasContentType(MediaType.TEXT_PLAIN)
                        .containsQueryParameter("my-param")
                        .containsCookie("my-cookie")
                        .isOfMethod(Http.Method.GET)
                        .thenApply((req, resp) -> {
                            checker.handlerInvoked("hasAllConditions");
                        }).otherwise((req, res) -> {
                            checker.handlerInvoked("doesNotHaveAllConditions");
                        }))
                .build();
        Contexts.runInContext(Context.create(), () -> {
            routing.route(mockRequest("/multiple?my-param=abc",
                                      Map.of("Content-Type", List.of("text/plain"),
                                             "Accept", List.of("text/plain"),
                                             "Cookie", List.of("my-cookie=abc"))), mockResponse());
            assertThat(checker.handlersInvoked(), is("hasAllConditions"));

            checker.reset();
            routing.route(mockRequest("/multiple?my-param=abc",
                                      Map.of("Accept", List.of("text/plain"),
                                             "Cookie", List.of("my-cookie=abc"))), mockResponse());
            assertThat(checker.handlersInvoked(), is("doesNotHaveAllConditions"));
        });
    }

    @Test
    public void and() {
        final RoutingChecker checker = new RoutingChecker();
        Routing routing = Routing.builder()
                .get("/and", RequestPredicate.create()
                        .accepts(MediaType.TEXT_PLAIN)
                        .and((req) -> req.headers().first("my-header").isPresent())
                        .thenApply((req, resp) -> {
                            checker.handlerInvoked("hasAllConditions");
                        }).otherwise((req, res) -> {
                            checker.handlerInvoked("doesNotHaveAllConditions");
                        }))
                .build();
        Contexts.runInContext(Context.create(), () -> {
            routing.route(mockRequest("/and",
                                      Map.of("Accept", List.of("text/plain"),
                                             "my-header", List.of("abc"))), mockResponse());
            assertThat(checker.handlersInvoked(), is("hasAllConditions"));

            checker.reset();
            routing.route(mockRequest("/and",
                                      Map.of("Accept", List.of("text/plain"))), mockResponse());
            assertThat(checker.handlersInvoked(), is("doesNotHaveAllConditions"));
        });
    }

    @Test
    public void or() {
        final RoutingChecker checker = new RoutingChecker();
        Routing routing = Routing.builder()
                .get("/or", RequestPredicate.create()
                        .hasContentType(MediaType.TEXT_PLAIN)
                        .or((req) -> req.headers().first("my-header").isPresent())
                        .thenApply((req, resp) -> {
                            checker.handlerInvoked("hasAnyCondition");
                        }).otherwise((req, res) -> {
                            checker.handlerInvoked("doesNotHaveAnyCondition");
                        }))
                .build();
        Contexts.runInContext(Context.create(), () -> {
            routing.route(mockRequest("/or",
                                      Map.of("Content-Type", List.of("text/plain"))), mockResponse());
            assertThat(checker.handlersInvoked(), is("hasAnyCondition"));

            checker.reset();
            routing.route(mockRequest("/or",
                                      Map.of("my-header", List.of("abc"))), mockResponse());
            assertThat(checker.handlersInvoked(), is("hasAnyCondition"));

            checker.reset();
            routing.route(mockRequest("/or", Map.of()), mockResponse());
            assertThat(checker.handlersInvoked(), is("doesNotHaveAnyCondition"));
        });
    }

    @Test
    public void negate() {
        final RoutingChecker checker = new RoutingChecker();
        Routing routing = Routing.builder()
                .get("/negate", RequestPredicate.create()
                        .hasContentType(MediaType.TEXT_PLAIN)
                        .containsCookie("my-cookie")
                        .negate()
                        .thenApply((req, resp) -> {
                            checker.handlerInvoked("hasAllConditions");
                        }).otherwise((req, res) -> {
                            checker.handlerInvoked("doesNotHaveAllConditions");
                        }))
                .build();
        Contexts.runInContext(Context.create(), () -> {
            routing.route(mockRequest("/negate",
                                      Map.of("Content-Type", List.of("application/json"))), mockResponse());
            assertThat(checker.handlersInvoked(), is("hasAllConditions"));

            checker.reset();
            routing.route(mockRequest("/negate",
                                      Map.of("Content-Type", List.of("text/plain"),
                                             "Cookie", List.of("my-cookie=abc"))), mockResponse());
            assertThat(checker.handlersInvoked(), is("doesNotHaveAllConditions"));
        });
    }

    @Test
    public void nextAlreadySet() {
        RequestPredicate requestPredicate = RequestPredicate.create()
                .containsCookie("my-cookie");
        requestPredicate.containsHeader("my-header");
        assertThrows(IllegalStateException.class, () -> {
            requestPredicate.containsHeader("my-param");
        });
    }

    private static BareRequest mockRequest(final String path) {
        BareRequest bareRequestMock = RoutingTest.mockRequest(path,
                                                              Http.Method.GET);
        return bareRequestMock;
    }

    private static BareRequest mockRequest(final String path,
                                           final Http.Method method) {

        BareRequest bareRequestMock = RoutingTest.mockRequest(path, method);
        return bareRequestMock;
    }

    private static BareRequest mockRequest(final String path,
                                           final Map<String, List<String>> headers) {

        BareRequest bareRequestMock = RoutingTest.mockRequest(path,
                                                              Http.Method.GET);
        Mockito.doReturn(headers).when(bareRequestMock).headers();
        return bareRequestMock;
    }
}
