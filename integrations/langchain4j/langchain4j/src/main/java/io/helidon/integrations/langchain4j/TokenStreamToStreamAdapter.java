/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.integrations.langchain4j;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Stream;

import dev.langchain4j.service.TokenStream;
import dev.langchain4j.spi.services.TokenStreamAdapter;

/**
 * The TokenStreamToMultiAdapter class adapts a TokenStream to {@link java.util.stream.Stream},
 * allowing using {@link java.util.stream.Stream}
 * API on AI services with {@link dev.langchain4j.model.chat.StreamingChatModel}s.
 * <p>
 * Usage:
 * <pre>{@code
 * @Ai.Service
 * interface HelidonAssistant {
 *
 *   @SystemMessage("You are Frank, a helpful Helidon expert.")
 *   Stream<String> chat(@UserMessage String question);
 * }
 * }</pre>
 */
public class TokenStreamToStreamAdapter implements TokenStreamAdapter {
    private static final Class<?>[] SINGLE_STRING_ARRAY = new Class[] {String.class};

    /**
     * Constructs a new instance of {@code TokenStreamToStreamAdapter}.
     */
    public TokenStreamToStreamAdapter() {
    }

    @Override
    public boolean canAdaptTokenStreamTo(Type type) {
        return type instanceof ParameterizedType t
                && t.getRawType() == Stream.class
                && Arrays.equals(t.getActualTypeArguments(), SINGLE_STRING_ARRAY);
    }

    @Override
    public Object adapt(TokenStream tokenStream) {
        BlockingQueue<Object> queue = new LinkedBlockingQueue<>();
        tokenStream.onPartialResponse(e -> blockingNonNullPut(e, queue))
                .onCompleteResponse(ignored -> blockingNonNullPut(CompleteSignal.class, queue))
                .onError(t -> blockingNonNullPut(t, queue))
                .start();

        return Stream.generate(() -> {
                    Object o = null;
                    try {
                        o = queue.take();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    if (o instanceof String s) {
                        return s;
                    } else if (o == CompleteSignal.class) {
                        return null;
                    } else if (o instanceof Throwable t) {
                        throw new TokenStreamException(t);
                    } else {
                        throw new IllegalStateException("Unexpected object type: " + o);
                    }
                })
                .takeWhile(Objects::nonNull);
    }

    private static void blockingNonNullPut(Object o, BlockingQueue<Object> queue) {
        try {
            if (o != null) {
                queue.put(o);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private interface CompleteSignal {
    }
}
