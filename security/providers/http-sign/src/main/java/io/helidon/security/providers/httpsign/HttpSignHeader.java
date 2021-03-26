/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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

package io.helidon.security.providers.httpsign;

/**
 * Headers supported by HTTP Signature.
 */
public enum HttpSignHeader {
    /**
     * Creates (or validates) a "Signature" header.
     */
    SIGNATURE,
    /**
     * Creates (or validates) an "Authorization" header, that contains "Signature" as the
     * beginning of its content (the rest of the header is the same as for {@link #SIGNATURE}.
     */
    AUTHORIZATION,
    /**
     * Custom provided using a {@link io.helidon.security.util.TokenHandler}.
     */
    CUSTOM
}
