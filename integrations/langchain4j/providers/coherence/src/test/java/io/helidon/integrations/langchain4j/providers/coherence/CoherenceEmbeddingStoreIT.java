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

package io.helidon.integrations.langchain4j.providers.coherence;

import java.util.List;

import io.helidon.service.registry.Service;
import io.helidon.testing.junit5.Testing;

import com.tangosol.net.Coherence;
import com.tangosol.net.CoherenceConfiguration;
import com.tangosol.net.SessionConfiguration;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;

@Testing.Test
public class CoherenceEmbeddingStoreIT {

    private static final List<Float> QUERY_EMBEDDING = List.of(887788f);
    private static final List<Float> TEST_EMBEDDING = List.of(445566f, 887799f, QUERY_EMBEDDING.getFirst(), 887733f);
    private static Coherence COHERENCE_SESSION;

    @BeforeAll
    static void beforeAll() throws InterruptedException {
        System.setProperty("coherence.wka", "127.0.0.1");
        COHERENCE_SESSION = Coherence.create(CoherenceConfiguration.builder()
                                                     .named("coherence_embedding_store_it")
                                                     .withSession(SessionConfiguration.builder()
                                                                          .named("session")
                                                                          .build())
                                                     .build())
                .startAndWait();
    }

    @AfterAll
    static void afterAll() {
        if (COHERENCE_SESSION != null) {
            COHERENCE_SESSION.close();
        }
    }

    @Service.Singleton
    static class StoreWrapper {
        private final EmbeddingStore<TextSegment> namedStore;
        private final EmbeddingStore<?> defaultStore;

        @Service.Inject
        StoreWrapper(
                @Service.Named("coherence") EmbeddingStore<TextSegment> namedStore,
                EmbeddingStore<?> defaultStore
        ) {
            this.namedStore = namedStore;
            this.defaultStore = defaultStore;
        }
    }

    @Test
    void embeddingTest(StoreWrapper storeWrapper) throws InterruptedException {
            var embeddingStore = storeWrapper.namedStore;
            embeddingStore.add(Embedding.from(TEST_EMBEDDING));

            // Coherence store should have higher weight than default in-memory store.
            assertThat(storeWrapper.defaultStore, equalTo(embeddingStore));

            assertStore(embeddingStore);
            assertStore(storeWrapper.defaultStore);
    }

    private void assertStore(EmbeddingStore<?> embeddingStore) {
        var result = embeddingStore.search(EmbeddingSearchRequest.builder()
                                                   .queryEmbedding(Embedding.from(QUERY_EMBEDDING))
                                                   .build());

        assertThat(result.matches().getFirst().embedding().vectorAsList(), contains(TEST_EMBEDDING.toArray(Float[]::new)));
    }

}
