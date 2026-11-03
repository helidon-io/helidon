/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.helidon.tracing.exporter.zipkin;

import zipkin2.Span;
import zipkin2.reporter.BytesEncoder;
import zipkin2.reporter.Encoding;

/**
 * This supports the deprecated method {@link
 * ZipkinSpanExporterConfig.Builder#encoder(zipkin2.codec.BytesEncoder)}.
 */
final class BytesEncoderAdapter implements BytesEncoder<Span> {
    private final zipkin2.codec.BytesEncoder<Span> delegate;
    private final Encoding encoding;

    // The legacy interface includes deprecated Thrift encoding; this enum switch is exhaustive.
    @SuppressWarnings({"deprecation", "checkstyle:MissingSwitchDefault"})
    BytesEncoderAdapter(zipkin2.codec.BytesEncoder<Span> delegate) {
        this.delegate = delegate;
        this.encoding = switch (delegate.encoding()) {
            case JSON -> Encoding.JSON;
            case PROTO3 -> Encoding.PROTO3;
            case THRIFT -> Encoding.THRIFT;
        };
    }

    @Override
    public Encoding encoding() {
        return encoding;
    }

    @Override
    public int sizeInBytes(Span span) {
        return delegate.sizeInBytes(span);
    }

    @Override
    public byte[] encode(Span span) {
        return delegate.encode(span);
    }

    @Override
    public String toString() {
        return delegate.toString();
    }
}
