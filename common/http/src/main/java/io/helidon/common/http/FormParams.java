/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
     * @deprecated use {@link FormParams#builder()} instead or register {@code io.helidon.media.common.FormParamsBodyReader}
     */
    @Deprecated(since = "2.0.2")
    static FormParams create(String paramAssignments, MediaType mediaType) {
        return FormParamsImpl.create(paramAssignments, mediaType);
    }

    /**
     * Creates a new {@link Builder} of {@code FormParams} instance.
     *
     * @return builder instance
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * Builder of a new {@link FormParams} instance.
     */
    class Builder implements FormBuilder<Builder, FormParams> {

        private final Map<String, List<String>> params = new LinkedHashMap<>();

        private Builder() {
        }

        @Override
        public FormParams build() {
            return new FormParamsImpl(this);
        }

        @Override
        public Builder add(String name, String... values) {
            Objects.requireNonNull(name);
            params.computeIfAbsent(name, k -> new ArrayList<>()).addAll(Arrays.asList(values));
            return this;
        }

        Map<String, List<String>> params() {
            return params;
        }

    }

}
