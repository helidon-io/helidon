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
package io.helidon.tests.integration.gh1468;

import java.util.Map;

import org.eclipse.microprofile.config.spi.ConfigSource;

import static io.helidon.common.CollectionsHelper.mapOf;

public class CustomConfigSource implements ConfigSource {
	private static final String NAME = "Gh1468ConfigSource";
	private static final int PRIORITY = 200; // Default for MP is 100
	private static final Map<String, String> PROPERTIES = mapOf("app.greeting", "Hi");


	@Override
	public String getName() {
		return NAME;
	}

	@Override
	public Map<String, String> getProperties() {
		return PROPERTIES;
	}

	@Override
	public String getValue(String key) {
		return PROPERTIES.get(key);
	}

	@Override
	public int getOrdinal() {
		return PRIORITY;
	}

}
