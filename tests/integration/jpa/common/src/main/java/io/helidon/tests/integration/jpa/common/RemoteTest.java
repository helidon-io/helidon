/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
package io.helidon.tests.integration.jpa.common;

import java.util.NoSuchElementException;

import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Response;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * Base class for remote tests.
 */
public abstract class RemoteTest {
    private final WebTarget target;

    /**
     * Create a new instance.
     *
     * @param path base path
     * @param port port
     */
    @SuppressWarnings("resource")
    protected RemoteTest(String path, int port) {
        target = ClientBuilder.newClient()
                .target("http://localhost:" + port)
                .path(path);
    }

    /**
     * Invoke a GET request against {@code /{path}/{testName}} and assert a {@code 200} response status.
     */
    protected void remoteTest() {
        String testName = findTestName();
        Response response = target.path(testName).request().get();
        assertThat(response.getStatus(), is(200));
    }

    private String findTestName() {
        return StackWalker.getInstance().walk(s ->
                s.dropWhile(f -> f.getClassName().equals(RemoteTest.class.getName()))
                        .findFirst()
                        .map(StackWalker.StackFrame::getMethodName)
                        .orElseThrow(() -> new NoSuchElementException("Unable to find caller method name")));
    }
}
