/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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
package io.helidon.tests.integration.dbclient.common.tests;

import java.util.List;
import java.util.ServiceLoader;

import io.helidon.common.HelidonServiceLoader;
import io.helidon.config.Config;
import io.helidon.dbclient.DbClient;
import io.helidon.tests.integration.dbclient.common.spi.SetupProvider;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

public class DbClientParameterResolver implements ParameterResolver {

    private static final SetupProvider SETUP_PROVIDER = initSetupProvider();

    private static SetupProvider initSetupProvider() {
        ServiceLoader<SetupProvider> loader = ServiceLoader.load(SetupProvider.class);
        List<SetupProvider> providers = HelidonServiceLoader
                .builder(loader)
                .build()
                .asList();
        switch (providers.size()) {
            case 0: throw new IllegalStateException("No SetupProvider instance found on the classpath");
            case 1: return providers.getFirst();
            default: throw new IllegalStateException("Multiple SetupProvider instances found on the classpath");
        }
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
            throws ParameterResolutionException {
        Class<?> type = parameterContext.getParameter().getType();
        if (DbClient.class.isAssignableFrom(type)) {
            return true;
        }
        if (Config.class.isAssignableFrom(type)) {
            return true;
        }
        return false;
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
            throws ParameterResolutionException {
        Class<?> type = parameterContext.getParameter().getType();
        if (DbClient.class.isAssignableFrom(type)) {
            return SETUP_PROVIDER.dbClient();
        }
        if (Config.class.isAssignableFrom(type)) {
            return SETUP_PROVIDER.config();
        }
        return null;
    }
}
