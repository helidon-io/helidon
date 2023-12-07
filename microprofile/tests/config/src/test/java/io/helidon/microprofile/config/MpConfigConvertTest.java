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
package io.helidon.microprofile.config;

import io.helidon.config.mp.MpConfig;
import io.helidon.microprofile.testing.junit5.AddConfig;
import io.helidon.microprofile.testing.junit5.HelidonTest;

import jakarta.inject.Inject;
import org.eclipse.microprofile.config.Config;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Test for correct handling of other implementations of {@link Config}.
 *
 * @see "https://github.com/helidon-io/helidon/issues/6668"
 */
@HelidonTest
@AddConfig(key = "key", value = "value")
public class MpConfigConvertTest {

    @Inject
    private Config mpConfig;

    //No exceptions should occur.
    @Test
    void testConvertToHelidonConfig() {
        io.helidon.config.Config helidonConfig = MpConfig.toHelidonConfig(mpConfig);
        assertThat(helidonConfig.get("key").asString().get(), is("value"));
    }
}
