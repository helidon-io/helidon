/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.integrations.microstream.core;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.text.StringContainsInOrder.stringContainsInOrder;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.helidon.config.ClasspathConfigSource;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import one.microstream.storage.embedded.types.EmbeddedStorageManager;

class ConfigurationTest {

	@TempDir
	Path tempDir;

	@Test
	void createFromConfigTest() {

		Config helidonConfig = Config.builder().addSource(ClasspathConfigSource.create("/microstreamConfig.yml"))
				.addSource(ConfigSources.create(Map.of("microstream.storage-directory", tempDir.toString())))
				.build();

		EmbeddedStorageManagerBuilder embeddedStorageManagerBuilder = EmbeddedStorageManagerBuilder.builder();
		EmbeddedStorageManager embeddedStorageManager = embeddedStorageManagerBuilder.config(helidonConfig.get("microstream")).build();

		assertNotNull(embeddedStorageManager, "embeddedStorageManager is null!");

		//need to compare strings as the microstream APath is not a java path
		assertThat(tempDir.toString(), stringContainsInOrder(
				Arrays.asList(embeddedStorageManager.configuration().fileProvider().baseDirectory().toPath())));
				

		assertThat(embeddedStorageManager.configuration().channelCountProvider().getChannelCount(), is(4));
		assertThat(embeddedStorageManager.configuration().housekeepingController().housekeepingIntervalMs(), is(2000L));
	}
}
