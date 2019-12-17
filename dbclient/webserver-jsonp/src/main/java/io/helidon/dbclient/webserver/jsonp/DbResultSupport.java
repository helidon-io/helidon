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
package io.helidon.dbclient.webserver.jsonp;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
import io.helidon.dbclient.DbRows;
import io.helidon.media.common.ContentWriters;
import io.helidon.webserver.Handler;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.Service;

/**
 * Support to write {@link io.helidon.dbclient.DbRows} directly to webserver.
 * This result support creates an array of json objects and writes them to the response entity.
 */
public final class DbResultSupport implements Service, Handler {

    /** Local logger instance. */
    private static final Logger LOG = Logger.getLogger(DbResultSupport.class.getName());

    private static final JsonBuilderFactory JSON = Json.createBuilderFactory(Collections.emptyMap());
    private static final JsonWriterFactory WRITER_FACTORY = Json.createWriterFactory(Collections.emptyMap());
    private static final byte[] EMPTY_JSON_BYTES = "[]".getBytes(StandardCharsets.UTF_8);
    private static final byte[] ARRAY_JSON_END_BYTES = "]".getBytes(StandardCharsets.UTF_8);
    private static final byte[] ARRAY_JSON_BEGIN_BYTES = "[".getBytes(StandardCharsets.UTF_8);
    private static final byte[] COMMA_BYTES = ",".getBytes(StandardCharsets.UTF_8);

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
        serverResponse.registerWriter(DbRows.class, DbResultSupport::writer);
        serverResponse.registerWriter(DbResult.class, DbResultWriter::new);
        serverRequest.next();
    }

    @Override
    public void update(Routing.Rules rules) {
        rules.any(this);
    }

    private static final class DbResultWriter implements Flow.Publisher<DataChunk> {
        private final CompletableFuture<Long> dml = new CompletableFuture<>();
        private final CompletableFuture<DbRows<DbRow>> query = new CompletableFuture<>();

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
                    objectWriter(JSON.createObjectBuilder().add("count", count).build());
                }
            });
        }

        private static Flow.Publisher<DataChunk> objectWriter(JsonObject json) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (JsonWriter writer = WRITER_FACTORY.createWriter(baos)) {
                writer.write(json);
            }
            return ContentWriters.byteArrayWriter(false)
                    .apply(baos.toByteArray());
        }

    }

    private static final class DbRowSubscription implements Flow.Subscription {

        private final Flow.Subscription subscription;

        private DbRowSubscription(final Flow.Subscription subscription) {
            this.subscription = subscription;
        }

        @Override
        public void request(long l) {
            subscription.request(l);
        }

        @Override
        public void cancel() {
            subscription.cancel();
        }

    }

    private static final class DbRowSubscriber implements Flow.Subscriber<DbRow> {

        private volatile boolean first = true;
        private final Flow.Subscriber<? super DataChunk> subscriber;

        private DbRowSubscriber(final Flow.Subscriber<? super DataChunk> subscriber) {
            this.subscriber = subscriber;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            Flow.Subscription mySubscription = new DbRowSubscription(subscription);
            subscriber.onSubscribe(mySubscription);
        }

        @Override
        public void onNext(DbRow dbRow) {
            LOG.info(String.format("onNext: %s", dbRow.toString()));
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            if (first) {
                try {
                    baos.write(ARRAY_JSON_BEGIN_BYTES);
                } catch (IOException ignored) {
                }
                first = false;
            } else {
                try {
                    baos.write(COMMA_BYTES);
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
                subscriber.onNext(DataChunk.create(EMPTY_JSON_BYTES));
            } else {
                subscriber.onNext(DataChunk.create(ARRAY_JSON_END_BYTES));
            }
            subscriber.onComplete();
        }

    }

    private static final class DataChunkPublisher implements Flow.Publisher<DataChunk> {

        private final DbRows<DbRow> dbRows;

        private DataChunkPublisher(final DbRows<DbRow> dbRows) {
            this.dbRows = dbRows;
        }

        @Override
        public void subscribe(Flow.Subscriber<? super DataChunk> subscriber) {
            dbRows.publisher().subscribe(new DbRowSubscriber(subscriber));
        }

    }

    // server send streaming
    // json streaming & data type
    private static Flow.Publisher<DataChunk> writer(DbRows<DbRow> dbRows) {
        return new DataChunkPublisher(dbRows);
    }

}
