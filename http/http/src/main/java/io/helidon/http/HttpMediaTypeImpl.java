/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import io.helidon.common.media.type.MediaType;

final class HttpMediaTypeImpl implements HttpMediaType {
    private final Map<String, String> parameters = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private final MediaType mediaType;

    private double q = -1;

    HttpMediaTypeImpl(Builder builder) {
        this.parameters.putAll(builder.parameters());
        this.mediaType = builder.mediaType();
    }

    @Override
    public int compareTo(HttpMediaType o) {
        // decreasing order
        int compared = Double.compare(o.qualityFactor(), this.qualityFactor());
        if (compared == 0) {
            if (this.mediaType.isWildcardSubtype() && !o.mediaType().isWildcardSubtype()) {
                return 1; // the other is more important
            }
            if (this.mediaType.isWildcardType() && !o.mediaType().isWildcardType()) {
                return 1; // the other is more important
            }
            if (o.mediaType().isWildcardSubtype() && !this.mediaType.isWildcardSubtype()) {
                return -1; // we are more important (other has wildcard subtype)
            }
            if (o.mediaType().isWildcardType() && !this.mediaType.isWildcardType()) {
                return -1; // we are more important (other has wildcard type)
            }
            return 0; // we do not care - same importance
        } else {
            return compared;
        }
    }

    @Override
    public MediaType mediaType() {
        return mediaType;
    }

    @Override
    public double qualityFactor() {
        if (q == -1) {
            String q = parameters.get("q");
            if (q == null) {
                this.q = 1;
            } else {
                this.q = Double.parseDouble(q);
            }
        }
        return q;
    }

    @Override
    public Map<String, String> parameters() {
        return Map.copyOf(parameters);
    }

    @Override
    public boolean test(HttpMediaType other) {
        if (mediaType == null) {
            return false;
        }

        if (typeMismatch(other)) {
            return false;
        }

        return !subtypeMismatch(other);
    }

    @Override
    public boolean test(MediaType mediaType) {
        if (mediaType == null) {
            return false;
        }

        if (typeMismatch(mediaType)) {
            return false;
        }

        return !subtypeMismatch(mediaType);
    }

    @Override
    public String text() {
        StringBuilder result = new StringBuilder(mediaType.text());
        for (Map.Entry<String, String> param : parameters.entrySet()) {
            result.append("; ")
                    .append(param.getKey())
                    .append('=')
                    .append(param.getValue());
        }
        return result.toString();
    }

    @Override
    public int hashCode() {
        return Objects.hash(parameters, mediaType);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof HttpMediaTypeImpl that)) {
            return false;
        }
        return parameters.equals(that.parameters) && mediaType.equals(that.mediaType);
    }

    @Override
    public String toString() {
        return text();
    }

    private boolean subtypeMismatch(MediaType other) {
        if (mediaType.isWildcardSubtype() || other.isWildcardSubtype()) {
            return false;
        }
        return !mediaType.subtype().equalsIgnoreCase(other.subtype());
    }

    private boolean typeMismatch(MediaType other) {
        if (mediaType.isWildcardType() || other.isWildcardType()) {
            return false;
        }
        return !mediaType.type().equalsIgnoreCase(other.type());
    }
}
