/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates.
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

package io.helidon.microprofile.metrics;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.microprofile.server.Server;
import org.eclipse.microprofile.metrics.SimpleTimer;
import org.eclipse.microprofile.metrics.Tag;
import org.hamcrest.core.Is;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.json.JsonObject;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import java.util.Properties;
import java.util.stream.IntStream;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Makes sure that no synthetic SimpleTimer metrics are created for JAX-RS endpoints when
 * the config disables that feature.
 */
public class HelloWorldRestEndpointSimpleTimerDisabledTest extends HelloWorldTest {

    @BeforeAll
    public static void initializeServer() {
        HelloWorldTest.initializeServer(new Properties());
    }

    @Test
    public void testSyntheticSimpleTimer() {
        // Expect 0, because the config should have suppressed the synthetic SimpleTimer
        // metrics for JAX-RS endpoints, and the we will have just created the metric (by
        // looking for it) with an initialized count of 0.
        testSyntheticSimpleTimer(0L);
    }
}
