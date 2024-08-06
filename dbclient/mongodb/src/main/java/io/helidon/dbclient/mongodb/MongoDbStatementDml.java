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

import java.util.List;
import java.util.stream.Stream;

import io.helidon.dbclient.DbExecuteContext;
import io.helidon.dbclient.DbResultDml;
import io.helidon.dbclient.DbStatementDml;
import io.helidon.dbclient.DbStatementType;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.InsertOneResult;
import com.mongodb.client.result.UpdateResult;
import org.bson.BsonDocument;
import org.bson.BsonDocumentReader;
import org.bson.BsonValue;
import org.bson.Document;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.DocumentCodec;

/**
 * MongoDB {@link DbStatementDml} implementation.
 */
public class MongoDbStatementDml extends MongoDbStatement<DbStatementDml> implements DbStatementDml {

    private static final System.Logger LOGGER = System.getLogger(MongoDbStatementDml.class.getName());
    // Whether generated ID shall be returned
    private boolean returnGeneratedKeys;
    private final DbStatementType type;
    private final DocumentCodec codec = new DocumentCodec();
    private final DecoderContext decoderContext = DecoderContext.builder().build();

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
                    case DML -> executeDml(stmt);
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

    @Override
    @SuppressWarnings("resource") // DbResultDml life-cycle is handled by user of this method
    public DbResultDml insert() {
        return doExecute((future, context) -> {
            MongoStatement stmt = new MongoStatement(type, prepareStatement(context));
            try {
                DbResultDml result = switch (type) {
                    case INSERT -> executeInsertAsDbResultDml(stmt);
                    case UPDATE -> executeUpdateAsDbResultDml(stmt);
                    case DELETE -> executeDeleteAsDbResultDml(stmt);
                    case DML -> executeDmlAsDbResultDml(stmt);
                    default -> throw new UnsupportedOperationException(String.format(
                            "Statement operation not yet supported: %s",
                            type.name()));
                };
                future.complete(result.result());
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

    @Override
    public DbStatementDml returnGeneratedKeys() {
        returnGeneratedKeys = true;
        return this;
    }

    @Override
    public DbStatementDml returnColumns(List<String> columnNames) {
        throw new UnsupportedOperationException("Retrieval of specific auto-generated columns is not supported for Mongo");
    }

    private Long executeInsert(MongoStatement stmt) {
        MongoCollection<Document> mc = db().getCollection(stmt.getCollection());
        mc.insertOne(stmt.getValue());
        return 1L;
    }

    private DbResultDml executeInsertAsDbResultDml(MongoStatement stmt) {
        MongoCollection<Document> mc = db().getCollection(stmt.getCollection());
        InsertOneResult result = mc.insertOne(stmt.getValue());
        if (returnGeneratedKeys && result.wasAcknowledged()) {
            BsonValue insertedId = result.getInsertedId();
            if (insertedId != null) {
                return DbResultDml.create(
                        Stream.of(
                                new MongoDbRow(
                                        codec.decode(
                                                new BsonDocumentReader(new BsonDocument("_id", insertedId)),
                                                decoderContext),
                                        context())),
                        1L);
            }
        }
        return DbResultDml.create(Stream.of(), 1L);
    }

    private Long executeUpdate(MongoStatement stmt) {
        MongoCollection<Document> mc = db().getCollection(stmt.getCollection());
        Document query = stmt.getQuery();
        UpdateResult updateResult = mc.updateMany(query, stmt.getValue());
        return updateResult.getModifiedCount();
    }

    private DbResultDml executeUpdateAsDbResultDml(MongoStatement stmt) {
        MongoCollection<Document> mc = db().getCollection(stmt.getCollection());
        Document query = stmt.getQuery();
        UpdateResult result = mc.updateMany(query, stmt.getValue());
        if (returnGeneratedKeys && result.wasAcknowledged()) {
            BsonValue upsertedId = result.getUpsertedId();
            if (upsertedId != null) {
                return DbResultDml.create(
                        Stream.of(
                                new MongoDbRow(
                                        codec.decode(
                                                new BsonDocumentReader(new BsonDocument("_id", upsertedId)),
                                                decoderContext),
                                        context())),
                        1L);
            }
        }
        return DbResultDml.create(Stream.of(), 1L);
    }

    private Long executeDelete(MongoStatement stmt) {
        MongoCollection<Document> mc = db().getCollection(stmt.getCollection());
        Document query = stmt.getQuery();
        DeleteResult deleteResult = mc.deleteMany(query);
        return deleteResult.getDeletedCount();
    }

    private DbResultDml executeDeleteAsDbResultDml(MongoStatement stmt) {
        return DbResultDml.create(Stream.of(), executeDelete(stmt));
    }

    private Long executeDml(MongoStatement stmt) {
        switch (stmt.getOperation()) {
        case INSERT: return executeInsert(stmt);
        case UPDATE: return executeUpdate(stmt);
        case DELETE: return executeDelete(stmt);
        case COMMAND:
        default: throw new UnsupportedOperationException(String.format(
                "Statement operation %s is not supported as DML",
                stmt.getOperation()));
        }
    }

    private DbResultDml executeDmlAsDbResultDml(MongoStatement stmt) {
        switch (stmt.getOperation()) {
        case INSERT: return executeInsertAsDbResultDml(stmt);
        case UPDATE: return executeUpdateAsDbResultDml(stmt);
        case DELETE: return executeDeleteAsDbResultDml(stmt);
        case COMMAND:
        default: throw new UnsupportedOperationException(String.format(
                "Statement operation %s is not supported as DML",
                stmt.getOperation()));
        }
    }

}
