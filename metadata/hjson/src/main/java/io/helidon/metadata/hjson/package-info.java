/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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
 * Tiny JSON parser and writer. This is intended for annotation processors, code generators,
 * and readers of the generated files, such as Config Metadata, Service Registry etc.
 * <p>
 * To write JSON, start with either of the following types:
 * <ul>
 *     <li>{@link io.helidon.metadata.hjson.JArray}</li>
 *     <li>{@link io.helidon.metadata.hjson.JObject}</li>
 * </ul>
 * To read JSON, start with {@link io.helidon.metadata.hjson.JValue#read(java.io.InputStream)}.
 * <p>
 * Supported features, non-features:
 * <ul>
 *     <li>Only UTF-8 is supported</li>
 *     <li>Arrays</li>
 *     <li>Objects</li>
 *     <li>Nesting of objects and arrays</li>
 *     <li>String, BigDecimal, boolean, null</li>
 *     <li>No pretty print (always writes as small as possible)</li>
 *     <li>Keeps order of insertion on write</li>
 *     <li>Keeps order of original JSON on read</li>
 *     <li>Maximal value size is 64000 bytes</li>
 *     <li>Maximal JSON size is unlimited - NEVER USE WITH OVER-THE-NETWORK PROVIDED JSON</li>
 * </ul>
 *
 * Should you use this library?
 * No, unless:
 * <ul>
 *     <li>You are developing writing or parsing of metadata in Helidon codegen or runtime for Helidon features,
 *     the metadata must be produced by Helidon as well (NEVER USE ON OVER-THE-NETWORK PROVIDED JSON)</li>
 *     <li>You are a brave user who wants to do something similar in their library</li>
 * </ul>
 *
 * <b>WARNING:</b> The JSON object is always fully read into memory
 */
package io.helidon.metadata.hjson;
