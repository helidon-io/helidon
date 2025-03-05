/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
package io.helidon.data.jakarta.persistence.codegen;

import java.util.HashMap;
import java.util.Map;

import io.helidon.data.codegen.common.BaseGenerator;
import io.helidon.data.codegen.common.RepositoryInfo;
import io.helidon.data.codegen.common.spi.PersistenceGenerator;

import static io.helidon.data.jakarta.persistence.codegen.JpqlKeywords.COMMA;
import static io.helidon.data.jakarta.persistence.codegen.JpqlKeywords.DOUBLE_QUOTE;
import static io.helidon.data.jakarta.persistence.codegen.JpqlKeywords.LEFT_BRACKET;
import static io.helidon.data.jakarta.persistence.codegen.JpqlKeywords.RIGHT_BRACKET;
import static io.helidon.data.jakarta.persistence.codegen.JpqlKeywords.SPACE;

/**
 * Jakarta Persistence base generator.
 */
abstract class JakartaPersistenceBaseBuilder extends BaseGenerator {

    private final RepositoryInfo repositoryInfo;
    private final Map<CharSequence, PersistenceGenerator.QuerySettings> setParameter;

    JakartaPersistenceBaseBuilder(RepositoryInfo repositoryInfo) {
        this.repositoryInfo = repositoryInfo;
        this.setParameter = new HashMap<>();
    }

    protected RepositoryInfo repositoryInfo() {
        return repositoryInfo;
    }

    protected Map<CharSequence, PersistenceGenerator.QuerySettings> setParameter() {
        return setParameter;
    }

    protected record Limit(int count) implements PersistenceGenerator.QuerySettings {

        private static final String SET = "setMaxResults";

        @Override
        public CharSequence code() {
            // Integer needs 10 characters at most
            return new StringBuilder(SET.length() + 12)
                    .append(SET)
                    .append(LEFT_BRACKET)
                    .append(count)
                    .append(RIGHT_BRACKET);
        }

    }

    protected record Param(CharSequence name) implements PersistenceGenerator.QuerySettings {

        private static final String SET = "setParameter";

        @Override
        public CharSequence code() {
            return new StringBuilder(SET.length() + 2 * name.length() + 6)
                    .append(SET)
                    .append(LEFT_BRACKET)
                    .append(DOUBLE_QUOTE)
                    .append(name)
                    .append(DOUBLE_QUOTE)
                    .append(COMMA)
                    .append(SPACE)
                    .append(name)
                    .append(RIGHT_BRACKET);
        }

    }

}
