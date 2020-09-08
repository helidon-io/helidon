/*
 * Copyright (c)  2020 Oracle and/or its affiliates.
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
 *
 */

package io.helidon.tests.integration.nativeimage.se1;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;

import javax.json.Json;
import javax.json.JsonPointer;
import javax.json.JsonString;
import javax.json.JsonValue;

import io.helidon.common.GenericType;
import io.helidon.common.http.DataChunk;
import io.helidon.common.reactive.Multi;
import io.helidon.common.reactive.Single;
import io.helidon.media.common.MessageBodyReaderContext;
import io.helidon.media.common.MessageBodyStreamReader;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.Service;

public class MockZipkinService implements Service {

    private static final Logger LOGGER = Logger.getLogger(MockZipkinService.class.getName());

    final static JsonPointer TAGS_POINTER = Json.createPointer("/tags");
    final static JsonPointer COMPONENT_POINTER = Json.createPointer("/tags/component");

    private final Set<String> filteredComponents;
    private final AtomicReference<CompletableFuture<JsonValue>> next = new AtomicReference<>(new CompletableFuture<>());

    /**
     * Create mock of the Zipkin listening on /api/v2/spans.
     *
     * @param filteredComponents listen only for traces with component tag having one of specified values
     */
    MockZipkinService(Set<String> filteredComponents) {
        this.filteredComponents = filteredComponents;
    }

    @Override
    public void update(final Routing.Rules rules) {
        rules.post("/api/v2/spans", this::mockZipkin);
    }

    /**
     * Return completion being completed when next trace call arrives.
     *
     * @return completion being completed when next trace call arrives
     */
    CompletionStage<JsonValue> next() {
        return next.get();
    }

    private void mockZipkin(final ServerRequest request, final ServerResponse response) {
        request.queryParams().all("serviceName").forEach(s -> System.out.println(">>>" + s));
        request.content()
                .registerReader(new MessageBodyStreamReader<JsonValue>() {
                    @Override
                    public PredicateResult accept(final GenericType<?> type, final MessageBodyReaderContext context) {
                        return PredicateResult.COMPATIBLE;
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public <U extends JsonValue> Flow.Publisher<U> read(final Flow.Publisher<DataChunk> publisher, final GenericType<U> type, final MessageBodyReaderContext context) {
                        return (Flow.Publisher<U>) Multi.create(publisher)
                                .map(d -> ByteBuffer.wrap(d.bytes()))
                                .reduce((buf, buf2) ->
                                        ByteBuffer.allocate(buf.capacity() + buf2.capacity())
                                                .put(buf.array())
                                                .put(buf2.array()))
                                .flatMap(b -> {
                                    try (ByteArrayInputStream bais = new ByteArrayInputStream(b.array());
                                         GZIPInputStream gzipInputStream = new GZIPInputStream(bais)) {
                                        return Single.just(Json.createReader(new StringReader(new String(gzipInputStream.readAllBytes())))
                                                .readArray());
                                    } catch (EOFException e) {
                                        //ignore
                                        return Multi.empty();
                                    } catch (IOException e) {
                                        throw new RuntimeException(e);
                                    }
                                })
                                .flatMap(a -> Multi.create(a.stream()));
                    }
                })
                .asStream(JsonValue.class)
                .map(JsonValue::asJsonObject)
                .filter(json ->
                        TAGS_POINTER.containsValue(json)
                                && COMPONENT_POINTER.containsValue(json)
                                && filteredComponents.stream()
                                .anyMatch(s -> s.equals(((JsonString) COMPONENT_POINTER.getValue(json)).getString()))
                )
                .onError(Throwable::printStackTrace)
                .onError(t -> response.status(500).send(t))
                .onComplete(response::send)
                .peek(json -> LOGGER.info(json.toString()))
                .forEach(e -> next.getAndSet(new CompletableFuture<>()).complete(e));
    }
}
