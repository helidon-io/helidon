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

import java.util.Optional;

import com.oracle.coherence.ai.DocumentChunk;
import com.oracle.coherence.ai.VectorIndexExtractor;
import com.oracle.coherence.ai.hnsw.HnswIndex;
import com.tangosol.util.ValueExtractor;
import dev.langchain4j.model.embedding.EmbeddingModel;

/**
 * Utility methods for special Coherence model configs.
 */
final class CoherenceFactoryMethods {
    private static final System.Logger LOGGER = System.getLogger(CoherenceLc4jProvider.class.getName());

    private CoherenceFactoryMethods() {
    }

    /**
     * Creates and returns an {@link com.oracle.coherence.ai.VectorIndexExtractor} if `hnsw` index is configured.
     *
     * @param index the configured index name to use
     * @param dimension the configured  number of dimensions in the embeddings
     * @return an instance of {@link com.oracle.coherence.ai.VectorIndexExtractor} with dimensions
     */
    static VectorIndexExtractor<DocumentChunk, float[]> createVectorIndexExtractor(Optional<String> index, Optional<Integer> dimension, Optional<EmbeddingModel> embeddingModel) {
        VectorIndexExtractor<DocumentChunk, float[]> extractor = null;
        if (index.isPresent()) {
            if ("hnsw".equalsIgnoreCase(index.get())) {
                Integer modelDimension = embeddingModel.isPresent() ? (Integer) embeddingModel.get().dimension() : (dimension.orElse(null));
                if (modelDimension != null) {
                    extractor = new HnswIndex<>(ValueExtractor.of(DocumentChunk::vector), modelDimension);
                } else {
                    LOGGER.log(System.Logger.Level.WARNING,
                               "Cannot create embedding hnsw store index - No dimension name has been specified for the hnsw index.");
                }
            }
        }
        return extractor;
    }
}
