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

package io.helidon.integrations.microstream.cache;

import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.helidon.config.ClasspathConfigSource;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import one.microstream.cache.types.Cache;
import one.microstream.cache.types.CacheConfiguration;

class CacheTest {

	@TempDir
	Path tempDir;

	@Test
	void createCacheTest() {
		CacheConfiguration<Integer, String> config = MicrostreamCacheConfigurationBuilder.builder(Integer.class, String.class)
				.build();
		CacheBuilder<Integer, String> builder = CacheBuilder.builder(config, Integer.class, String.class);
		Cache<Integer, String> cache = builder.build("testCache");

		cache.put(1, "Hello");

		cache.close();
	}

	@Test
	void createCacheFromConfigTest() {
		Config helidonConfig = Config.builder().addSource(ClasspathConfigSource.create("/microstreamCacheConfig.yml"))
				.addSource(ConfigSources.create(Map.of("cache.microstream.storage.storage-directory", tempDir.toString())))
				.build();

		CacheConfiguration<Integer, String> config = MicrostreamCacheConfigurationBuilder
				.builder(helidonConfig.get("cache.microstream"), Integer.class, String.class)
				.build();

		Cache<Integer, String> cache = CacheBuilder.builder(config, Integer.class, String.class).build("Cache_IntStr");

		cache.put(1, "Hello");

		cache.close();
	}
}
