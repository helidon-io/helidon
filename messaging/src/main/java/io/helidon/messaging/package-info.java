/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

/**
 * Helidon-native declarative messaging API, runtime, and connector SPI.
 * <p>
 * Message delivery uses a synchronous, at-least-once settlement contract. An emission returns only after every
 * required output completes successfully. Outputs are invoked sequentially and delivery fails immediately when an
 * output throws. Outputs completed before that failure are not rolled back, so retrying a failed emission can deliver
 * the same message to those outputs again. Each logical channel executes at most one delivery at a time; different
 * channels may execute concurrently. Applications and connectors must therefore tolerate duplicate delivery.
 */
package io.helidon.messaging;
