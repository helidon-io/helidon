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

package io.helidon.common.uri;

import java.util.Objects;

/**
 * Fragment section of the URI.
 */
public class UriFragment {
    private static final UriFragment EMPTY = new UriFragment(null, null);

    private final String rawFragment;

    private String decodedFragment;

    private UriFragment(String rawFragment) {
        this.rawFragment = rawFragment;
    }

    private UriFragment(String encoded, String fragment) {
        this.rawFragment = encoded;
        this.decodedFragment = fragment;
    }

    /**
     * Create a fragment from raw value.
     *
     * @param rawFragment fragment encoded value
     * @return a new instance
     */
    public static UriFragment create(String rawFragment) {
        Objects.requireNonNull(rawFragment);
        return new UriFragment(rawFragment);
    }

    /**
     * Create a fragment from decoded value.
     *
     * @param fragment fragment decoded value
     * @return a new instance
     */
    public static UriFragment createFromDecoded(String fragment) {
        return new UriFragment(UriEncoding.encode(fragment, UriEncoding.Type.FRAGMENT), fragment);
    }

    /**
     * Empty fragment.
     * @return empty instance
     */
    public static UriFragment empty() {
        return EMPTY;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof UriFragment that)) {
            return false;
        }
        return Objects.equals(rawFragment, that.rawFragment);
    }

    @Override
    public int hashCode() {
        return Objects.hash(rawFragment);
    }

    @Override
    public String toString() {
        if (rawFragment == null) {
            return "";
        }
        return value();
    }

    /**
     * Whether there is a fragment.
     *
     * @return {@code true} if fragment exists
     */
    public boolean hasValue() {
        return rawFragment != null;
    }

    /**
     * Raw (encoded) value of the fragment.
     *
     * @return encoded fragment
     */
    public String rawValue() {
        if (rawFragment == null) {
            throw new IllegalStateException("UriFragment does not have a value, guard with hasValue()");
        }
        return rawFragment;
    }

    /**
     * Value (decoded) of the fragment.
     *
     * @return decoded fragment
     */
    public String value() {
        if (decodedFragment == null) {
            decodedFragment = UriEncoding.decodeUri(rawValue());
        }
        return decodedFragment;
    }
}
