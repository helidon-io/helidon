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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.stream.Stream;

import io.helidon.testing.junit5.Testing;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.service.AiServices;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;

@Testing.Test
class TokenStreamToStreamAdapterTest {

    private static final List<String> TEST_STREAM = List.of("TEST 1", "TEST 2", "TEST 3", "TEST 4");
    private static final ExecutorService THREAD_POOL = Executors.newVirtualThreadPerTaskExecutor();
    private static final String ERROR_SIGNAL = "ERROR_SIGNAL";
    private static final int TIMEOUT_SEC = 5;

    @AfterAll
    static void afterAll() throws InterruptedException {
        THREAD_POOL.shutdown();
        if (!THREAD_POOL.awaitTermination(TIMEOUT_SEC, SECONDS)) {
            THREAD_POOL.shutdownNow();
        }
    }

    @Test
    void stream() throws InterruptedException {
        Semaphore emitSemaphore = new Semaphore(0);
        Semaphore recSemaphore = new Semaphore(0);

        var assistant = AiServices.create(HelidonAssistant.class, mockedStremingChatModel(TEST_STREAM, emitSemaphore));
        List<String> results = new CopyOnWriteArrayList<>();

        CompletableFuture.runAsync(() -> {
            assistant.chat("test message").forEach(e -> {
                results.add(e);
                recSemaphore.release();
            });
        });

        for (int i = 0; i < TEST_STREAM.size(); i++) {
            emitSemaphore.release();
            assertTrue(recSemaphore.tryAcquire(TIMEOUT_SEC, SECONDS));
            assertThat(results, contains(TEST_STREAM.subList(0, i + 1).toArray(String[]::new)));
        }
    }

    @Test
    void error() throws InterruptedException {
        Semaphore emitSemaphore = new Semaphore(0);
        Semaphore recSemaphore = new Semaphore(0);

        var data = new ArrayList<>(TEST_STREAM);
        data.add(ERROR_SIGNAL);

        var assistant = AiServices.create(HelidonAssistant.class, mockedStremingChatModel(data, emitSemaphore));
        List<String> results = new CopyOnWriteArrayList<>();

        var consumer = CompletableFuture.runAsync(() -> {
            assistant.chat("test message").forEach(e -> {
                results.add(e);
                recSemaphore.release();
            });
        });

        for (int i = 0; i < TEST_STREAM.size() + 1; i++) {
            emitSemaphore.release();
            if (i < TEST_STREAM.size()) {
                assertTrue(recSemaphore.tryAcquire(TIMEOUT_SEC, SECONDS));
                assertThat(results, contains(TEST_STREAM.subList(0, i + 1).toArray(String[]::new)));
            }
        }
        var exception = assertThrows(ExecutionException.class, () -> {
            try {
                consumer.get(TIMEOUT_SEC, SECONDS);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
        assertThat(exception.getCause().getCause().getMessage(), is(ERROR_SIGNAL));
    }

    interface HelidonAssistant {
        Stream<String> chat(String question);
    }

    private static StreamingChatModel mockedStremingChatModel(List<String> data, Semaphore emitSemaphore) {
        var streamingChatModel = spy(StreamingChatModel.class);
        Mockito.doAnswer(i -> {
                    THREAD_POOL.submit(() -> {
                        var responseHandler = (StreamingChatResponseHandler) i.getArgument(1);
                        for (var m : data) {
                            try {
                                assertTrue(emitSemaphore.tryAcquire(TIMEOUT_SEC, SECONDS));
                                if (m.equals(ERROR_SIGNAL)) {
                                    responseHandler.onError(new RuntimeException(ERROR_SIGNAL));
                                    return;
                                }
                            } catch (InterruptedException e) {
                                throw new RuntimeException(e);
                            }
                            responseHandler.onPartialResponse(m);
                        }
                        responseHandler.onCompleteResponse(ChatResponse.builder()
                                                                   .aiMessage(AiMessage.builder().build())
                                                                   .build());
                    });
                    return Void.TYPE;
                })
                .when(streamingChatModel).doChat(any(), any());
        return streamingChatModel;
    }
}