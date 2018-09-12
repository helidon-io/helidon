/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.webserver.testsupport;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.helidon.common.http.Http;
import io.helidon.webserver.HttpException;
import io.helidon.webserver.NotFoundException;
import io.helidon.webserver.Routing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;


/**
 * Tests {@link TestClient}.
 */
public class TestClientTest {

    @Test
    public void singleHandlerTest() throws Exception {
        StringBuffer sb = new StringBuffer();
        Routing routing = Routing.builder()
                                 .get("/foo", (req, res) -> {
                                     sb.append("a");
                                     res.send();
                                 })
                                 .build();
        TestResponse response = TestClient.create(routing)
                .path("/foo")
                .get();
        assertEquals(Http.Status.OK_200, response.status());
        assertEquals("a", sb.toString());
    }

    @Test
    public void multipleHandlers() throws Exception {
        StringBuffer sb = new StringBuffer();
        Routing routing = Routing.builder()
                .get("/foo", (req, res) -> {
                    sb.append("foo-get");
                    res.send();
                })
                .post("/foo", (req, res) -> {
                    sb.append("foo-post");
                    res.send();
                })
                .put("/foo", (req, res) -> {
                    sb.append("foo-put");
                    res.send();
                })
                .put("/foo", (req, res) -> {
                    sb.append("foo-putb");
                    res.send();
                })
                .get("/foo/bar", (req, res) -> {
                    sb.append("foo/bar-get");
                    res.send();
                })
                .build();
        TestResponse response = TestClient.create(routing)
                .path("/foo")
                .get();
        assertEquals("foo-get", sb.toString());
        sb.setLength(0);
        response = TestClient.create(routing)
                .path("/foo")
                .post();
        assertEquals("foo-post", sb.toString());
        sb.setLength(0);
        response = TestClient.create(routing)
                .path("/foo")
                .put();
        assertEquals("foo-put", sb.toString());
        sb.setLength(0);
        response = TestClient.create(routing)
                .path("/foo/bar")
                .get();
        assertEquals("foo/bar-get", sb.toString());
    }

    @Test
    public void subRoutes() throws Exception {
        StringBuffer sb = new StringBuffer();
        Routing routing = Routing.builder()
                .get("/foo/{name}", (req, res) -> {
                    sb.append("foo-get:").append(req.path().param("name"));
                    res.send();
                })
                .register("/bar/{name}", config -> {
                    config
                            .get("/baz", (req, res) -> {
                                sb.append("baz-get:")
                                        .append(req.path().absolute().param("name"));
                                res.send();
                            })
                            .get("/{id}", (req, res) -> {
                                sb.append("bar-get:")
                                        .append(req.path().absolute().param("name"))
                                        .append(':')
                                        .append(req.path().param("id"));
                                res.send();
                            });
                })
                .post("/foo/{name}", (req, res) -> {
                    sb.append("foo-post:").append(req.path().param("name"));
                    res.send();
                })
                .build();
        TestResponse response = TestClient.create(routing)
                .path("/foo/a")
                .get();
        assertEquals("foo-get:a", sb.toString());
        sb.setLength(0);
        response = TestClient.create(routing)
                .path("/foo/a")
                .post();
        assertEquals("foo-post:a", sb.toString());
        sb.setLength(0);
        response = TestClient.create(routing)
                .path("/bar/n/baz")
                .get();
        assertEquals("baz-get:n", sb.toString());
        sb.setLength(0);
        response = TestClient.create(routing)
                .path("/bar/n/kuk")
                .get();
        assertEquals("bar-get:n:kuk", sb.toString());
    }

    @Test
    public void filtering() throws Exception {
        StringBuffer sb = new StringBuffer();
        Routing routing = Routing.builder()
                .any((req, res) -> {
                    sb.append("A-");
                    req.next();
                })
                .get("/foo", (req, res) -> {
                    sb.append("foo-get");
                    res.send();
                })
                .register(config -> {
                    config.get("/bar", (req, res) -> {
                        sb.append("B-");
                        req.next();
                    });
                })
                .get("/bar", (req, res) -> {
                    sb.append("bar-get");
                    res.send();
                })
                .build();
        TestResponse response = TestClient.create(routing)
                .path("/foo")
                .get();
        assertEquals("A-foo-get", sb.toString());
        sb.setLength(0);
        response = TestClient.create(routing)
                .path("/bar")
                .get();
        assertEquals("A-B-bar-get", sb.toString());
    }

