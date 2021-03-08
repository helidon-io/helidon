/*
 * Copyright (c) 2017, 2021 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.demo.todos.backend;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import io.helidon.config.Config;
import io.helidon.security.SecurityException;

import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;

/**
 * A service showing to access a no-SQL database.
 */
@ApplicationScoped
public class DbService {

    /**
     * The database query for retrieving all entries.
     */
    private static final String LIST_QUERY =
            "select * from backend where user = ? ALLOW FILTERING";

    /**
     * The database query for retrieving a single entry.
     */
    private static final String GET_QUERY =
            "select * from backend where id = ?";

    /**
     * The database query for inserting a new entry.
     */
    private static final String INSERT_QUERY =
            "insert into backend (id, user, message, completed, created)"
            + " values (?, ?, ?, ?, ?)";

    /**
     * The database query for updating an existing entry.
     */
    private static final String UPDATE_QUERY =
            "update backend set message = ?, completed = ?"
            + " where id = ? if user = ?";

    /**
     * The database query for deleting an entry.
     */
    private static final String DELETE_QUERY =
            "delete from backend where id = ?";

    /**
     * The database session.
     */
    private final Session session;

    /**
     * The database statement for retrieving all entries.
     */
    private final PreparedStatement listStatement;

    /**
     * The database statement for retrieving a single entry.
     */
    private final PreparedStatement getStatement;

    /**
     * The database statement for inserting a new entry.
     */
    private final PreparedStatement insertStatement;

    /**
     * The database statement for updating an existing entry.
     */
    private final PreparedStatement updateStatement;

    /**
     * The database statement for deleting an entry.
     */
    private final PreparedStatement deleteStatement;

    /**
     * The database cluster.
     */
    private final Cluster cluster;

    /**
     * Create a new {@code DbService} instance.
     * @param config the configuration root
     */
    @Inject
    public DbService(final Config config) {
        Cluster.Builder clusterBuilder = Cluster.builder()
                .withoutMetrics();

        Config cConfig = config.get("cassandra");
        cConfig.get("servers").asList(Config.class).get().forEach(serverConfig -> {
            clusterBuilder.addContactPoints(
                    serverConfig.get("host").asString().get());
        });
        cConfig.get("port").asInt().ifPresent(clusterBuilder::withPort);

        cluster = clusterBuilder.build();
        session = cluster.connect("backend");

        listStatement = session.prepare(LIST_QUERY);
        getStatement = session.prepare(GET_QUERY);
        insertStatement = session.prepare(INSERT_QUERY);
        updateStatement = session.prepare(UPDATE_QUERY);
        deleteStatement = session.prepare(DELETE_QUERY);
    }

    /**
     * Invoke the given supplier and wrap it around with a tracing
     * {@code Span}.
     * @param <T> the supplier return type
     * @param tracingSpan the parent span to use
     * @param operation the name of the operation
     * @param supplier the supplier to invoke
     * @return the object returned by the supplier
     */
    private static <T> T execute(final SpanContext tracingSpan,
                                 final String operation,
                                 final Supplier<T> supplier) {

        Span span = startSpan(tracingSpan, operation);

        try {
            return supplier.get();
        } catch (Exception e) {
            Tags.ERROR.set(span, true);
            span.log(Map.of("event", "error",
                            "error.object", e));
            throw e;
        } finally {
            span.finish();
        }
    }

    /**
     * Utility method to create and start a child span of the given span.
     * @param span the parent span
     * @param operation the name for the new span
     * @return the created span
     */
    private static Span startSpan(final SpanContext span,
                                  final String operation) {

        return GlobalTracer.get()
                .buildSpan(operation).asChildOf(span).start();
    }

    /**
     * Retrieve the TODOs entries from the database.
     * @param tracingSpan the tracing span to use
     * @param userId the database user id
     * @return retrieved entries as {@code Iterable}
     */
    Iterable<Todo> list(final SpanContext tracingSpan,
                        final String userId) {

        return execute(tracingSpan, "cassandra::list", () -> {
            BoundStatement bs = listStatement.bind(userId);
            ResultSet rs = session.execute(bs);

            List<Todo> result = new ArrayList<>();
            for (Row r : rs) {
                result.add(Todo.fromDb(r));
            }

            return result;
        });
    }

    /**
     * Get the TODO entry identified by the given ID from the database.
     * @param tracingSpan the tracing span to use
     * @param id the ID identifying the entry to retrieve
     * @param userId the database user id
     * @return retrieved entry as {@code Optional}
     */
    Optional<Todo> get(final SpanContext tracingSpan,
                       final String id,
                       final String userId) {

        return execute(tracingSpan, "cassandra::get",
                () -> getNoContext(id, userId));
    }

    /**
     * Get the TODO identified by the given ID from the database, fails if the
     * entry is not associated with the given {@code userId}.
     * @param id the ID identifying the entry to retrieve
     * @param userId the database user id
     * @return retrieved entry as {@code Optional}
     */
    private Optional<Todo> getNoContext(final String id,
                                        final String userId) {

        BoundStatement bs = getStatement.bind(id);
        ResultSet rs = session.execute(bs);
        Row one = rs.one();
        if (null == one) {
            return Optional.empty();
        }
        Todo result = Todo.fromDb(one);
        if (userId.equals(result.getUserId())) {
            return Optional.of(result);
        }
        throw new SecurityException("User " + userId
                + " attempted to read record "
                + id + " of another user");
    }

    /**
     * Update the given TODO entry in the database.
     * @param tracingSpan the tracing span to use
     * @param todo the entry to update
     * @return {@code Optional} of updated entry if the update was successful,
     * otherwise an empty {@code Optional}
     */
    Optional<Todo> update(final SpanContext tracingSpan, final Todo todo) {
        return execute(tracingSpan, "cassandra::update", () -> {
            //update backend set message = ?
            // , completed = ? where id = ? if user = ?
            BoundStatement bs = updateStatement.bind(
                    todo.getTitle(),
                    todo.getCompleted(),
                    todo.getId(),
                    todo.getUserId());
            ResultSet execute = session.execute(bs);

            if (execute.wasApplied()) {
                return Optional.of(todo);
            } else {
                return Optional.empty();
            }
        });
    }

    /**
     * Delete the TODO entry identified by the given ID in from the database.
     * @param tracingSpan the tracing span to use
     * @param id the ID identifying the entry to delete
     * @param userId the database user id
     * @return the deleted entry as {@code Optional}
     */
    Optional<Todo> delete(final SpanContext tracingSpan,
                          final String id,
                          final String userId) {

        return execute(tracingSpan, "cassandra::delete",
                () -> getNoContext(id, userId)
                .map(todo -> {
                    BoundStatement bs = deleteStatement.bind(id);
                    ResultSet rs = session.execute(bs);
                    if (!rs.wasApplied()) {
                        throw new RuntimeException("Failed to delete todo: "
                                + todo);
                    }
                    return todo;
                }));
    }

    /**
     * Insert a new TODO entry in the database.
     * @param tracingSpan the tracing span to use
     * @param todo the entry to insert
     */
    void insert(final SpanContext tracingSpan, final Todo todo) {
        execute(tracingSpan, "cassandra::insert", () -> {
            BoundStatement bs = insertStatement
                    .bind(todo.getId(),
                          todo.getUserId(),
                          todo.getTitle(),
                          todo.getCompleted(),
                          new Date(todo.getCreated()));

            ResultSet execute = session.execute(bs);
            if (!execute.wasApplied()) {
                throw new RuntimeException("Failed to insert todo: "
                        + todo);
            }
            return null;
        });
    }
}
