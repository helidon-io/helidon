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

package io.helidon.declarative.codegen.faulttolerance;

class InterceptorWeights {
    static final double WEIGHT_RETRY = 10;
    static final double WEIGHT_BULKHEAD = 20;
    static final double WEIGHT_CIRCUIT_BREAKER = 30;
    static final double WEIGHT_TIMEOUT = 40;
    static final double WEIGHT_ASYNC = 50;
    static final double WEIGHT_FALLBACK = 60;

    private InterceptorWeights() {
    }
}
