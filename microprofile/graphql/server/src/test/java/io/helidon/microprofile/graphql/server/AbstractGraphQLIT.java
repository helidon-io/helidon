/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.microprofile.graphql.server;

import io.helidon.microprofile.graphql.server.util.JandexUtils;
import io.helidon.microprofile.server.Server;
import org.junit.jupiter.api.AfterAll;

/**
 * Abstract functionality for integration tests.
 */
public abstract class AbstractGraphQLIT extends AbstractGraphQLTest {

    private static Server server;
    private static String graphQLUrl;
    private static String graphQLUIUrl;

    public static int getPort() {
        return server.port();
    }

    public static String getGraphQLUrl() {
        return graphQLUrl;
    }

    public static void _setupTest() {
        System.clearProperty(JandexUtils.PROP_INDEX_FILE);

        server = Server.create().start();
        String baseURL = "http://127.0.0.1:" + getPort() + "/";
        graphQLUrl = baseURL + "graphql";
        graphQLUIUrl = baseURL+ "ui";

        System.out.println("GraphQL URL: " + graphQLUrl);
        System.out.println("GraphQL UI: " + graphQLUIUrl);
    }

    @AfterAll
    public static void teardownTest() {
        if (server != null) {
            server.stop();
        }
    }

}
