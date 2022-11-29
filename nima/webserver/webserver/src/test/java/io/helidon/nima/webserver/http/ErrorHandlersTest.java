/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.nima.webserver.http;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import io.helidon.common.http.Http;
import io.helidon.common.http.HttpPrologue;
import io.helidon.common.uri.UriFragment;
import io.helidon.common.uri.UriPath;
import io.helidon.common.uri.UriQuery;
import io.helidon.nima.http.media.ReadableEntityBase;
import io.helidon.nima.webserver.ConnectionContext;

import org.hamcrest.Matcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;

import static io.helidon.common.testing.junit5.OptionalMatcher.optionalEmpty;
import static io.helidon.common.testing.junit5.OptionalMatcher.optionalValue;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ErrorHandlersTest {
    static Stream<TestData> testData() {
        return Stream.of(
                new TestData(ErrorHandlers.create(Map.of()),
                             optionalEmpty(),
                             optionalEmpty(),
                             optionalEmpty(),
                             optionalEmpty(),
                             optionalEmpty(),
                             optionalEmpty()),
                new TestData(ErrorHandlers.create(Map.of(Throwable.class, new TestHandler<>("Throwable"))),
                             optionalValue(instanceOf(TestHandler.class)),
                             optionalValue(instanceOf(TestHandler.class)),
                             optionalValue(instanceOf(TestHandler.class)),
                             optionalValue(instanceOf(TestHandler.class)),
                             optionalValue(instanceOf(TestHandler.class)),
                             optionalValue(instanceOf(TestHandler.class))),
                new TestData(ErrorHandlers.create(Map.of(Exception.class, new TestHandler<>("Exception"))),
                             optionalEmpty(),
                             optionalValue(instanceOf(TestHandler.class)),
                             optionalValue(instanceOf(TestHandler.class)),
                             optionalValue(instanceOf(TestHandler.class)),
                             optionalValue(instanceOf(TestHandler.class)),
                             optionalValue(instanceOf(TestHandler.class))),
                new TestData(ErrorHandlers.create(Map.of(RuntimeException.class, new TestHandler<>("RuntimeException"))),
                             optionalEmpty(),
                             optionalEmpty(),
                             optionalValue(instanceOf(TestHandler.class)),
                             optionalValue(instanceOf(TestHandler.class)),
                             optionalValue(instanceOf(TestHandler.class)),
                             optionalEmpty()),
                new TestData(ErrorHandlers.create(Map.of(TopRuntimeException.class, new TestHandler<>("TopRuntimeException"))),
                             optionalEmpty(),
                             optionalEmpty(),
                             optionalEmpty(),
                             optionalValue(instanceOf(TestHandler.class)),
                             optionalValue(instanceOf(TestHandler.class)),
                             optionalEmpty()),
                new TestData(ErrorHandlers.create(Map.of(ChildRuntimeException.class,
                                                         new TestHandler<>("ChildRuntimeException"))),
                             optionalEmpty(),
                             optionalEmpty(),
                             optionalEmpty(),
                             optionalEmpty(),
                             optionalValue(instanceOf(TestHandler.class)),
                             optionalEmpty())
        );
    }

    @ParameterizedTest
    @MethodSource("testData")
    void testHandleFound(TestData testData) {
        ErrorHandlers handlers = testData.handlers();

        assertAll(
                () -> assertThat(handlers.errorHandler(Throwable.class), testData.tMatcher()),
                () -> assertThat(handlers.errorHandler(Exception.class), testData.eMatcher()),
                () -> assertThat(handlers.errorHandler(RuntimeException.class), testData.rtMatcher()),
                () -> assertThat(handlers.errorHandler(TopRuntimeException.class), testData.trtMatcher()),
                () -> assertThat(handlers.errorHandler(ChildRuntimeException.class), testData.crtMatcher()),
                () -> assertThat(handlers.errorHandler(OtherException.class), testData.oMatcher())
        );
    }

    @Test
    void testHandler() {
        ErrorHandlers handlers = ErrorHandlers.create(Map.of(TopRuntimeException.class,
                                                             (req, res, t) -> res.send(t.getMessage())));

        testHandler(handlers, new TopRuntimeException(), "Top");
        testHandler(handlers, new ChildRuntimeException(), "Child");
        testNoHandler(handlers, new OtherException(), "Other");
    }

    private void testNoHandler(ErrorHandlers handlers, Exception e, String message) {
        ServerRequest req = mock(ServerRequest.class);
        ServerResponse res = mock(ServerResponse.class);
        ConnectionContext ctx = mock(ConnectionContext.class);

        when(req.prologue()).thenReturn(HttpPrologue.create("http",
                                                            "1.0",
                                                            Http.Method.GET,
                                                            UriPath.create("/"),
                                                            UriQuery.empty(),
                                                            UriFragment.empty()));
        when(req.content()).thenReturn(ReadableEntityBase.empty());
        when(ctx.directHandlers()).thenReturn(DirectHandlers.builder().build());

        handlers.runWithErrorHandling(ctx, req, res, () -> {
            throw e;
        });

        var status = ArgumentCaptor.forClass(Http.Status.class);
        verify(res).status(status.capture());
        assertThat(status.getValue(), is(Http.Status.INTERNAL_SERVER_ERROR_500));

        var sent = ArgumentCaptor.forClass(byte[].class);
        verify(res).send(sent.capture());
        assertThat(sent.getValue(), is("Other".getBytes(StandardCharsets.UTF_8)));
    }

    private void testHandler(ErrorHandlers handlers, Exception e, String message) {
        ServerRequest req = mock(ServerRequest.class);
        ServerResponse res = mock(ServerResponse.class);
        ConnectionContext ctx = mock(ConnectionContext.class);

        handlers.runWithErrorHandling(ctx, req, res, () -> {
            throw e;
        });

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(res).send(captor.capture());
        assertThat(captor.getValue(), is(message));
    }

    private static class TopRuntimeException extends RuntimeException {
        private TopRuntimeException() {
            this("Top");
        }

        public TopRuntimeException(String message) {
            super(message);
        }
    }

    private static class ChildRuntimeException extends TopRuntimeException {
        private ChildRuntimeException() {
            super("Child");
        }
    }

    private static class OtherException extends Exception {
        public OtherException() {
            super("Other");
        }
    }

    private record TestData(ErrorHandlers handlers,
                            Matcher<Optional<ErrorHandler<Throwable>>> tMatcher,
                            Matcher<Optional<ErrorHandler<Exception>>> eMatcher,
                            Matcher<Optional<ErrorHandler<RuntimeException>>> rtMatcher,
                            Matcher<Optional<ErrorHandler<TopRuntimeException>>> trtMatcher,
                            Matcher<Optional<ErrorHandler<ChildRuntimeException>>> crtMatcher,
                            Matcher<Optional<ErrorHandler<OtherException>>> oMatcher) {
    }

    private static class TestHandler<T extends Throwable> implements ErrorHandler<T> {
        private final String message;

        TestHandler(String message) {
            this.message = message;
        }

        @Override
        public void handle(ServerRequest req, ServerResponse res, T throwable) {
        }

        @Override
        public String toString() {
            return message;
        }
    }
}