    @Test
    public void exceptionalDefaultErrorHandling() throws Exception {
        errorHandling(new RuntimeException("test-exception"), Http.Status.INTERNAL_SERVER_ERROR_500, true);
    }
    @Test
    public void exceptionalNotFoundErrorHandling() throws Exception {
        errorHandling(new NotFoundException("test-exception"), Http.Status.NOT_FOUND_404, true);
    }

    @Test
    public void explicitDefaultErrorHandling() throws Exception {
        errorHandling(new RuntimeException("test-exception"), Http.Status.INTERNAL_SERVER_ERROR_500, false);
    }
    @Test
    public void explicitNotFoundErrorHandling() throws Exception {
        errorHandling(new NotFoundException("test-exception"), Http.Status.NOT_FOUND_404, false);
    }

    void errorHandling(RuntimeException exception, Http.Status status, boolean doThrow) throws Exception {

        Routing routing = Routing.builder()
                .any((req, res) -> {
                    if (doThrow) {
                        throw exception;
                    } else {
                        req.next(exception);
                    }
                })
                .build();
        TestResponse response = TestClient.create(routing)
                .path("/anything/anywhere")
                .get();

        assertEquals(status, response.status());
    }


    private static class MyTestException extends RuntimeException {
        public MyTestException(String message) {
            super(message);
        }
    }

    @Test
    public void userDefinedErrorHandling() throws Exception {
        StringBuffer sb = new StringBuffer();
        MyTestException exception = new MyTestException("test-exception");
        CountDownLatch latch = new CountDownLatch(1);

        Routing routing = Routing.builder()
                .any((req, res) -> {
                    sb.append("any-");
                    req.next(exception);
                })
                .error(IllegalStateException.class, (req, res, ex) -> {
                    fail("Should not be called");
                })
                .error(MyTestException.class, (req, res, ex) -> {
                    sb.append(ex.getMessage());
                    res.status(417);
                    res.send().whenComplete((serverResponse, throwable) -> {
                        sb.append("-complete");
                        latch.countDown();
                    });
                })
                .error(RuntimeException.class, (req, res, ex) -> {
                    fail("Should not be called");
                })
                .build();
        TestResponse response = TestClient.create(routing)
                .path("/anything/anywhere")
                .get();


        latch.await(10, TimeUnit.SECONDS);

        assertEquals("any-test-exception-complete", sb.toString());
        assertEquals(417, response.status().code());
    }

    @Test
    public void implicitNotFound() throws Exception {
        Routing routing = Routing.builder()
                .any("/", (req, res) -> {
                    fail("The handler for '/' is not expected to be matched");
                })
                .build();
        TestResponse response = TestClient.create(routing)
                .path("/nonexisting")
                .get();

        assertEquals(Http.Status.NOT_FOUND_404, response.status());
    }

    @Test
    public void advancingToDefaultErrorHandler() throws Exception {
        StringBuffer sb = new StringBuffer();
        HttpException exception = new HttpException("test-exception", Http.ResponseStatus.from(777));

        Routing routing = Routing.builder()
                .any((req, res) -> {
                    sb.append("any-");
                    req.next(exception);
                })
                .error(IllegalStateException.class, (req, res, ex) -> {
                    fail("Should not be called");
                })
                .error(Exception.class, (req, res, ex) -> {
                    sb.append("exceptionHandler-");
                    req.next(ex);
                })
                .error(IllegalArgumentException.class, (req, res, ex) -> {
                    fail("Should not be called");
                })
                .error(HttpException.class, (req, res, ex) -> {
                    sb.append("httpExceptionHandler-");
                    req.next();
                })
                .error(Throwable.class, (req, res, ex) -> {
                    sb.append("throwableHandler");
                    req.next();
                })
                .build();
        TestResponse response = TestClient.create(routing)
                .path("/anything/anywhere")
                .get();

        assertEquals("any-exceptionHandler-httpExceptionHandler-throwableHandler", sb.toString());
        assertEquals(777, response.status().code());
    }

