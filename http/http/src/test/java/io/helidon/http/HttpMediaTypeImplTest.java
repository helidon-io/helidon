/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.http;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.helidon.common.media.type.MediaType;
import io.helidon.common.media.type.MediaTypes;

import org.hamcrest.core.IsCollectionContaining;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;

class HttpMediaTypeImplTest {
    @Test
    void testCorrectOrder() {
        MediaType textWildcard = MediaTypes.create("text/*");
        MediaType appWildcard = MediaTypes.create("application/*");

        List<HttpMediaType> list = new ArrayList<>();
        list.add(HttpMediaType.builder().mediaType(textWildcard).q(0.2).build());
        list.add(HttpMediaType.builder().mediaType(MediaTypes.APPLICATION_ATOM_XML).q(0.1).build());
        list.add(HttpMediaType.builder().mediaType(MediaTypes.WILDCARD).q(0.2).build());
        list.add(HttpMediaType.builder().mediaType(MediaTypes.APPLICATION_JSON).q(0.2).build());
        list.add(HttpMediaType.builder().mediaType(MediaTypes.APPLICATION_JAVASCRIPT).q(0.5).build());
        list.add(HttpMediaType.builder().mediaType(MediaTypes.APPLICATION_YAML).q(1).build());
        list.add(HttpMediaType.builder().mediaType(appWildcard).build());

        Collections.sort(list);
        List<MediaType> mediaTypes = list.stream()
                .map(HttpMediaType::mediaType)
                .toList();
        /*
        order MUST be as follows:
        app/yaml
        app/* (wildcard is less important than explicit type, but q is 1, so above javascript)
        app/javascript
        app/json (explicit)
        text/* (explicit type)
        * /* (full wildcard)
        app/xml (last, lowest q)
         */

        assertThat(mediaTypes, IsCollectionContaining.hasItems(MediaTypes.APPLICATION_YAML,
                                                               appWildcard,
                                                               MediaTypes.APPLICATION_JAVASCRIPT,
                                                               MediaTypes.APPLICATION_JSON,
                                                               textWildcard,
                                                               MediaTypes.WILDCARD,
                                                               MediaTypes.APPLICATION_ATOM_XML));
    }
}