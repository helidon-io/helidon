/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.tests.integration.config.ftbootstrap;

import java.util.Set;

import io.helidon.config.Config;
import io.helidon.config.spi.ConfigSource;
import io.helidon.config.spi.ConfigSourceProvider;

public class RetryConfigSourceProvider implements ConfigSourceProvider {
    @Override
    public boolean supports(String type) {
        return RetryConfigSource.TYPE.equals(type);
    }

    @Override
    public ConfigSource create(String type, Config metaConfig) {
        return new RetryConfigSource();
    }

    @Override
    public Set<String> supported() {
        return Set.of(RetryConfigSource.TYPE);
    }
}