    @Test
    public void throwingExceptionInErrorHandler() throws Exception {
        StringBuffer sb = new StringBuffer();

        Routing routing = Routing.builder()
                .any((req, res) -> {
                    sb.append("any-");
                    throw new HttpException("original-exception", Http.ResponseStatus.from(777));
                })
                .error(HttpException.class, (req, res, ex) -> {
                    sb.append("httpExceptionHandler-");
                    throw new HttpException("unexpected-exception", Http.ResponseStatus.from(888));
                })
                .error(Throwable.class, (req, res, ex) -> {
                    fail("The rest of the handlers were supposed to be skipped due to an unexpected exception being thrown "
                                 + "from the error handler.");
                    sb.append("throwableHandler-");
                    sb.append(ex.getMessage());
                    req.next();
                })
                .build();
        TestResponse response = TestClient.create(routing)
                .path("/anything/anywhere")
                .get();

        assertEquals("any-httpExceptionHandler-", sb.toString());
        assertEquals(Http.Status.INTERNAL_SERVER_ERROR_500, response.status());
    }

    @Test
    public void nextInErrorWhenHeadersSent() throws Exception {
        StringBuffer sb = new StringBuffer();

        Routing routing = Routing.builder()
                .any((req, res) -> {
                    sb.append("any-");
                    res.status(300);
                    try {
                        res.send().toCompletableFuture().get();
                    } catch (Exception e) {
                        fail("Should not have gotten an exception.");
                    }
                    throw new HttpException("test-exception", Http.ResponseStatus.from(400));
                })
                .error(Throwable.class, (req, res, ex) -> {
                    sb.append("throwableHandler");
                    if (!res.headers().whenSend().toCompletableFuture().isDone()) {
                        fail("Headers weren't send as expected!");
                    }
                    req.next();
                })
                .build();
        TestResponse response = TestClient.create(routing)
                .path("/anything/anywhere")
                .get();

        assertEquals("any-throwableHandler", sb.toString());
        assertEquals(300, response.status().code());
    }

    @Test
    public void multipleErrorEndingRequests() throws Exception {
        StringBuffer sb = new StringBuffer();

        Routing routing = Routing.builder()
                .any((req, res) -> {
                    sb.append("any-");

            switch (req.queryParams().first("a").orElse("")) {
            case "IllegalArgumentException":
                throw new IllegalArgumentException();
            case "IllegalStateException":
                throw new IllegalStateException();
            }
        })
        .error(Throwable.class, (req, res, ex) -> {
            sb.append("throwableHandler-");
            req.next();
        })
        .error(NumberFormatException.class, (req, res, ex) -> {
            fail("Should not be called!");
        })
        .error(IllegalArgumentException.class, (req, res, ex) -> {
            sb.append("IllegalArgumentExceptionHandler-");
            req.next();
        })
        .error(IllegalStateException.class, (req, res, ex) -> {
            sb.append("IllegalStateExceptionHandler-");
            req.next();
        })
        .build();
        TestResponse responseIse = TestClient.create(routing)
                .path("/1")
                .queryParameter("a", IllegalStateException.class.getSimpleName())
                .get();

        TestResponse responseIae = TestClient.create(routing)
                .path("/2")
                .queryParameter("a", IllegalArgumentException.class.getSimpleName())
                .get();

        assertEquals("any-throwableHandler-IllegalStateExceptionHandler-any-throwableHandler-"
                             + "IllegalArgumentExceptionHandler-", sb.toString());
        assertEquals(Http.Status.INTERNAL_SERVER_ERROR_500, responseIse.status());
        assertEquals(Http.Status.INTERNAL_SERVER_ERROR_500, responseIae.status());
    }
}
