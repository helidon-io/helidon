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

import java.util.List;

import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.Service;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;

@Service.Singleton
@Weight(Weighted.DEFAULT_WEIGHT - 10)
class TextSegmentInMemoryEmbeddingStoreFactory implements Service.ServicesFactory<EmbeddingStore<TextSegment>> {
    TextSegmentInMemoryEmbeddingStoreFactory() {
    }

    @Override
    public List<Service.QualifiedInstance<EmbeddingStore<TextSegment>>> services() {
        var store = new InMemoryEmbeddingStore<TextSegment>();

        return List.of(Service.QualifiedInstance.create(store),
                       Service.QualifiedInstance.create(store, Qualifier.createNamed("in-memory")));

    }
}
