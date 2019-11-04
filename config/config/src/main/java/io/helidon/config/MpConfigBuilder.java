/*
 * Copyright (c) 2018, 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.config;

import java.lang.reflect.Array;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.spi.ConfigBuilder;
import org.eclipse.microprofile.config.spi.ConfigSource;
import org.eclipse.microprofile.config.spi.Converter;

/**
 * Configuration builder.
 */
public class MpConfigBuilder implements ConfigBuilder {
    private final BuilderImpl delegate = new BuilderImpl();

    MpConfigBuilder() {
        delegate.disableSystemPropertiesSource();
        delegate.disableEnvironmentVariablesSource();
    }

    @Override
    public ConfigBuilder addDefaultSources() {
        delegate.mpAddDefaultSources();
        return this;
    }

    @Override
    public ConfigBuilder addDiscoveredSources() {
        delegate.mpAddDiscoveredSources();
        return this;
    }

    @Override
    public ConfigBuilder addDiscoveredConverters() {
        delegate.mpAddDiscoveredConverters();
        return this;
    }

    @Override
    public ConfigBuilder forClassLoader(ClassLoader loader) {
        delegate.mpForClassLoader(loader);
        return this;
    }

    @Override
    public ConfigBuilder withSources(ConfigSource... sources) {
        delegate.mpWithSources(sources);
        return this;
    }

    @Override
    public <T> ConfigBuilder withConverter(Class<T> aClass, int ordinal, Converter<T> converter) {
        delegate.mpWithConverter(aClass, ordinal, converter);
        return this;
    }

    @Override
    public ConfigBuilder withConverters(Converter<?>... converters) {
        delegate.mpWithConverters(converters);
        return this;
    }

    @Override
    public Config build() {
        AbstractConfigImpl helidonConfig = delegate.build();

        AtomicReference<AbstractConfigImpl> configRef = new AtomicReference<>();
        configRef.set(helidonConfig);

        helidonConfig.onChange(it -> {
            configRef.set((AbstractConfigImpl) it);
        });

        return new MpConfig(configRef);
    }

    private static final class MpConfig implements Config {
        private final AtomicReference<AbstractConfigImpl> configRef;

        private MpConfig(AtomicReference<AbstractConfigImpl> configRef) {
            this.configRef = configRef;
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> T getValue(String propertyName, Class<T> propertyType) {
            try {
                if (propertyType.isArray()) {
                    Class<?> element = propertyType.getComponentType();
                    return (T) findArrayValue(propertyName, element);
                }

                return findValue(propertyName, propertyType);
            } catch (MissingValueException e) {
                throw new NoSuchElementException(e.getMessage());
            } catch (ConfigMappingException e) {
                throw new IllegalArgumentException(e);
            }
        }

        @Override
        public <T> Optional<T> getOptionalValue(String propertyName, Class<T> propertyType) {
            try {
                return Optional.of(getValue(propertyName, propertyType));
            } catch (NoSuchElementException e) {
                return Optional.empty();
            } catch (ConfigMappingException e) {
                throw new IllegalArgumentException(e);
            }
        }

        @Override
        public Iterable<String> getPropertyNames() {
            return configRef.get()
                    .asMap()
                    .orElseGet(Collections::emptyMap)
                    .keySet();
        }

        @Override
        public Iterable<ConfigSource> getConfigSources() {
            return configRef.get()
                    .sources();
        }

        private <T> T findValue(String propertyName, Class<T> propertyType) {
            return configRef.get()
                    .get(propertyName)
                    .as(propertyType)
                    .get();
        }

        private Object findArrayValue(String propertyName, Class<?> element) {
            // there should not be io.helidon.Config[]
            io.helidon.config.Config config = configRef.get().get(propertyName);

            List<?> objects = config.asList(element).get();
            Object array = Array.newInstance(element, objects.size());
            for (int i = 0; i < objects.size(); i++) {
                Array.set(array, i, objects.get(i));
            }

            return array;

        }
    }
}
