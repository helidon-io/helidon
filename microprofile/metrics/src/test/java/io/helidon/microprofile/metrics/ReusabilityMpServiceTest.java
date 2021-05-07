/*
 * Copyright (c) 2019, 2021 Oracle and/or its affiliates.
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

import io.helidon.microprofile.server.Server;

import org.junit.jupiter.api.Test;

/**
 *
 */
public class ReusabilityMpServiceTest {

    static Server initServer(Class<?> resourceClass) {
        return Server.builder()
                .addResourceClass(resourceClass)
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
