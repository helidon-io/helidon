/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates.
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
package io.helidon.media.common;

import java.util.Collection;
import java.util.Collections;

/**
 * Service used to register readers and writers to the respective context.
 * <p>
 * MediaSupport instances can be used with WebServer and WebClient to register readers and writer.
 * Each of these have method addMediaSupport(), which will add corresponding support.
 * </p><br>
 * WebServer example usage:
 * <pre><code>
 * WebServer.builder()
 *          .host("localhost")
 *          .addMediaSupport(JsonbSupport.create())
 *          .build();
 * </code></pre>
 * WebClient example usage:
 * <pre><code>
 * WebClient.builder()
 *          .addMediaSupport(JacksonSupport.create())
 *          .build();
 * </code></pre>
 * If you need to register MediaSupport on the request or response, you will need to register them to
 * the corresponding context.
 * <br>
 * Example request reader registration:
 * <pre><code>
 * Routing.builder()
 *        .get("/foo", (res, req) -&gt; {
 *            MessageBodyReadableContent content = req.content();
 *            content.registerReader(JsonbSupport.create())
 *            content.as(String.class)
 *                   .thenAccept(System.out::print);
 *        })
 * </code></pre>
 * Example response writer registration:
 * <pre><code>
 * Routing.builder()
 *        .get("/foo", (res, req) -&gt; {
 *           MessageBodyWriterContext writerContext = res.writerContext();
 *           writerContext.registerWriter(JsonbSupport.create())
 *           res.send("Example entity");
 *        })
 * </code></pre>
 */
public interface MediaSupport {

    /**
     * Method used to register readers and writers.
     *
     * @param readerContext reader context
     * @param writerContext writer context
     */
    default void register(MessageBodyReaderContext readerContext, MessageBodyWriterContext writerContext) {
        readers().forEach(readerContext::registerReader);
        writers().forEach(writerContext::registerWriter);
        streamReaders().forEach(readerContext::registerReader);
        streamWriters().forEach(writerContext::registerWriter);
    }

    /**
     * Returns the collection of the readers which should be registered.
     *
     * @return readers
     */
    default Collection<MessageBodyReader<?>> readers() {
        return Collections.emptyList();
    }

    /**
     * Returns the collection of the writers which should be registered.
     *
     * @return writers
     */
    default Collection<MessageBodyWriter<?>> writers() {
        return Collections.emptyList();
    }

    /**
     * Returns the collection of the stream readers which should be registered.
     *
     * @return stream readers
     */
    default Collection<MessageBodyStreamReader<?>> streamReaders() {
        return Collections.emptyList();
    }

    /**
     * Returns the collection of the stream writers which should be registered.
     *
     * @return stream writers
     */
    default Collection<MessageBodyStreamWriter<?>> streamWriters() {
        return Collections.emptyList();
    }

}
