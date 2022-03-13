/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.tests.configprofile;

import jakarta.json.JsonObject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DevTest extends BaseTest {

    /**
     * This test will only succeed if the 'dev' profile is enabled and the
     * config files are loaded properly.
     */
    @Test
    @EnabledIfSystemProperty(named = "config.profile", matches = "dev")
    public void testHelloDevWorld() {
        JsonObject jsonObject = webClient().get()
                .path("/greet")
                .request(JsonObject.class)
                .await();
        assertEquals("Hello Dev World!", jsonObject.getString("message"));
    }
}
