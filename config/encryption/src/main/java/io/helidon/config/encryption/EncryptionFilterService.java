/*
 * Copyright (c) 2018, 2024 Oracle and/or its affiliates.
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

package io.helidon.config.encryption;

import io.helidon.config.Config;
import io.helidon.config.ConfigItem;
import io.helidon.config.spi.ConfigFilter;

/**
 * A Java service for {@link EncryptionFilter}.
 */
public class EncryptionFilterService implements ConfigFilter {
    private ConfigFilter filter;

    @Override
    public String apply(Config.Key key, String stringValue) {
        if (null == filter) {
            return stringValue;
        }

        return filter.apply(key, stringValue);
    }

    @Override
    public ConfigItem apply(Config.Key key, ConfigItem itemPolicy) {
        if (null == filter) {
            return itemPolicy;
        }
        return filter.apply(key, itemPolicy);
    }

    @Override
    public void init(Config config) {
        this.filter = EncryptionFilter.fromConfig().apply(config);
    }
}
