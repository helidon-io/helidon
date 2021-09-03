/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

package io.helidon.dbclient.common;

import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import io.helidon.common.reactive.Single;
import io.helidon.config.Config;
import io.helidon.dbclient.DbClientService;
import io.helidon.dbclient.DbClientServiceContext;
import io.helidon.dbclient.DbStatementType;

/**
 * A base implementation of a client service that supports configuration
 * of execution based on a statement name pattern and statement types.
 */
public abstract class DbClientServiceBase implements DbClientService {
    private final Predicate<DbClientServiceContext> predicate;

    /**
     * Create a new instance based on the builder base each implementation must extend.
     *
     * @param builder builder to configure predicate to use
     */
    protected DbClientServiceBase(DbClientServiceBuilderBase<?> builder) {
        this.predicate = builder.predicate();
    }

    @Override
    public final Single<DbClientServiceContext> statement(DbClientServiceContext context) {
        if (predicate.test(context)) {
            return apply(context);
        }
        return Single.just(context);
    }

    /**
     * This method is only invoked if the predicate for this service
     * was passed.
     *
     * @param context db client invocation context
     * @return single with the new context (or the same one if not modified)
     * @see #statement(io.helidon.dbclient.DbClientServiceContext)
     */
    protected abstract Single<DbClientServiceContext> apply(DbClientServiceContext context);

    /**
     * A base class for builders of {@link DbClientServiceBase}.
     *
     * @param <B> type of the builder extending this class
     */
    public abstract static class DbClientServiceBuilderBase<B extends DbClientServiceBuilderBase<B>> {
        private static final Predicate<DbClientServiceContext> YES = it -> true;
        private static final Predicate<DbClientServiceContext> NO = it -> false;

        @SuppressWarnings("unchecked")
        private final B me = (B) this;

        // we can filter by statement name
        private Set<String> statementNames = new LinkedHashSet<>();
        // and statement type
        private Set<DbStatementType> statementTypes = EnumSet.noneOf(DbStatementType.class);
        private Predicate<DbClientServiceContext> predicate;
        private boolean enabled = true;

        /**
         * No-op constructor.
         */
        protected DbClientServiceBuilderBase() {
        }

        /**
         * Configure this client service from config.
         * <p>
         * Supported keys:
         * <table class="config">
         * <caption>DB Client Service configuration options</caption>
         * <tr>
         *  <th>key</th>
         *  <th>default value</th>
         *  <th>description</th>
         * </tr>
         * <tr>
         *  <td>statement-names</td>
         *  <td>&nbsp;</td>
         *  <td>An array of statement name patterns to apply this service for. If undefined, service
         *  would be executed for all statements.
         *  See {@link #statementNames(String...)} and {@link java.util.regex.Pattern}</td>
         * </tr>
         * <tr>
         *  <td>statement-types</td>
         *  <td>&nbsp;</td>
         *  <td>An array of statement types to apply this service for. If undefined, service
         *  would be executed for all statements.
         *  See {@link #statementTypes(io.helidon.dbclient.DbStatementType...)}.</td>
         * </tr>
         * <tr>
         *  <td>enabled</td>
         *  <td>{@code true}</td>
         *  <td>Whether this client service is enabled. See {@link #enabled(boolean)}</td>
         * </tr>
         * </table>
         *
         * @param config configuration on the node of this service
         * @return updated builder instance
         */
        public B config(Config config) {
            config.get("statement-names").asList(String.class).ifPresent(this::statementNames);
            config.get("statement-types").asList(cfg -> cfg.asString().map(DbStatementType::valueOf).get())
                    .ifPresent(this::statementTypes);
            config.get("enabled").asBoolean().ifPresent(this::enabled);
            return me;
        }

        /**
         * Configure a predicate whose result will be used to decide whether to
         * trigger this service or not.
         * <p>
         * When a predicate is explicitly configured, {@link #statementNames(String...)}
         * and {@link #statementTypes(io.helidon.dbclient.DbStatementType...)} is ignored.
         *
         * @param predicate predicate that should return {@code true} to enable this
         *                  service, or {@code false} to disable it
         * @return updated builder instance
         */
        public B statementPredicate(Predicate<DbClientServiceContext> predicate) {
            this.predicate = predicate;
            return me;
        }

        /**
         * Configure statement types this service will be triggered for.
         * If an explicit {@link #statementPredicate(java.util.function.Predicate)} is configured,
         * this method is ignored.
         *
         * @param types types that trigger this service
         * @return updated builder instance
         */
        public B statementTypes(DbStatementType... types) {
            return statementTypes(List.of(types));
        }

        /**
         * Configure statement name patterns this service will be triggered for.
         * If an explicit {@link #statementPredicate(java.util.function.Predicate)} is configured,
         * this method is ignored.
         *
         * @param names name patterns (as in {@link java.util.regex.Pattern}) that trigger this service
         * @return updated builder instance
         */
        public B statementNames(String... names) {
            return statementNames(List.of(names));
        }

        /**
         * Configure whether this service is enabled or not.
         *
         * @param enabled whether to enable this service or disable it, {@code true} by default
         */
        public void enabled(boolean enabled) {
            this.enabled = enabled;
        }

        /**
         * Configures statement types from configuration.
         *
         * @param types types to add for this service
         * @return updated builder instance
         */
        protected B statementTypes(List<DbStatementType> types) {
            this.statementTypes.addAll(types);
            return me;
        }

        /**
         * Configures statement name patterns from configuration.
         *
         * @param names names to add for this service
         * @return updated builder instance
         */
        protected B statementNames(List<String> names) {
            this.statementNames.addAll(names);
            return me;
        }

        /**
         * Set of statement name patterns.
         *
         * @return configured statement names
         */
        protected Set<String> statementNames() {
            return statementNames;
        }

        /**
         * Set of statement types.
         *
         * @return configured statement types
         */
        protected Set<DbStatementType> statementTypes() {
            return statementTypes;
        }

        /**
         * Predicate used to build a client service.
         * <p>
         * The predicate always returns {@code false} if service is disabled.
         * <p>
         * The predicate is obtained from the configured predicate using
         * {@link #statementPredicate(java.util.function.Predicate)},
         * if none is configured, it is created from configured statement types and statement names.
         * If none are configured, the predicate just returns {@code true}.
         *
         * @return predicate to check whether this service should be invoked for current statement context
         */
        protected Predicate<DbClientServiceContext> predicate() {
            if (!enabled) {
                return NO;
            }

            if (null != predicate) {
                return predicate;
            }

            List<Pattern> namePatterns = statementNames.stream()
                    .map(Pattern::compile)
                    .collect(Collectors.toList());

            Set<DbStatementType> types = EnumSet.copyOf(statementTypes);

            Predicate<DbClientServiceContext> namePredicate;
            Predicate<DbClientServiceContext> typePredicate;
            if (namePatterns.isEmpty()) {
                namePredicate = YES;
            } else {
                namePredicate = it -> {
                    String statementName = it.statementName();
                    for (Pattern namePattern : namePatterns) {
                        if (namePattern.matcher(statementName).matches()) {
                            return true;
                        }
                    }
                    return false;
                };
            }
            if (types.isEmpty()) {
                typePredicate = YES;
            } else {
                typePredicate = it -> types.contains(it.statementType());
            }

            return context -> namePredicate.test(context) && typePredicate.test(context);
        }
    }
}
