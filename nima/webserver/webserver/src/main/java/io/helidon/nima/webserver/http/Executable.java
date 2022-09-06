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

package io.helidon.nima.webserver.http;

/**
 * A runnable that can throw a checked exception.
 * This is to allow users to throw exception from their routes (and these will either end in an
 * {@link io.helidon.common.http.InternalServerException}, or will be handled by exception handler.
 */
interface Executable {
    /**
     * Execute with a possible checked exception.
     *
     * @throws Exception any exception
     */
    void execute() throws Exception;
}
