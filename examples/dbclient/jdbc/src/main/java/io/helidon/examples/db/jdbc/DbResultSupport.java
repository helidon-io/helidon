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
package io.helidon.examples.db.jdbc;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import javax.json.Json;
import javax.json.JsonBuilderFactory;
import javax.json.JsonObject;
import javax.json.JsonWriter;
import javax.json.JsonWriterFactory;

import io.helidon.common.http.DataChunk;
import io.helidon.common.reactive.Flow;
import io.helidon.dbclient.DbResult;
import io.helidon.dbclient.DbRow;
import io.helidon.dbclient.DbRowResult;
import io.helidon.media.common.ContentWriters;
import io.helidon.webserver.Handler;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.Service;

/**
 * Support to write {@link io.helidon.dbclient.DbRowResult} directly to webserver.
 * This result support creates an array of json objects and writes them to the response entity.
 */
public final class DbResultSupport implements Service, Handler {

    /** Local logger instance. */
    private static final Logger LOG = Logger.getLogger(DbResultSupport.class.getName());

    private static final JsonBuilderFactory JSON = Json.createBuilderFactory(Collections.emptyMap());
    private static final JsonWriterFactory WRITER_FACTORY = Json.createWriterFactory(Collections.emptyMap());

    private DbResultSupport() {
    }

    /**
     * Create a new instance to register with a webserver.
     * @return {@link io.helidon.webserver.WebServer} {@link io.helidon.webserver.Service}
     */
    public static DbResultSupport create() {
        return new DbResultSupport();
    }

    @Override
    public void accept(ServerRequest serverRequest, ServerResponse serverResponse) {
        serverResponse.registerWriter(DbRowResult.class, DbResultSupport::writer);
        serverResponse.registerWriter(DbResult.class, DbResultWriter::new);
        serverRequest.next();
    }

    @Override
    public void update(Routing.Rules rules) {
        rules.any(this);
    }

    private class DbResultWriter implements Flow.Publisher<DataChunk> {
        private final CompletableFuture<Long> dml = new CompletableFuture<>();
        private final CompletableFuture<DbRowResult<DbRow>> query = new CompletableFuture<>();

        private DbResultWriter(DbResult dbResult) {
            dbResult
                    .whenDml(count -> {
                        dml.complete(count);
                        query.complete(null);
                    })
                    .whenRs(rs -> {
                        query.complete(rs);
                        dml.complete(null);
                    });
        }

        @Override
        public void subscribe(Flow.Subscriber<? super DataChunk> subscriber) {
            query.thenAccept(rs -> {
                if (null != rs) {
                    writer(rs).subscribe(subscriber);
                }
            });
            dml.thenAccept(count -> {
                if (null != count) {
                    writer(JSON.createObjectBuilder().add("count", count).build());
                }
            });
        }
    }

    // server send streaming
    // json streaming & data type
    private static Flow.Publisher<DataChunk> writer(DbRowResult<DbRow> dbRowResult) {
        return new Flow.Publisher<DataChunk>() {
            @Override
            public void subscribe(Flow.Subscriber<? super DataChunk> subscriber) {
                dbRowResult.publisher().subscribe(new Flow.Subscriber<DbRow>() {
                    private Flow.Subscription subscription;
                    private volatile boolean first = true;

                    @Override
                    public void onSubscribe(Flow.Subscription subscription) {
                        this.subscription = subscription;

                        Flow.Subscription mySubscription = new Flow.Subscription() {
                            @Override
                            public void request(long l) {
                                subscription.request(l);
                            }

                            @Override
                            public void cancel() {
                                subscription.cancel();
                            }
                        };

                        subscriber.onSubscribe(mySubscription);
                    }

                    @Override
                    public void onNext(DbRow dbRow) {
                        LOG.info(String.format("onNext: %s", dbRow.toString()));
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        if (first) {
                            try {
                                baos.write("[".getBytes());
                            } catch (IOException ignored) {
                            }
                            first = false;
                        } else {
                            try {
                                baos.write(",".getBytes());
                            } catch (IOException ignored) {
                            }
                        }
                        JsonWriter writer = WRITER_FACTORY.createWriter(baos);
                        writer.write(dbRow.as(JsonObject.class));
                        writer.close();
                        subscriber.onNext(DataChunk.create(baos.toByteArray()));
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        subscriber.onError(throwable);
                    }

                    @Override
                    public void onComplete() {
                        LOG.info("onComplete");

                        if (first) {
                            subscriber.onNext(DataChunk.create("[]".getBytes()));
                        } else {
                            subscriber.onNext(DataChunk.create("]".getBytes()));
                        }
                        subscriber.onComplete();
                    }
                });
            }
        };
    }

    private static Flow.Publisher<DataChunk> writer(JsonObject json) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        JsonWriter writer = WRITER_FACTORY.createWriter(baos);
        writer.write(json);
        writer.close();
        return ContentWriters.byteArrayWriter(false)
                .apply(baos.toByteArray());
    }
}
