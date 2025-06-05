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

package io.helidon.webserver.context;

import java.util.List;
import java.util.Optional;

import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import io.helidon.webserver.spi.ServerFeature;
import io.helidon.webserver.testing.junit5.SetUpFeatures;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.junit5.OptionalMatcher.optionalPresent;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

abstract class ContextFeatureTestBase {
    private static final HeaderName FIRST = HeaderNames.create("X-First");
    private static final HeaderName SECOND = HeaderNames.create("X-Second");
    private static final String FIRST_CLASSIFIER = "my.first.context";

    private final Http1Client client;

    ContextFeatureTestBase(Http1Client client) {
        this.client = client;
    }

    @SetUpFeatures
    static List<ServerFeature> features() {
        return List.of(ContextFeature.builder()
                               .addRecord(first -> first.header(FIRST)
                                       .classifier(FIRST_CLASSIFIER))
                               .addRecord(second -> second.header(SECOND))
                               .build());
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder router) {
        Contexts.globalContext().register(ContextFeatureTestBase.class, "fixed-value");

        router.get("/context", ContextFeatureTestBase::testingHandler)
                .get("/propagation", ContextFeatureTestBase::propagationHandler);
    }

    @Test
    void testNoHeaderPropagation() {
        String response = client.get("/propagation")
                .requestEntity(String.class);

        assertThat(response, is("context-values:{EMPTY},{EMPTY}"));
    }

    @Test
    void testFirstHeaderPropagation() {
        String response = client.get("/propagation")
                .header(FIRST, "some-value")
                .requestEntity(String.class);

        assertThat(response, is("context-values:some-value,{EMPTY}"));
    }

    @Test
    void testBothHeaderPropagation() {
        String response = client.get("/propagation")
                .header(FIRST, "some-value")
                .header(SECOND, "other-value")
                .requestEntity(String.class);

        assertThat(response, is("context-values:some-value,other-value"));
    }

    @Test
    void testContext() {
        String response = client.get("/context")
                .request(String.class)
                .entity();
        assertThat(response, is("fixed-value"));
        // make sure the second request gets the same value (to make sure we do not change value in parent)
        response = client.get("/context")
                .request(String.class)
                .entity();
        assertThat(response, is("fixed-value"));
    }

    private static void propagationHandler(ServerRequest req, ServerResponse res) {
        Context context = req.context();

        String first = context.get(FIRST_CLASSIFIER, String.class)
                .orElse("{EMPTY}");
        String second = context.get(SECOND.defaultCase(), String.class)
                .orElse("{EMPTY}");

        res.send("context-values:" + first + "," + second);
    }

    private static void testingHandler(ServerRequest req, ServerResponse res) {
        Optional<Context> optionalContext = Contexts.context();

        assertThat(optionalContext, optionalPresent());

        Context context = optionalContext.get();
        String value = context.get(ContextFeatureTestBase.class, String.class).get();
        // then set it to a different value (will not impact parent)
        context.register(ContextFeatureTestBase.class, "request-value");
        // make sure we get the value registered with parent context (will be validated by client)
        res.send(value);
    }
}