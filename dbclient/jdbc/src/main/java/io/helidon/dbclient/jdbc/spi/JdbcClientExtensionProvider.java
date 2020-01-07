/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.dbclient.jdbc.spi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ServiceLoader;

import io.helidon.common.serviceloader.HelidonServiceLoader;
import io.helidon.dbclient.jdbc.JdbcClientExtension;

/**
 * Java Service loader interface that provides JDBC DB Client configuration extension.
 */
public interface JdbcClientExtensionProvider {

    /**
     * Class containing List of loaded service implementations.
     *
     * Thread safe lazy loading of available extension service implementations.
     */
    final class Instance {

        /** List of loaded service implementations. */
        private static final List<JdbcClientExtension> EXTENSIONS = Collections.unmodifiableList(initExtensions());

        /**
         * Initialize (load) all available extension service implementations.
         *
         * @return List of loaded extension service implementations
         */
        private static List<JdbcClientExtension> initExtensions() {
            final HelidonServiceLoader<JdbcClientExtensionProvider> serviceLoader
                    = HelidonServiceLoader
                            .builder(ServiceLoader.load(JdbcClientExtensionProvider.class))
                            .build();
            final List<JdbcClientExtensionProvider> providers = serviceLoader.asList();
            final List<JdbcClientExtension> extensions = new ArrayList<>(providers.size());
            providers.forEach(provider -> {
                final JdbcClientExtension extension = provider.extension();
                if (extension != null) {
                    extensions.add(extension);
                }
            });
            return extensions;
        }

    }

    /**
     * Get list of all extension service implementations.
     *
     * @return list of all extension service implementations
     */
    static List<JdbcClientExtension> extensions() {
        return Instance.EXTENSIONS;
    }

    /**
     * Get instance of JDBC DB Client configuration extension.
     *
     * @return JDBC DB Client configuration extension
     */
    JdbcClientExtension extension();

}
