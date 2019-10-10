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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.helidon.common.CollectionsHelper;

/**
 * Implementation of the {@link FormParams} interface.
 */
public class FormParamsImpl implements FormParams {

    /*
     * For form params represented in text/plain (uncommon), newlines appear between name=value
     * assignments. When urlencoded, ampersands separate the name=value assignments.
     */
    private static final Map<MediaType, Pattern> PATTERNS = CollectionsHelper.mapOf(
            MediaType.APPLICATION_FORM_URLENCODED, preparePattern("&"),
            MediaType.TEXT_PLAIN, preparePattern("\n"));

    private static Pattern preparePattern(String assignmentSeparator) {
        return Pattern.compile(String.format("([^=]+)=([^%1$s]+)%1$s?", assignmentSeparator));
    }

    private Map<String, List<String>> params;

    static FormParams create(String paramAssignments, MediaType mediaType) {
        final Map<String, List<String>> params = new HashMap<>();
        Matcher m = PATTERNS.get(mediaType).matcher(paramAssignments);
        while (m.find()) {
            final String key = m.group(1);
            final String value = m.group(2);
            List<String> values = params.compute(key, (k, v) -> {
                        if (v == null) {
                            v = new ArrayList<>();
                        }
                        v.add(value);
                        return v;
            });
        }
        return new FormParamsImpl(params);
    }

    private FormParamsImpl(Map<String, List<String>> params) {
        this.params = params;
    }

    @Override
    public Optional<String> first(String name) {
        return Optional.ofNullable(params.get(name)).filter(list -> !list.isEmpty()).map(list -> list.get(0));
    }

    @Override
    public List<String> all(String name) {
        List<String> result = params.get(name);
        return result == null ? Collections.emptyList() : Collections.unmodifiableList(result);
    }

    @Override
    public List<String> put(String key, String... values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<String> put(String key, Iterable<String> values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<String> putIfAbsent(String key, String... values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<String> putIfAbsent(String key, Iterable<String> values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<String> computeIfAbsent(String key, Function<String, Iterable<String>> values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<String> computeSingleIfAbsent(String key, Function<String, String> value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void putAll(Parameters parameters) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void add(String key, String... values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void add(String key, Iterable<String> values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void addAll(Parameters parameters) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<String> remove(String key) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Map<String, List<String>> toMap() {
        return Collections.unmodifiableMap(params);
    }
}
