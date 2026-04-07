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
package io.helidon.dbclient.jdbc;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import io.helidon.config.Config;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class JdbcClientBuilderApiTest {

    @Test
    void testJdbcClientBuilderPublicApiShape() throws Exception {
        Method create = JdbcClientBuilder.class.getMethod("create");
        Method config = JdbcClientBuilder.class.getMethod("config", Config.class);
        Method parametersSetter = JdbcClientBuilder.class.getMethod("parametersSetter", JdbcParametersConfig.class);
        Method connectionPool = JdbcClientBuilder.class.getMethod("connectionPool", JdbcConnectionPool.class);

        assertThat(create.getReturnType().getName(), is(JdbcClientBuilder.class.getName()));
        assertThat(config.getReturnType().getName(), is(JdbcClientBuilder.class.getName()));
        assertThat(parametersSetter.getReturnType().getName(), is(JdbcClientBuilder.class.getName()));
        assertThat(connectionPool.getReturnType().getName(), is(JdbcClientBuilder.class.getName()));
        assertThat(Modifier.isPublic(create.getModifiers()), is(true));
        assertThat(Modifier.isPublic(config.getModifiers()), is(true));
        assertThat(Modifier.isPublic(parametersSetter.getModifiers()), is(true));
        assertThat(Modifier.isPublic(connectionPool.getModifiers()), is(true));
    }
}
