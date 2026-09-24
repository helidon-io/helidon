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

package io.helidon.data.jakarta.persistence;

import java.util.List;
import java.util.Set;

import io.helidon.data.jakarta.persistence.LocalTransactionStorage.LocalTransactionManager;
import io.helidon.data.jakarta.persistence.LocalTransactionStorage.LocalTransactionProvider;

import jakarta.persistence.spi.PersistenceUnitInfo;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.nullValue;

class PersistenceConfigurationTest {

    @Test
    void testNoCdiAnnotations() {
        var provider = new LocalTransactionProvider(new LocalTransactionManager());
        var txSupport = new LocalTxSupport(provider, List.of());
        var config = JpaPersistenceUnitConfig.builder()
                .dataSource(new JdbcDataSource())
                .build();
        PersistenceUnitInfo unitInfo = PersistenceConfiguration.create("test",
                                                                       txSupport,
                                                                       Set.of(),
                                                                       List::of,
                                                                       config);

        assertThat("Unspecified CDI scope", unitInfo.getScopeAnnotationName(), nullValue());
        assertThat("Unspecified CDI qualifiers", unitInfo.getQualifierAnnotationNames(), empty());
    }
}
