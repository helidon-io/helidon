/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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

package io.helidon.config.hocon.mp;

import java.io.Reader;
import java.util.List;
import java.util.Set;

import io.helidon.config.Config;
import io.helidon.config.mp.MpMetaConfigUtils;
import io.helidon.config.mp.Prioritized;
import io.helidon.config.mp.spi.MpMetaConfigProvider;

import com.typesafe.config.ConfigParseOptions;
import org.eclipse.microprofile.config.spi.ConfigSource;

/**
 * Helidon MicroProfile meta-config provider for Hocon and Json.
 */
public class HoconMpMetaConfigProvider implements MpMetaConfigProvider, Prioritized {
    @Override
    public Set<String> supportedTypes() {
        return Set.of("hocon", "json");
    }

    @Override
    public List<? extends ConfigSource> create(String type, Config metaConfig, String profile) {
        return MpMetaConfigUtils.sourceFromMeta(
                metaConfig,
                profile,
                HoconMpConfigSource::create,
                HoconMpConfigSource::classPath,
                HoconMpConfigSource::classPath,
                HoconMpConfigSource::create
        );
    }

    @Override
    public int priority() {
        return 300;
    }

    @Override
    public ConfigSource create(Reader content) {
        return HoconMpConfigSource.create("HoconMpMetaConfigProvider", content,
                ConfigParseOptions.defaults());
    }
}
