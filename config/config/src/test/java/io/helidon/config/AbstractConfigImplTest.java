/*
 * Copyright (c) 2017, 2019 Oracle and/or its affiliates. All rights reserved.
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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Flow;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import io.helidon.common.GenericType;
import io.helidon.config.internal.ConfigKeyImpl;
import io.helidon.config.spi.ConfigFilter;
import io.helidon.config.spi.ConfigNode;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.hamcrest.CoreMatchers.sameInstance;
import static org.junit.Assert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests of the {@link AbstractConfigImpl} class.
 */
public class AbstractConfigImplTest {

    @Test
    void testConfigCorrectUnlocking() {
        // GIVEN
        RuntimeException exception = new RuntimeException("LOCKS UNLOCK TEST");

        // a provider with a publisher that throws an exception when calling 'subscribe()'
        ProviderImpl providerMock = mock(ProviderImpl.class);
        Flow.Publisher<ConfigDiff> publisherMock = Mockito.mock(Flow.Publisher.class);
        when(providerMock.changes()).thenReturn(publisherMock);
        Mockito.doThrow(exception).when(publisherMock).subscribe(Mockito.any());

        // and an almost empty stub of AbstractConfigImpl
        AbstractConfigImpl config = configStub(
                Config.Type.LIST,
                mock(ConfigKeyImpl.class),
                mock(ConfigKeyImpl.class),
                new ConfigFactory(
                        mock(ConfigMapperManager.class),
                        mock(ConfigNode.ObjectNode.class),
                        mock(ConfigFilter.class),
                        providerMock,
                        key -> Collections.emptyList(),
                        Collections.emptyList()),
                mock(ConfigMapperManager.class));

        // WHEN we reproduce the issue https://github.com/oracle/helidon/issues/299
        RuntimeException runtimeException = assertThrows(RuntimeException.class, config::subscribe);

        // THEN we get the exception we threw and not a 'java.lang.IllegalMonitorStateException'
        assertThat(runtimeException, sameInstance(exception));
    }

    private AbstractConfigImpl configStub(Config.Type type, final ConfigKeyImpl prefix, final ConfigKeyImpl key,
            final ConfigFactory factory, final ConfigMapperManager configMapperManager) {
        return new AbstractConfigImpl(type, prefix, key, factory, configMapperManager) {
            @Override
            public boolean hasValue() {
                return false;
            }

            @Override
            public Stream<Config> traverse(Predicate<Config> predicate) {
                return null;
            }

            @Override
            public <T> ConfigValue<T> as(GenericType<T> genericType) {
                return null;
            }

            @Override
            public <T> ConfigValue<T> as(Class<T> type) {
                return null;
            }

            @Override
            public <T> ConfigValue<T> as(Function<Config, T> mapper) {
                return null;
            }

            @Override
            public <T> ConfigValue<List<T>> asList(Class<T> type) throws ConfigMappingException {
                return null;
            }

            @Override
            public <T> ConfigValue<List<T>> asList(Function<Config, T> mapper) throws ConfigMappingException {
                return null;
            }

            @Override
            public ConfigValue<Map<String, String>> asMap() throws MissingValueException {
                return null;
            }
        };
    }
}
