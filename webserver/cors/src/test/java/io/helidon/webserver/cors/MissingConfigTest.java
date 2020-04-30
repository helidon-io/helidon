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

import io.helidon.config.Config;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static io.helidon.webserver.cors.CustomMatchers.present;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.greaterThan;

/**
 * Checks handling of missing (absent) config.
 *
 * Make sure that passing a missing config node to
 */
class MissingConfigTest {

    private static final Config EMPTY = Config.empty();

    @Test
    void testCrossOriginConfig() {
        CrossOriginConfig coc = CrossOriginConfig.create(EMPTY);
        checkCrossOriginConfig(coc);
    }

    @Test
    void testCorsSupport() {
        CorsSupport cs = CorsSupport.create(EMPTY);
        assertThat(cs.helper().isActive(), is(true));
        Aggregator aggregator = cs.helper().aggregator();
        assertThat(aggregator.isActive(), is(true));
        Optional<CrossOriginConfig> cocOpt = aggregator.lookupCrossOrigin("/any/path", () -> Optional.empty());
        assertThat(cocOpt, present());
        checkCrossOriginConfig(cocOpt.get());
    }

    private void checkCrossOriginConfig(CrossOriginConfig coc) {
        assertThat(coc.allowCredentials(), is(false));
        assertThat(coc.allowMethods().length, greaterThan(0));
        assertThat(coc.allowMethods()[0], is("*"));
    }
}
