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

import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Implementation of the {@link FormParams} interface.
 */
class FormParamsImpl extends ReadOnlyParameters implements FormParams {

    /*
     * For form params represented in text/plain (uncommon), newlines appear between name=value
     * assignments. When urlencoded, ampersands separate the name=value assignments.
     */
    private static final Map<MediaType, Pattern> PATTERNS = Map.of(
            MediaType.APPLICATION_FORM_URLENCODED, preparePattern("&"),
            MediaType.TEXT_PLAIN, preparePattern("\n"));

    private FormParamsImpl(Map<String, List<String>> params) {
        super("FormParam", params);
    }

    FormParamsImpl(FormParams.Builder builder) {
        super(builder.params());
    }

    private static Pattern preparePattern(String assignmentSeparator) {
        return Pattern.compile(String.format("([^=]+)=([^%1$s]+)%1$s?", assignmentSeparator));
    }

    static FormParams create(String paramAssignments, MediaType mediaType) {
        Charset charset = mediaType.charset().map(Charset::forName).orElse(StandardCharsets.UTF_8);
        Function<String, String> decoder = decoder(mediaType, charset);
        final Map<String, List<String>> params = new HashMap<>();
        Matcher m = PATTERNS.get(mediaType).matcher(paramAssignments);
        while (m.find()) {
            final String key = decoder.apply(m.group(1));
            final String value = m.group(2);
            if (value == null) {
                params.computeIfAbsent(key, k -> new ArrayList<>());
            } else {
                params.computeIfAbsent(key, k -> new ArrayList<>()).add(decoder.apply(value));
            }
        }
        return new FormParamsImpl(params);
    }

    private static Function<String, String> decoder(MediaType mediaType, Charset charset) {
        if (mediaType == MediaType.TEXT_PLAIN) {
            return (s) -> s;
        } else {
            return (s) -> URLDecoder.decode(s, charset);
        }
    }

}
