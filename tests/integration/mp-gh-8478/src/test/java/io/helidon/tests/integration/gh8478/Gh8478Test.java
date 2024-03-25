/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.tests.integration.gh8478;

import io.helidon.microprofile.tests.junit5.AddConfig;
import io.helidon.microprofile.tests.junit5.HelidonTest;

import javax.inject.Inject;
import javax.ws.rs.ForbiddenException;
import javax.ws.rs.client.WebTarget;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

@HelidonTest
@AddConfig(key ="io.helidon.tests.integration.gh8478.Gh8478Resource/Retry/enabled", value = "true")
@AddConfig(key ="io.helidon.tests.integration.gh8478.Gh8478Resource/Retry/maxRetries", value = "1")
@AddConfig(key ="io.helidon.tests.integration.gh8478.Gh8478Resource/Timeout/enabled", value = "true")
@AddConfig(key ="io.helidon.tests.integration.gh8478.Gh8478Resource/Timeout/value", value = "10000")
class Gh8478Test {
    @Inject
    private WebTarget target;

    @BeforeEach
    void setUp() {
        Gh8478Resource.COUNTER.set(0);
    }

    @Test
    void test() {
        assertThrows(ForbiddenException.class, () -> target
                .path("/greet")
                .request()
                .get(String.class));
    }
}
