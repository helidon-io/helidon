/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.config.internal;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.util.Optional;

import io.helidon.config.ConfigException;
import io.helidon.config.ConfigSources;
import io.helidon.config.spi.ConfigContext;
import io.helidon.config.spi.ConfigNode.ObjectNode;
import io.helidon.config.spi.ConfigSource;

import static io.helidon.config.ValueNodeMatcher.valueNode;
import io.helidon.config.test.infra.TemporaryFolderExt;
import static org.hamcrest.MatcherAssert.assertThat;

import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import static org.mockito.Mockito.mock;

/**
 * Tests {@link DirectoryConfigSource}.
 */
public class DirectoryConfigSourceTest {

    @RegisterExtension
    static TemporaryFolderExt folder = TemporaryFolderExt.build();
    
    @Test
    public void testDescriptionMandatory() {
        ConfigSource configSource = ConfigSources.directory("secrets").build();

        assertThat(configSource.description(), is("DirectoryConfig[secrets]"));
    }

    @Test
    public void testDescriptionOptional() {
        ConfigSource configSource = ConfigSources.directory("secrets").optional().build();

        assertThat(configSource.description(), is("DirectoryConfig[secrets]?"));
    }

    @Test
    public void testLoadNoDirectory() {
        DirectoryConfigSource configSource = (DirectoryConfigSource) ConfigSources.directory("unknown")
                .changesExecutor(Runnable::run)
                .changesMaxBuffer(1)
                .build();

        configSource.init(mock(ConfigContext.class));
        assertThat(configSource.dataStamp().get(), is(Instant.MAX));

        ConfigException ex = assertThrows(ConfigException.class, () -> {
            configSource.load();
        });
        assertTrue(instanceOf(ConfigException.class).matches(ex.getCause()));
        assertTrue(ex.getMessage().startsWith("Cannot load data from mandatory source"));
    }

    @Test
    public void testLoadNoDirectoryOptional() {
        DirectoryConfigSource configSource = (DirectoryConfigSource) ConfigSources.directory("unknown")
                .optional()
                .changesExecutor(Runnable::run)
                .changesMaxBuffer(1)
                .build();

        configSource.init(mock(ConfigContext.class));
        assertThat(configSource.dataStamp().get(), is(Instant.MAX));

        Optional<ObjectNode> loaded = configSource.load();
        assertThat(loaded, is(Optional.empty()));
    }

    @Test
    public void testLoadEmptyDirectory() throws IOException {
        DirectoryConfigSource configSource = (DirectoryConfigSource) ConfigSources.directory(folder.newFolder().getAbsolutePath())
                .changesExecutor(Runnable::run)
                .changesMaxBuffer(1)
                .build();

        configSource.init(mock(ConfigContext.class));
        assertThat(configSource.dataStamp().get(),
                   both(greaterThan(Instant.now().minusSeconds(3)))
                           .and(lessThan(Instant.now().plusSeconds(3))));

        ObjectNode objectNode = configSource.load().get();

        assertThat(objectNode.entrySet(), hasSize(0));
    }

    @Test
    public void testLoadDirectory() throws IOException {
        File folder = this.folder.newFolder();
        Files.write(Files.createFile(new File(folder, "username").toPath()), "libor".getBytes());
        Files.write(Files.createFile(new File(folder, "password").toPath()), "^ery$ecretP&ssword".getBytes());

        DirectoryConfigSource configSource = (DirectoryConfigSource) ConfigSources.directory(folder.getAbsolutePath())
                .changesExecutor(Runnable::run)
                .changesMaxBuffer(1)
                .build();

        configSource.init(mock(ConfigContext.class));
        assertThat(configSource.dataStamp().get(),
                   both(greaterThan(Instant.now().minusSeconds(3)))
                           .and(lessThan(Instant.now().plusSeconds(3))));

        ObjectNode objectNode = configSource.load().get();

        assertThat(objectNode.entrySet(), hasSize(2));
        assertThat(objectNode.get("username"), valueNode("libor"));
        assertThat(objectNode.get("password"), valueNode("^ery$ecretP&ssword"));
    }

}
