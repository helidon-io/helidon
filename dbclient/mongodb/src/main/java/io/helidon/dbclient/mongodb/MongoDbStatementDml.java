/*
 * Copyright (c) 2019, 2024 Oracle and/or its affiliates.
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

import io.helidon.dbclient.DbExecuteContext;
import io.helidon.dbclient.DbStatementDml;
import io.helidon.dbclient.DbStatementType;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import org.bson.Document;

/**
 * MongoDB {@link DbStatementDml} implementation.
 */
public class MongoDbStatementDml extends MongoDbStatement<DbStatementDml> implements DbStatementDml {

    private static final System.Logger LOGGER = System.getLogger(MongoDbStatementDml.class.getName());

    private final DbStatementType type;

    /**
     * Create a new instance.
     *
     * @param db      MongoDb instance
     * @param context context
     */
    MongoDbStatementDml(MongoDatabase db, DbStatementType type, DbExecuteContext context) {
        super(db, context);
        this.type = type;
    }

    @Override
    public DbStatementType statementType() {
        return type;
    }

    @Override
    public long execute() {
        return doExecute((future, context) -> {
            MongoStatement stmt = new MongoStatement(type, prepareStatement(context));
            try {
                long result = switch (type) {
                    case INSERT -> executeInsert(stmt);
                    case UPDATE -> executeUpdate(stmt);
                    case DELETE -> executeDelete(stmt);
                    default -> throw new UnsupportedOperationException(String.format(
                            "Statement operation not yet supported: %s",
                            type.name()));
                };
                future.complete(result);
                LOGGER.log(System.Logger.Level.DEBUG, () -> String.format(
                        "%s DML %s execution succeeded",
                        type.name(),
                        context().statementName()));
                return result;
            } catch (UnsupportedOperationException ex) {
                throw ex;
            } catch (Throwable throwable) {
                LOGGER.log(System.Logger.Level.DEBUG, () -> String.format(
                        "%s DML %s execution failed",
                        type.name(),
                        context().statementName()));
                throw throwable;
            }
        });
    }

    private Long executeInsert(MongoStatement stmt) {
        MongoCollection<Document> mc = db().getCollection(stmt.getCollection());
        mc.insertOne(stmt.getValue());
        return 1L;
    }

    private Long executeUpdate(MongoStatement stmt) {
        MongoCollection<Document> mc = db().getCollection(stmt.getCollection());
        Document query = stmt.getQuery();
        UpdateResult updateResult = mc.updateMany(query, stmt.getValue());
        return updateResult.getModifiedCount();
    }

    private Long executeDelete(MongoStatement stmt) {
        MongoCollection<Document> mc = db().getCollection(stmt.getCollection());
        Document query = stmt.getQuery();
        DeleteResult deleteResult = mc.deleteMany(query);
        return deleteResult.getDeletedCount();
    }
}
