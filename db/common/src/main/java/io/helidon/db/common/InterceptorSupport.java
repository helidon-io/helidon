/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.db.common;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import io.helidon.common.CollectionsHelper;
import io.helidon.common.configurable.LruCache;
import io.helidon.db.DbInterceptor;
import io.helidon.db.StatementType;

/**
 * Support for interceptors.
 */
public interface InterceptorSupport {
    /**
     * Get a list of interceptors to be executed for the specified statement.
     *
     * @param statementType Type of the statement
     * @param statementName Name of the statement (unnamed statements should have a name generated, see
     *                  {@link AbstractDbExecute#generateName(io.helidon.db.StatementType, String)}
     * @return list of interceptors to executed for the defined type and name (may be empty)
     * @see io.helidon.db.DbInterceptor
     */
    List<DbInterceptor> interceptors(StatementType statementType, String statementName);

    /**
     * Create a new fluent API builder.
     *
     * @return a builder instance
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * Fluent API builder for {@link io.helidon.db.common.InterceptorSupport}.
     */
    final class Builder implements io.helidon.common.Builder<InterceptorSupport> {
        private final List<DbInterceptor> interceptors = new LinkedList<>();
        private final Map<StatementType, List<DbInterceptor>> typeInterceptors = new EnumMap<>(StatementType.class);
        private final Map<String, List<DbInterceptor>> namedStatementInterceptors = new HashMap<>();

        private Builder() {
        }

        @Override
        public InterceptorSupport build() {
            // the result must be immutable (if somebody modifies the builder, the behavior must not change)
            List<DbInterceptor> interceptors = new LinkedList<>(this.interceptors);
            final Map<StatementType, List<DbInterceptor>> typeInterceptors = new EnumMap<>(this.typeInterceptors);
            final Map<String, List<DbInterceptor>> namedStatementInterceptors = new HashMap<>(this.namedStatementInterceptors);

            final LruCache<CacheKey, List<DbInterceptor>> cachedInterceptors = LruCache.create();
            return new InterceptorSupport() {
                @Override
                public List<DbInterceptor> interceptors(StatementType statementType, String statementName) {
                    // order is defined in DbInterceptor interface
                    return cachedInterceptors.computeValue(new CacheKey(statementType, statementName), () -> {
                        List<DbInterceptor> result = new LinkedList<>();
                        addAll(result, namedStatementInterceptors.get(statementName));
                        addAll(result, typeInterceptors.get(statementType));
                        result.addAll(interceptors);
                        return Optional.of(Collections.unmodifiableList(result));
                    }).orElseGet(CollectionsHelper::listOf);
                }

                private void addAll(List<DbInterceptor> result, List<DbInterceptor> dbInterceptors) {
                    if (null == dbInterceptors) {
                        return;
                    }
                    result.addAll(dbInterceptors);
                }
            };
        }

        public Builder add(DbInterceptor interceptor) {
            this.interceptors.add(interceptor);
            return this;
        }

        public Builder add(DbInterceptor interceptor, String... statementNames) {
            for (String statementName : statementNames) {
                this.namedStatementInterceptors.computeIfAbsent(statementName, theName -> new LinkedList<>())
                        .add(interceptor);
            }
            return this;
        }

        public Builder add(DbInterceptor interceptor, StatementType... statementTypes) {
            for (StatementType statementType : statementTypes) {
                this.typeInterceptors.computeIfAbsent(statementType, theType -> new LinkedList<>())
                        .add(interceptor);
            }
            return this;
        }

        private static final class CacheKey {
            private final StatementType type;
            private final String statementName;

            private CacheKey(StatementType type, String statementName) {
                this.type = type;
                this.statementName = statementName;
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) {
                    return true;
                }
                if (!(o instanceof CacheKey)) {
                    return false;
                }
                CacheKey cacheKey = (CacheKey) o;
                return (type == cacheKey.type)
                        && statementName.equals(cacheKey.statementName);
            }

            @Override
            public int hashCode() {
                return Objects.hash(type, statementName);
            }
        }
    }
}
