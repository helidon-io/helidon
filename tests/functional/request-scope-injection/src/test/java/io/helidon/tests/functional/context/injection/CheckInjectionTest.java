/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.tests.functional.context.injection;

import io.helidon.microprofile.tests.junit5.HelidonTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.client.WebTarget;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Unit test for {@link CheckInjectionResource}.
 */
@HelidonTest
class CheckInjectionTest {

    private final WebTarget baseTarget;

    @Inject
    CheckInjectionTest(WebTarget baseTarget) {
        this.baseTarget = baseTarget;
    }

    @Test
    void testCheckInjection() {
        WebTarget target = baseTarget.path("/check");
        assertThat(target.request().get().getStatus(), is(200));
    }
}