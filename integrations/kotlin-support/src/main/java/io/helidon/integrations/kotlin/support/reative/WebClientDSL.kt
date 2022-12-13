/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.integrations.kotlin.support.reative

import io.helidon.reactive.media.jsonp.JsonpSupport
import io.helidon.reactive.webclient.WebClient

/**
 * DSL for the builder for WebClient.
 */
fun webClient(block: WebClient.Builder.() -> Unit = {}): WebClient = WebClient.builder().apply(block).build()

fun jsonpSupport(block: JsonpSupport.Builder.() ->
                                        Unit = {}): JsonpSupport = JsonpSupport.builder().apply(block).build();