/*
 * Copyright (c) 2017, 2020 Oracle and/or its affiliates.
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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.util.Optional;

import io.helidon.config.spi.ConfigContent;
import io.helidon.config.spi.ConfigNode.ObjectNode;
import io.helidon.config.spi.ConfigSource;
import io.helidon.config.test.infra.TemporaryFolderExt;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static io.helidon.config.ValueNodeMatcher.valueNode;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.core.Is.is;

/**
 * Tests {@link io.helidon.config.DirectoryConfigSource}.
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
        DirectoryConfigSource configSource = ConfigSources.directory("unknown")
                .build();

        assertThat(configSource.load(), is(Optional.empty()));
    }

    @Test
    public void testLoadEmptyDirectory() throws IOException {
        DirectoryConfigSource configSource = ConfigSources.directory(folder.newFolder().getAbsolutePath())
                .build();

        Optional<ConfigContent.NodeContent> maybeContent = configSource.load();

        assertThat(maybeContent, not(Optional.empty()));

        ConfigContent.NodeContent nodeContent = maybeContent.get();
        Optional<Object> maybeStamp = nodeContent.stamp();
        assertThat(maybeStamp, not(Optional.empty()));
        Instant stamp = (Instant) maybeStamp.get();
        assertThat(stamp,
                   both(greaterThan(Instant.now().minusSeconds(3)))
                           .and(lessThan(Instant.now().plusSeconds(3))));

        ObjectNode objectNode = nodeContent.data();

        assertThat(objectNode.entrySet(), hasSize(0));
    }

    @Test
    public void testLoadDirectory() throws IOException {
        File folder = DirectoryConfigSourceTest.folder.newFolder();
        Files.write(Files.createFile(new File(folder, "username").toPath()), "libor".getBytes());
        Files.write(Files.createFile(new File(folder, "password").toPath()), "^ery$ecretP&ssword".getBytes());

        DirectoryConfigSource configSource = ConfigSources.directory(folder.getAbsolutePath())
                .build();

        Optional<ConfigContent.NodeContent> maybeContent = configSource.load();
        ConfigContent.NodeContent nodeContent = maybeContent.get();
        Optional<Object> maybeStamp = nodeContent.stamp();
        assertThat(maybeStamp, not(Optional.empty()));
        Instant stamp = (Instant) maybeStamp.get();
        assertThat(stamp,
                   both(greaterThan(Instant.now().minusSeconds(3)))
                           .and(lessThan(Instant.now().plusSeconds(3))));

        ObjectNode objectNode = nodeContent.data();

        assertThat(objectNode.entrySet(), hasSize(2));
        assertThat(objectNode.get("username"), valueNode("libor"));
        assertThat(objectNode.get("password"), valueNode("^ery$ecretP&ssword"));
    }

}
