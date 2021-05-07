/*
 * Copyright (c) 2017, 2021 Oracle and/or its affiliates.
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.helidon.webserver.Handler;
import io.helidon.webserver.Routing;

/**
 * Provides ability to define mocking of particular routes.
 */
public class RouteMock {

    private final Set<Integer> skipIndexes = new HashSet<>();
    private final Map<Integer, Handler> replaceHandlers = new HashMap<>();
    private final List<Handler> beforeHandlers = new ArrayList<>();

    /**
     * Skips single matched rout.
     *
     * @param matchRoutIndex Index of the matched rout. First matched rout has index {@code 0}.
     * @return updated instance.
     */
    public RouteMock skip(int matchRoutIndex) {
        skipIndexes.add(matchRoutIndex);
        return this;
    }

    /**
     * Replace single matched rout.
     *
     * @param matchRoutIndex Index of the matched rout. First matched rout has index {@code 0}.
     * @param handler a new handler.
     * @return updated instance.
     */
    public RouteMock replace(int matchRoutIndex, Handler handler) {
        replaceHandlers.put(matchRoutIndex, handler);
        return this;
    }

    /**
     * Executes this handler before first matched handler. Executed only if some handler is matched.
     *
     * @param handler a handler to execute.
     * @return updated instance.
     */
    public RouteMock before(Handler handler) {
        beforeHandlers.add(handler);
        return this;
    }

    /**
     * Creates new client with this mock and provided routing.
     *
     * @param routing a routing for the client.
     * @return new test client.
     */
    public TestClient client(Routing routing) {
        throw new UnsupportedOperationException("Not implemented!");
    }

}
