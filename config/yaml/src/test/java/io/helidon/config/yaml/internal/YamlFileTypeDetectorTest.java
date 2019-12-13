/*
 * Copyright (c) 2017, 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.config.yaml.internal;

import java.io.IOException;
import java.nio.file.Paths;

import io.helidon.config.ConfigHelper;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * Tests {@link YamlFileTypeDetector}.
 */
public class YamlFileTypeDetectorTest {

    @Test
    public void testProbeContentType() throws IOException {
        assertThat(ConfigHelper.detectContentType(Paths.get("config.yaml")), is("application/x-yaml"));
    }

}
