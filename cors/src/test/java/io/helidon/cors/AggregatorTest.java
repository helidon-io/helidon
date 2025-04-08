/*
 * Copyright (c) 2020, 2025 Oracle and/or its affiliates.
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
package io.helidon.cors;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.junit5.OptionalMatcher.optionalPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class AggregatorTest {

    private static final Supplier<Optional<CrossOriginConfig>> NO_OP = Optional::empty;
    private static final Map<CrossOriginConfig, Integer> configs = new HashMap<>();
    private static Aggregator aggregator;

    @BeforeAll
    static void init() {
        Aggregator.Builder builder = Aggregator.builder();
        builder.addCrossOrigin("/greet", addToMap(1, CrossOriginConfig.builder()
                .allowMethods("PUT", "DELETE")
                .build()));
        builder.addCrossOrigin("/greet", addToMap(2, CrossOriginConfig.builder()
                .allowMethods("GET")
                .build()));
        builder.addCrossOrigin("/openintegration/v10/ui/space in path",
                               addToMap(6, CrossOriginConfig.builder()
                                       .allowMethods("GET", "PUT")
                                       .build()));
        builder.addCrossOrigin("/openintegration/v1.0/ui/dotInPath",
                               addToMap(7, CrossOriginConfig.builder()
                                       .allowMethods("GET", "PUT")
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

    @Test
    void testSpecialCharacters() {
        /*
        The first two checks should match the entries added for those two specific paths (IDs 6 and 7 respectively).
        The second two checks make sure that wildcarded matches--the "pathless" entry ID 3--work with paths containing
        dots or spaces.
         */
        checkMatch("/openintegration/v10/ui/space in path", "GET", 6);
        checkMatch("/openintegration/v1.0/ui/dotInPath", "GET", 7);
        checkMatch("/openintegration/v20/ui/space in path", "GET", 3);
        checkMatch("/openintegration/v2.0/ui/dot and space in path", "GET", 3);
    }

    private static CrossOriginConfig addToMap(int ID, CrossOriginConfig coc) {
        configs.put(coc, ID);
        return coc;
    }

    private void checkMatch(String path, String method, int expected) {
        Optional<CrossOriginConfig> matchOpt = aggregator.lookupCrossOrigin(path, method, NO_OP);
        assertThat(path + ":" + method + " not matched", matchOpt, optionalPresent());
        assertThat(path + ":" + method + " matched incorrectly", configs.get(matchOpt.get()), is(expected));
    }

    private void checkNoMatch(String path, String method) {
        Optional<CrossOriginConfig> matchOpt = aggregator.lookupCrossOrigin(path, method, NO_OP);
        int match = matchOpt.map(configs::get).orElse(-1);
        assertThat(path + ":" + method + " matched but should be absent", match, is(-1));
    }
}
