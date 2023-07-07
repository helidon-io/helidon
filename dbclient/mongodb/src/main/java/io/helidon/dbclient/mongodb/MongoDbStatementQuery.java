/*
 * Copyright (c) 2019, 2023 Oracle and/or its affiliates.
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
package io.helidon.dbclient.mongodb;

import java.lang.System.Logger.Level;
import java.util.Spliterator;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import io.helidon.dbclient.DbClientException;
import io.helidon.dbclient.DbExecuteContext;
import io.helidon.dbclient.DbRow;
import io.helidon.dbclient.DbStatementQuery;
import io.helidon.dbclient.DbStatementType;

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;

import static java.util.Spliterators.spliteratorUnknownSize;

/**
 * MongoDB {@link DbStatementQuery} implementation.
 */
public class MongoDbStatementQuery extends MongoDbStatement<DbStatementQuery> implements DbStatementQuery {

    private static final System.Logger LOGGER = System.getLogger(MongoDbStatementQuery.class.getName());

    /**
     * Create a new instance.
     *
     * @param db      MongoDb instance
     * @param context context
     */
    MongoDbStatementQuery(MongoDatabase db, DbExecuteContext context) {
        super(db, context);
    }

    @Override
    public DbStatementType statementType() {
        return DbStatementType.QUERY;
    }

    @Override
    public Stream<DbRow> execute() {
        return doExecute((future, context) -> {
            String preparedStmt = prepareStatement(context);
            try {
                MongoStatement stmt = queryOrCommand(preparedStmt);
                return switch (stmt.getOperation()) {
                    case QUERY -> executeQuery(stmt);
                    case COMMAND -> executeCommand(stmt);
                    default -> throw new UnsupportedOperationException(String.format(
                            "Operation %s is not supported by query", stmt.getOperation().toString()));
                };
            } catch (Exception e) {
                throw new DbClientException(e.getMessage(), e);
            }
        });
    }

    private MongoStatement queryOrCommand(String statement) {
        try {
            return new MongoStatement(DbStatementType.QUERY, statement);
        } catch (IllegalStateException e) {
            // maybe this is a command?
            try {
                return new MongoStatement(DbStatementType.COMMAND, statement);
            } catch (IllegalStateException ignored) {
                // we want to report the original exception
                throw e;
            }
        }
    }

    private Stream<DbRow> executeCommand(MongoStatement stmt) {
        Document command = stmt.getQuery();
        LOGGER.log(Level.DEBUG, () -> String.format("Command: %s", command.toString()));
        Document doc = db().runCommand(command);
        return Stream.of(new MongoDbRow(doc, context()));
    }

    private Stream<DbRow> executeQuery(MongoStatement stmt) {
        MongoCollection<Document> mc = db().getCollection(stmt.getCollection());
        Document query = stmt.getQuery();
        Document projection = stmt.getProjection();

        LOGGER.log(Level.DEBUG, () -> String.format(
                "Query: %s, Projection: %s",
                query.toString(),
                (projection != null ? projection : "N/A")));

        FindIterable<Document> finder = mc.find(query);
        if (projection != null) {
            finder = finder.projection(projection);
        }

        Spliterator<Document> spliterator = spliteratorUnknownSize(finder.iterator(), Spliterator.ORDERED);
        Stream<Document> stream = StreamSupport.stream(spliterator, false);
        return stream.map(doc -> new MongoDbRow(doc, context()));
    }
}
