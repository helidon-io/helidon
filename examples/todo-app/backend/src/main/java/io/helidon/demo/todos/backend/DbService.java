/*
 * Copyright (c) 2017, 2023 Oracle and/or its affiliates.
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
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

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
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * A service showing to access a no-SQL database.
 */
@ApplicationScoped
public class DbService {

    private static final String LIST_QUERY = "select * from backend where user = ? ALLOW FILTERING";
    private static final String GET_QUERY = "select * from backend where id = ?";
    private static final String INSERT_QUERY = "insert into backend (id, user, message, completed, created)"
            + " values (?, ?, ?, ?, ?)";
    private static final String UPDATE_QUERY = "update backend set message = ?, completed = ? where id = ? if user = ?";
    private static final String DELETE_QUERY = "delete from backend where id = ?";

    private final Session session;
    private final PreparedStatement listStatement;
    private final PreparedStatement getStatement;
    private final PreparedStatement insertStatement;
    private final PreparedStatement updateStatement;
    private final PreparedStatement deleteStatement;

    /**
     * Create a new {@code DbService} instance.
     * @param config the configuration root
     */
    @Inject
    public DbService(Config config) {
        Cluster.Builder clusterBuilder = Cluster.builder()
                .withoutMetrics();

        Config cConfig = config.get("cassandra");
        cConfig.get("servers").asList(Config.class).stream()
                .flatMap(Collection::stream)
                .map(server -> server.get("host").asString().get())
                .forEach(clusterBuilder::addContactPoints);
        cConfig.get("port").asInt().ifPresent(clusterBuilder::withPort);

        Cluster cluster = clusterBuilder.build();
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
    private static <T> T execute(SpanContext tracingSpan, String operation, Supplier<T> supplier) {
        Span span = startSpan(tracingSpan, operation);

        try {
            return supplier.get();
        } catch (Exception e) {
            Tags.ERROR.set(span, true);
            span.log(Map.of("event", "error", "error.object", e));
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
    private static Span startSpan(SpanContext span, String operation) {
        return GlobalTracer.get().buildSpan(operation).asChildOf(span).start();
    }

    /**
     * Retrieve the TODOs entries from the database.
     * @param tracingSpan the tracing span to use
     * @param userId the database user id
     * @return retrieved entries as {@code Iterable}
     */
    Iterable<Todo> list(SpanContext tracingSpan, String userId) {
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
     * Get the entry identified by the given ID from the database.
     * @param tracingSpan the tracing span to use
     * @param id the ID identifying the entry to retrieve
     * @param userId the database user id
     * @return retrieved entry as {@code Optional}
     */
    Optional<Todo> get(SpanContext tracingSpan, String id, String userId) {
        return execute(tracingSpan, "cassandra::get", () -> getNoContext(id, userId));
    }

    /**
     * Get the entry identified by the given ID from the database, fails if the
     * entry is not associated with the given {@code userId}.
     * @param id the ID identifying the entry to retrieve
     * @param userId the database user id
     * @return retrieved entry as {@code Optional}
     */
    private Optional<Todo> getNoContext(String id, String userId) {
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
        throw new SecurityException(String.format(
                "User %s attempted to read record %s of another user",
                userId, id));
    }

    /**
     * Update the given entry in the database.
     * @param tracingSpan the tracing span to use
     * @param entry the entry to update
     * @return {@code Optional} of updated entry if the update was successful,
     * otherwise an empty {@code Optional}
     */
    Optional<Todo> update(SpanContext tracingSpan, Todo entry) {
        return execute(tracingSpan, "cassandra::update", () -> {
            //update backend set message = ?
            // , completed = ? where id = ? if user = ?
            BoundStatement bs = updateStatement.bind(
                    entry.getTitle(),
                    entry.getCompleted(),
                    entry.getId(),
                    entry.getUserId());
            ResultSet execute = session.execute(bs);

            if (execute.wasApplied()) {
                return Optional.of(entry);
            } else {
                return Optional.empty();
            }
        });
    }

    /**
     * Delete the entry identified by the given ID in from the database.
     * @param tracingSpan the tracing span to use
     * @param id the ID identifying the entry to delete
     * @param userId the database user id
     * @return the deleted entry as {@code Optional}
     */
    Optional<Todo> delete(SpanContext tracingSpan, String id, String userId) {
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
     * Insert a new entry in the database.
     * @param tracingSpan the tracing span to use
     * @param entry the entry to insert
     */
    void insert(SpanContext tracingSpan, Todo entry) {
        execute(tracingSpan, "cassandra::insert", () -> {
            BoundStatement bs = insertStatement
                    .bind(entry.getId(),
                          entry.getUserId(),
                          entry.getTitle(),
                          entry.getCompleted(),
                          new Date(entry.getCreated()));

            ResultSet execute = session.execute(bs);
            if (!execute.wasApplied()) {
                throw new RuntimeException("Failed to insert todo: " + entry);
            }
            return null;
        });
    }
}
