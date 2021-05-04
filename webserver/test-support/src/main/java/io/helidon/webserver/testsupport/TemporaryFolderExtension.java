/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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

package io.helidon.webserver.testsupport;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.TestInstancePostProcessor;

import static java.util.Arrays.stream;

/**
 * A JUnit5 extension to provide injection support of {@link TemporaryFolder}
 * instances.
 */
public class TemporaryFolderExtension implements AfterEachCallback,
                                                 TestInstancePostProcessor,
                                                 ParameterResolver {

    private final Collection<TemporaryFolder> tempFolders;

    /**
     * Create a new instance of {@link TemporaryFolderExtension}.
     */
    public TemporaryFolderExtension() {
        tempFolders = new ArrayList<>();
    }

    @Override
    public void afterEach(ExtensionContext context) throws IOException {
        tempFolders.forEach(TemporaryFolder::cleanUp);
    }

    @Override
    public void postProcessTestInstance(Object testInstance, ExtensionContext context) {

        stream(testInstance.getClass().getDeclaredFields())
                .filter(field -> field.getType() == TemporaryFolder.class)
                .forEach(field -> injectTemporaryFolder(testInstance, field));
    }

    private void injectTemporaryFolder(Object instance, Field field) {
        field.setAccessible(true);
        try {
            field.set(instance, createTempFolder());
        } catch (IllegalAccessException iae) {
            throw new RuntimeException(iae);
        }
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext,
            ExtensionContext extensionContext) {

        return parameterContext.getParameter().getType() == TemporaryFolder.class;
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext,
            ExtensionContext extensionContext) {
        return createTempFolder();
    }

    private TemporaryFolder createTempFolder() {
        TemporaryFolder result = new TemporaryFolder();
        result.prepare();
        tempFolders.add(result);
        return result;
    }
}
