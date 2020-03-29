/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.common.http;

/**
 * Provides access to any form parameters present in the request entity.
 */
public interface FormParams extends Parameters {

    /**
     * Creates a new {@code FormParams} instance using the provided assignment string and media
     * type.
     *
     * @param paramAssignments String containing the parameter assignments, formatted according
     *                         to the media type specified ({@literal &} separator for
     *                         URL-encoded, NL for text/plain)
     * @param mediaType MediaType for which the parameter conversion is occurring
     * @return the new {@code FormParams} instance
     */
    static FormParams create(String paramAssignments, MediaType mediaType) {
        return FormParamsImpl.create(paramAssignments, mediaType);
    }
}
