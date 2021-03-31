/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates.
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

package io.helidon.config;

import java.nio.file.Paths;
import java.util.Optional;

import io.helidon.config.spi.ConfigContent;
import io.helidon.config.spi.OverrideSource;
import io.helidon.config.test.infra.TemporaryFolderExt;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

/**
 * Tests {@link io.helidon.config.FileOverrideSource}.
 */
public class FileOverrideSourceTest {

    private static final String RELATIVE_PATH_TO_RESOURCE = "/src/test/resources/";

    @RegisterExtension
    static TemporaryFolderExt folder = TemporaryFolderExt.build();

    private static String getDir() {
        return Paths.get("").toAbsolutePath() + RELATIVE_PATH_TO_RESOURCE;
    }

    @Test
    public void testDescriptionMandatory() {
        OverrideSource overrideSource = OverrideSources.file("overrides.properties").build();

        assertThat(overrideSource.description(), is("FileOverride[overrides.properties]"));
    }

    @Test
    public void testDescriptionOptional() {
        OverrideSource overrideSource = OverrideSources.file("overrides.properties").optional().build();

        assertThat(overrideSource.description(), is("FileOverride[overrides.properties]?"));
    }

    @Test
    public void testLoadNotExists() {
        FileOverrideSource overrideSource = OverrideSources.file("overrides.properties")
                .build();

        assertThat(overrideSource.load(), is(Optional.empty()));
    }

    @Test
    public void testLoadExists() {
        OverrideSource overrideSource = OverrideSources.file(getDir() + "io/helidon/config/overrides.properties")
                .build();

        Optional<OverrideSource.OverrideData> configNode = overrideSource.load()
                .map(ConfigContent.OverrideContent::data);

        assertThat(configNode, notNullValue());
        assertThat(configNode.isPresent(), is(true));
    }

    @Test
    public void testBuilder() {
        OverrideSource overrideSource = OverrideSources.file("overrides.properties").build();

        assertThat(overrideSource, notNullValue());
    }
}
