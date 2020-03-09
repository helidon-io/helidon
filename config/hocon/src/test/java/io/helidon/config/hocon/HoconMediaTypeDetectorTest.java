/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

package io.helidon.config.hocon;

import java.nio.file.Paths;

import io.helidon.common.media.type.MediaTypes;

import org.junit.jupiter.api.Test;

import static io.helidon.config.testing.OptionalMatcher.value;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * File type detector for hocon is now obsolete.
 * Tests left in to make sure we detect these types as expected
 */
public class HoconMediaTypeDetectorTest {

    @Test
    public void testProbeContentTypeHocon() {
        assertThat(MediaTypes.detectType(Paths.get("config.conf")), value(is("application/hocon")));
    }

    @Test
    public void testProbeContentTypeJson() {
        assertThat(MediaTypes.detectType(Paths.get("config.json")), value(is("application/json")));
    }
}
