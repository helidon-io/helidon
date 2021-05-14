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

package io.helidon.dbclient.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

import io.helidon.common.serviceloader.HelidonServiceLoader;
import io.helidon.dbclient.jdbc.spi.JdbcCustomizationsProvider;

/**
 * JDBC DbClient customization manager.
 */
class JdbcCustomizationsManager {

    private final JdbcCustomizationsProvider.PrepareNamedStatement prepareNamedStatement;
    private final JdbcCustomizationsProvider.PrepareIndexedStatement prepareIndexedStatement;

    private JdbcCustomizationsManager(
            final JdbcCustomizationsProvider.PrepareNamedStatement prepareNamedStatement,
            final JdbcCustomizationsProvider.PrepareIndexedStatement prepareIndexedStatement
    ) {
        this.prepareNamedStatement = prepareNamedStatement != null
                ? prepareNamedStatement
                : new JdbcStatement.PrepareNamedStatement();
        this.prepareIndexedStatement = prepareIndexedStatement != null
                ? prepareIndexedStatement
                : new JdbcStatement.PrepareIndexedStatement();
    }

    PreparedStatement prepareNamedStatement(
                Connection connection,
                String statementName,
                String statement,
                Map<String, Object> parameters) {
        return prepareNamedStatement.apply(connection, statementName, statement, parameters);
    }

    PreparedStatement prepareIndexedStatement(
                Connection connection,
                String statementName,
                String statement,
                List<Object> parameters) {
        return prepareIndexedStatement.apply(connection, statementName, statement, parameters);
    }

    static JdbcCustomizationsManager create() {
        final HelidonServiceLoader<JdbcCustomizationsProvider> providers
                = HelidonServiceLoader.builder(ServiceLoader.load(JdbcCustomizationsProvider.class)).build();
        final Builder builder = new Builder();
        providers.forEach(provider -> builder.customization(provider));
        return builder.build();
    }

    private static class Builder {

        private JdbcCustomizationsProvider.PrepareNamedStatement prepareNamedStatement;
        private JdbcCustomizationsProvider.PrepareIndexedStatement prepareIndexedStatement;

        private Builder customization(final JdbcCustomizationsProvider provider) {
            if (provider.prepareNamedStatement() != null) {
                prepareNamedStatement = provider.prepareNamedStatement();
            }
            if (provider.prepareIndexedStatement() != null) {
                prepareIndexedStatement = provider.prepareIndexedStatement();
            }
            return this;
        }

        private JdbcCustomizationsManager build() {
            return new JdbcCustomizationsManager(prepareNamedStatement, prepareIndexedStatement);
        }
    }

}
