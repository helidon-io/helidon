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

package io.helidon.builder.test;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.notNullValue;

@Disabled("This feature is currently not implemented")
class HelidonOpenApiConfigTest {

    @Test
    void testIt() {
        /*
        HelidonOpenApiConfig config = HelidonOpenApiConfig.builder()
                .filter("xyz")
                .addServer("aServer")
                .addServers(List.of("anotherServer"))
                .build();

        assertThat(config.filter(),
                   equalTo("xyz"));
        assertThat(config.servers(),
                   containsInAnyOrder("aServer", "anotherServer"));
        assertThat(config.modelReader(),
                   equalTo("@default"));
        assertThat(config.toString(),
                   notNullValue());

        HelidonOpenApiConfig config2 = HelidonOpenApiConfig.builder(config).build();
        assertThat(config2,
                   equalTo(config));
        assertThat(config2,
                   not(sameInstance(config)));

         */
    }

}
