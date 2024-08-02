/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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
package io.helidon.tests.integration.dbclient.common;

import java.util.NoSuchElementException;

import io.helidon.http.Status;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientResponse;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * Base class for remote tests.
 */
public abstract class RemoteTest {
    private final Http1Client client;

    /**
     * Create a new instance.
     *
     * @param path base path
     * @param port port
     */
    protected RemoteTest(String path, int port) {
        client = Http1Client.builder()
                .baseUri("http://localhost:" + port + path)
                .build();
    }

    /**
     * Invoke a GET request against {@code /{path}/{testName}} and assert a {@code 200} response status.
     */
    protected void remoteTest() {
        String testName = findTestName();
        Http1ClientResponse response = client.get(testName).request();
        assertThat(response.status(), is(Status.OK_200));
    }

    private String findTestName() {
        return StackWalker.getInstance().walk(s ->
                s.dropWhile(f -> f.getClassName().equals(RemoteTest.class.getName()))
                        .findFirst()
                        .map(StackWalker.StackFrame::getMethodName)
                        .orElseThrow(() -> new NoSuchElementException("Unable to find caller method name")));
    }
}
