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

package io.helidon.integrations.langchain4j.providers.cohere;

import io.helidon.common.Weighted;
import io.helidon.integrations.langchain4j.Ai;
import io.helidon.service.registry.Qualifier;

final class Cohere {
    static final String COHERE = "cohere";
    static final String SCORING_MODEL = COHERE + "." + Ai.SCORING_MODEL_NAME;
    static final Qualifier COHERE_QUALIFIER = Qualifier.createNamed(COHERE);
    static final double WEIGHT = Weighted.DEFAULT_WEIGHT - 20;

    private Cohere() {
    }
}
