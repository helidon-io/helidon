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

package io.helidon.testing.junit5;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import io.helidon.common.media.type.MediaType;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;

import static io.helidon.common.media.type.MediaTypes.APPLICATION_X_YAML;

final class TestConfigFactory {

    public static final MediaType TEXT_X_JAVA_PROPERTIES = MediaTypes.create("text/x-java-properties");

    private final Config.Builder configBuilder = Config.builder();

    private TestConfigFactory(Class<?> testClass, Method testMethod) {
        var methodConfigAnnotation = Optional.ofNullable(testMethod)
                .flatMap(m -> Optional.ofNullable(m.getDeclaredAnnotation(Testing.Configuration.class)));
        var classConfigAnnotation = Optional.ofNullable(testClass.getDeclaredAnnotation(Testing.Configuration.class));

        var useOnlyExisting = methodConfigAnnotation.or(() -> classConfigAnnotation)
                .map(Testing.Configuration::useExisting)
                .orElse(false);

        if (useOnlyExisting) {
            configBuilder.addSource(ConfigSources.create(Config.create()));
            return;
        }

        // order of priority
        addMethodConfigBlocks(testMethod);
        addConfigSources(methodConfigAnnotation);
        addClassConfigBlocks(testClass);
        addConfigSources(classConfigAnnotation);
        // existing
        configBuilder.addSource(ConfigSources.create(Config.create()));
    }

    private Config build() {
        return configBuilder.build();
    }

    static Config create(Class<?> testClass, Method testMethod) {
        return new TestConfigFactory(testClass, testMethod).build();
    }

    private void addConfigSources(Optional<Testing.Configuration> configAnnotation) {
        configAnnotation
                .map(Testing.Configuration::configSources)
                .stream()
                .flatMap(Arrays::stream).map(ConfigSources::classpath)
                .forEach(configBuilder::addSource);
    }

    private void addMethodConfigBlocks(Method testMethod) {
        var methodContainerBlocks = Optional.ofNullable(testMethod)
                .map(tm -> Arrays.stream(tm.getDeclaredAnnotationsByType(Testing.AddConfigBlocks.class)))
                .stream()
                .flatMap(Function.identity())
                .map(Testing.AddConfigBlocks::value)
                .flatMap(Arrays::stream);
        var methodDirectBlocks = Optional.ofNullable(testMethod)
                .map(tm -> Arrays.stream(tm.getDeclaredAnnotationsByType(Testing.AddConfigBlock.class)))
                .stream()
                .flatMap(Function.identity());
        addConfigBlocks(Stream.concat(methodContainerBlocks, methodDirectBlocks));
    }

    private void addClassConfigBlocks(Class<?> testClass) {
        var classContainerBlocks = Arrays.stream(testClass.getDeclaredAnnotationsByType(Testing.AddConfigBlocks.class))
                .map(Testing.AddConfigBlocks::value)
                .flatMap(Arrays::stream);
        var classDirectBlocks = Arrays.stream(testClass.getDeclaredAnnotationsByType(Testing.AddConfigBlock.class));
        addConfigBlocks(Stream.concat(classContainerBlocks, classDirectBlocks));
    }

    private void addConfigBlocks(Stream<Testing.AddConfigBlock> blocks) {
        blocks.forEach(block -> {
            configBuilder.addSource(ConfigSources.create(block.value(),
                                                         block.type().equals("yaml")
                                                                 ? APPLICATION_X_YAML
                                                                 : TEXT_X_JAVA_PROPERTIES));
        });
    }
}
