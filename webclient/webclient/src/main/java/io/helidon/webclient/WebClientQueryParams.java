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

package io.helidon.webclient;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import io.helidon.common.http.HashParameters;
import io.helidon.common.http.Parameters;

class WebClientQueryParams implements Parameters {

    private final Parameters rawParams;
    private final Parameters encodedParams;

    private boolean skipEncoding = false;

    WebClientQueryParams() {
        rawParams = HashParameters.create();
        encodedParams = HashParameters.create();
    }

    @Override
    public Optional<String> first(String name) {
        String key = name;
        if (!skipEncoding) {
            key = encode(name);
        }
        return pickCorrectParameters().first(key);
    }

    @Override
    public List<String> all(String name) {
        String key = name;
        if (!skipEncoding) {
            key = encode(name);
        }
        return pickCorrectParameters().all(key);
    }

    @Override
    public List<String> put(String key, String... values) {
        List<String> toEncode = values == null ? null : Arrays.asList(values);
        encodedParams.put(encode(key), encodeIterable(toEncode));
        return rawParams.put(key, values);
    }

    @Override
    public List<String> put(String key, Iterable<String> values) {
        encodedParams.put(encode(key), encodeIterable(values));
        return rawParams.put(key, values);
    }

    @Override
    public List<String> putIfAbsent(String key, String... values) {
        List<String> toEncode = values == null ? null : Arrays.asList(values);
        encodedParams.putIfAbsent(encode(key), encodeIterable(toEncode));
        return rawParams.putIfAbsent(key, values);
    }

    @Override
    public List<String> putIfAbsent(String key, Iterable<String> values) {
        encodedParams.putIfAbsent(encode(key), encodeIterable(values));
        return rawParams.putIfAbsent(key, values);
    }

    @Override
    public List<String> computeIfAbsent(String key,
                                        Function<String, Iterable<String>> values) {
        encodedParams.computeIfAbsent(encode(key), k -> encodeIterable(values.apply(key)));
        return rawParams.computeIfAbsent(key, values);
    }

    @Override
    public List<String> computeSingleIfAbsent(String key,
                                              Function<String, String> value) {
        encodedParams.computeSingleIfAbsent(encode(key), k -> encode(value.apply(k)));
        return rawParams.computeSingleIfAbsent(key, value);
    }

    @Override
    public WebClientQueryParams putAll(Parameters parameters) {
        if (parameters == null) {
            return this;
        }
        rawParams.putAll(parameters);
        parameters.toMap().forEach((key, value) -> encodedParams.put(encode(key), encodeIterable(value)));
        return this;
    }

    @Override
    public WebClientQueryParams add(String key, String... values) {
        rawParams.add(key, values);
        encodedParams.add(encode(key), encodeIterable(Arrays.asList(values)));
        return this;
    }

    @Override
    public WebClientQueryParams add(String key, Iterable<String> values) {
        rawParams.add(key, values);
        encodedParams.add(encode(key), encodeIterable(values));
        return this;
    }

    @Override
    public WebClientQueryParams addAll(Parameters parameters) {
        if (parameters == null) {
            return this;
        }
        rawParams.addAll(parameters);
        parameters.toMap().forEach((key, value) -> encodedParams.add(key, encodeIterable(value)));
        return this;
    }

    private Iterable<String> encodeIterable(Iterable<String> iterable) {
        if (iterable == null) {
            return null;
        }
        List<String> toReturn = new ArrayList<>();
        for (String value : iterable) {
            toReturn.add(encode(value));
        }
        return toReturn;
    }

    private String encode(String value) {
        return UriComponentEncoder.encode(value, UriComponentEncoder.Type.QUERY_PARAM);
    }

    @Override
    public List<String> remove(String key) {
        encodedParams.remove(encode(key));
        return rawParams.remove(key);
    }

    @Override
    public Map<String, List<String>> toMap() {
        return rawParams.toMap();
    }

    void skipEncoding() {
        this.skipEncoding = true;
    }

    Parameters pickCorrectParameters() {
        return skipEncoding ? rawParams : encodedParams;
    }
}
