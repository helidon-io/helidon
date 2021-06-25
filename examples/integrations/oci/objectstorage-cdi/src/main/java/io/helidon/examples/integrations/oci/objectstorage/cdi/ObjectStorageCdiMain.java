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

package io.helidon.examples.integrations.oci.objectstorage.cdi;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import io.helidon.config.yaml.YamlMpConfigSource;
import io.helidon.microprofile.cdi.Main;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.spi.ConfigProviderResolver;
import org.eclipse.microprofile.config.spi.ConfigSource;

/**
 * Main class of the example.
 * This is only used to merge configuration from home directory with the one embedded on classpath.
 */
public final class ObjectStorageCdiMain {
    private ObjectStorageCdiMain() {
    }

    /**
     * Main method.
     *
     * @param args ignored
     */
    public static void main(String[] args) {
        ConfigProviderResolver configProvider = ConfigProviderResolver.instance();

        Config mpConfig = configProvider.getBuilder()
                .addDefaultSources()
                .withSources(examplesConfig())
                .addDiscoveredSources()
                .addDiscoveredConverters()
                .build();

        // configure
        configProvider.registerConfig(mpConfig, null);

        // update system properties - required for health check built from config
        System.setProperty("oci.properties.objectstorage-bucket",
                mpConfig.getValue("oci.properties.objectstorage-bucket", String.class));
        System.setProperty("oci.properties.objectstorage-namespace",
                mpConfig.getValue("oci.properties.objectstorage-namespace", String.class));

        // start CDI
        Main.main(args);
    }

    private static ConfigSource[] examplesConfig() {
        Path path = Paths.get(System.getProperty("user.home") + "/helidon/conf/examples.yaml");
        if (Files.exists(path)) {
            return new ConfigSource[] {YamlMpConfigSource.create(path)};
        }
        return new ConfigSource[0];
    }
}
