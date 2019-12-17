/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.config.tests.module.meta2;

import java.util.Set;

import io.helidon.config.Config;
import io.helidon.config.spi.ConfigSource;
import io.helidon.config.spi.ConfigSourceProvider;

/**
 * Service loader implementation.
 */
public class MyConfigSource2Provider implements ConfigSourceProvider {
    private static final String PROVIDER_TYPE = "meta2class";

    @Override
    public boolean supports(String type) {
        return PROVIDER_TYPE.equals(type);
    }

    @Override
    public ConfigSource create(String type, Config metaConfig) {
        return metaConfig.as(MyConfigSource2.class).get();
    }

    @Override
    public Set<String> supported() {
        return Set.of(PROVIDER_TYPE);
    }
}
