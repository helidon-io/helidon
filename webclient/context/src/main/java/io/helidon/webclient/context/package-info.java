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

/**
 * Propagation of context values across network using HTTP Headers.
 * <p>
 * The propagation requires context values of type {@link java.lang.String} with a classifier
 * (see {@link io.helidon.common.context.Context#register(java.lang.Object, java.lang.Object)} and
 * {@link io.helidon.common.context.Context#get(java.lang.Object, java.lang.Class)}).
 * <p>
 * Propagation can be configure to use the classifier as the header value, or map classifier to a header.
 */
package io.helidon.webclient.context.propagation;
