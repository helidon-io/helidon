/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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
 *
 */
package io.helidon.webserver.cors;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.helidon.webserver.cors.CustomMatchers.notPresent;
import static io.helidon.webserver.cors.CustomMatchers.present;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.array;
import static org.hamcrest.Matchers.arrayContaining;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

public class TestOrdering {

    private static final Supplier<Optional<CrossOriginConfig>> NO_OP = () -> Optional.empty();
    private static Aggregator aggregator;

    private static final Map<CrossOriginConfig, Integer> configs = new HashMap<>();

    private static CrossOriginConfig addToMap(int ID, CrossOriginConfig coc) {
        configs.put(coc, ID);
        return coc;
    }

    @BeforeAll
    static void init() {
        Aggregator.Builder builder = Aggregator.builder();
        builder.addCrossOrigin("/greet", addToMap(1, CrossOriginConfig.builder()
                .allowMethods("PUT", "DELETE")
                .build()));
        builder.addCrossOrigin("/greet", addToMap(2, CrossOriginConfig.builder()
                .allowMethods("GET")
                .build()));
        builder.addPathlessCrossOrigin(addToMap(3, CrossOriginConfig.builder()
                .allowMethods("GET", "HEAD", "OPTIONS")
                .build()));
        builder.addCrossOrigin("/othergreet", addToMap(4, CrossOriginConfig.builder()
                .allowMethods("PUT")
                .build()));
        builder.addCrossOrigin("/greet/{+}", addToMap(5, CrossOriginConfig.builder()
                .allowMethods("POST")
                .build()));

        aggregator = builder.build();
    }

    @Test
    void testGreet() {
        checkMatch("/greet", "GET", 2);
        checkMatch("/greet", "DELETE", 1);
        checkMatch("/greet", "HEAD", 3);
        checkNoMatch("/greet", "PATCH");

        checkMatch("/greet/sub", "GET", 3);
        checkMatch("/greet/sub", "POST", 5);
    }

    @Test
    void testOther() {
        checkMatch("/othergreet", "GET", 3);
        checkMatch("/othergreet", "PUT", 4);
        checkNoMatch("/othergreet", "JUNK");
    }

    @Test
    void testAbsent() {
        checkNoMatch("/absent", "PATCH");
    }

    @Test
    void testWildcardedPath() {
        checkNoMatch("/greet", "POST");
        checkMatch("/greet/sub", "POST", 5);
    }

    private void checkMatch(String path, String method, int expected) {
        Optional<CrossOriginConfig> matchOpt = aggregator.lookupCrossOrigin(path, method, NO_OP);
        assertThat(path + ":" + method + " not matched", matchOpt, present());
        assertThat(path + ":" + method + " matched incorrectly", configs.get(matchOpt.get()), is(expected));
    }

    private void checkNoMatch(String path, String method) {
        Optional<CrossOriginConfig> matchOpt = aggregator.lookupCrossOrigin(path, method, NO_OP);
        int match = matchOpt.map(configs::get).orElse(-1);
        assertThat(path + ":" + method + " matched but should be absent", match, is(-1));
    }
}
