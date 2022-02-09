/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
 * Helidon implementation of MicroProfile Long Running Actions.
 *
 * Allows defining transactional context for Jax-Rs resources with {@link org.eclipse.microprofile.lra.annotation.ws.rs.LRA}
 * annotation and compensation actions with {@link org.eclipse.microprofile.lra.annotation.Compensate} and
 * {@link org.eclipse.microprofile.lra.annotation.Complete}.
 *
 * @see org.eclipse.microprofile.lra
 */
package io.helidon.microprofile.lra;
