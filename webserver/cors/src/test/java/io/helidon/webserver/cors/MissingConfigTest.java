/*
 * Copyright (c) 2020, 2023 Oracle and/or its affiliates.
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
package io.helidon.webserver.cors;

import java.io.ByteArrayOutputStream;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.logging.StreamHandler;

import io.helidon.config.Config;
import io.helidon.cors.Aggregator;
import io.helidon.cors.CorsSupportBase;
import io.helidon.cors.CrossOriginConfig;
import io.helidon.logging.common.LogConfig;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.junit5.OptionalMatcher.optionalPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;

/**
 * Checks handling of missing (absent) config.
 *
 * Make sure that passing a missing config node to
 */
class MissingConfigTest {

    private static final Logger BASE_LOGGER = Logger.getLogger(CorsSupportBase.class.getName());
    private static final Config MISSING = Config.empty().get("anything");

    private ByteArrayOutputStream os;
    private StreamHandler handler;

    @BeforeAll
    static void setup() {
        LogConfig.configureRuntime();
    }

    @BeforeEach
    void setupLoggingCapture() {
        os = new ByteArrayOutputStream();
        handler = new StreamHandler(os, new SimpleFormatter());
        handler.setLevel(Level.FINEST);
    }

    @Test
    void testCrossOriginConfig() {
        CrossOriginConfig coc = CrossOriginConfig.create(MISSING);
        checkCrossOriginConfig(coc);
    }

    @Test
    void testCorsSupportBuilderConfig() {
        CorsSupport cs = checkForLogMessage(() -> CorsSupport.builder().config(MISSING).build());
        checkCorsSupport(cs);
    }

    @Test
    void testCorsSupportCreate() {
        CorsSupport cs = checkForLogMessage(() -> CorsSupport.create(MISSING));
        checkCorsSupport(cs);
    }

    private static void checkCorsSupport(CorsSupport cs) {
        assertThat(cs.helper().isActive(), is(true));
        Aggregator aggregator = cs.helper().aggregator();
        assertThat(aggregator.isActive(), is(true));
        Optional<CrossOriginConfig> cocOpt = aggregator.lookupCrossOrigin("/any/path", "GET", Optional::empty);
        assertThat(cocOpt, optionalPresent());
        checkCrossOriginConfig(cocOpt.get());
    }

    private static void checkCrossOriginConfig(CrossOriginConfig coc) {
        assertThat(coc.allowCredentials(), is(false));
        assertThat(coc.allowMethods().length, greaterThan(0));
        assertThat(coc.allowMethods()[0], is("*"));
    }

    private <T> T checkForLogMessage(Supplier<T> supplier) {
        try {
            BASE_LOGGER.addHandler(handler);
            T result = supplier.get();
            handler.flush();
            assertThat(os.toString(), containsString("Attempt to load"));
            return result;
        } finally {
            os.reset();
            BASE_LOGGER.removeHandler(handler);
        }
    }
}
