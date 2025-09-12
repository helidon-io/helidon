/*
 * Copyright (c) 2022, 2025 Oracle and/or its affiliates.
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

package io.helidon.webserver.tests;

import java.util.concurrent.atomic.AtomicBoolean;

import io.helidon.common.buffers.DataReader;
import io.helidon.http.Status;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * This is a reproducer test for issue 10647.
 * Before the associated fix, we would not log any details about an exception that has an error handler, but was thrown
 * after data was written.
 */
@ServerTest
public class ExceptionInOutputStreamTest {
    private final static AtomicBoolean EXCEPTION_HANDLER_INVOKED = new AtomicBoolean();
    private final Http1Client client;

    ExceptionInOutputStreamTest(Http1Client client) {
        this.client = client;
    }

    @BeforeAll
    static void beforeAll() {
        EXCEPTION_HANDLER_INVOKED.set(false);
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder builder) {
        builder.get("/error", (req, res) -> {
                    var os = res.outputStream();
                    os.write(1);
                    os.flush();
                    throw new CustomException();
                })
                .error(CustomException.class, (req, res, t) -> {
                    EXCEPTION_HANDLER_INVOKED.set(true);
                });
    }

    @Test
    void error() {
        try (var response = client.get("/error").request()) {
            assertThat(EXCEPTION_HANDLER_INVOKED.get(), is(false));
            assertThat(response.status(), is(Status.OK_200));
            assertThrows(DataReader.InsufficientDataAvailableException.class, () -> response.entity()
                    .as(String.class));
        }
    }

    private static class CustomException extends RuntimeException {
        public CustomException() {
            super();
        }
    }
}
