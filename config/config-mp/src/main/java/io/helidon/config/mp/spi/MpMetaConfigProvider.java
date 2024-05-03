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

package io.helidon.config.mp.spi;

import java.io.Reader;
import java.util.List;
import java.util.Set;

import io.helidon.config.Config;

import org.eclipse.microprofile.config.spi.ConfigSource;

/**
 * Java Service loader interface for Meta-config providers.
 */
public interface MpMetaConfigProvider {
    /**
     * Set of supported types for a MicroProfile meta-config provider.
     *
     * @return meta-config provider types
     */
    Set<String> supportedTypes();

    /**
     * Create a list of configuration sources from a meta-config type.
     *
     * @param type type of the config source
     * @param metaConfig configuration properties of a meta-config type
     * @param profile name of the profile to use or null if not used
     *
     * @return list of config sources
     */
    List<? extends ConfigSource> create(String type, Config metaConfig, String profile);

    /**
     * Create the {@link ConfigSource} from the given content.
     *
     * @param content a reader with the content data
     * @return config source
     */
    ConfigSource create(Reader content);
}
