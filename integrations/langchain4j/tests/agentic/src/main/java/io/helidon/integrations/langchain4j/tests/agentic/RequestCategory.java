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

package io.helidon.integrations.langchain4j.tests.agentic;

/**
 * Represents the classification of a user request used to select the appropriate
 * expert agent. The values correspond to the activation conditions in
 * {@code ExpertsAgent}. If a request does not fit any known domain, {@link #UNKNOWN}
 * is used.
 */
public enum RequestCategory {
    /**
     * Legal request category.
     */
    LEGAL,
    /**
     * Medical request category.
     */
    MEDICAL,
    /**
     * Technical request category.
     */
    TECHNICAL,
    /**
     * Unknown request category.
     */
    UNKNOWN
}
