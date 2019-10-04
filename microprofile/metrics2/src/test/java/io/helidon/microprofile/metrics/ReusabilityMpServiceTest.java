/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.microprofile.metrics;

import io.helidon.config.Config;
import io.helidon.microprofile.config.MpConfig;
import io.helidon.microprofile.server.Server;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

import org.jboss.weld.exceptions.DefinitionException;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 */
public class ReusabilityMpServiceTest {

    private Server initServer(Class<?> resourceClass) {
        return Server.builder()
                .addResourceClass(resourceClass)
                .config(MpConfig.builder().config(Config.create()).build())
                .host("localhost")
                // choose a random available port
                .port(-1)
                .build();
    }

    @Test
    public void tryToStartServerWithLegalAnnotationReuse() throws Exception {
        Server server = initServer(ResourceWithLegallyReusedMetrics.class);
        try {
            server.start();
        } finally {
            server.stop();
        }
    }
}
