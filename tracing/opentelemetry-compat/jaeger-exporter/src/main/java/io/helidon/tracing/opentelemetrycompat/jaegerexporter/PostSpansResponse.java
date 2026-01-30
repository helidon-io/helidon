/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.helidon.tracing.opentelemetrycompat.jaegerexporter;

// A Java object to correspond to the gRPC response for the Collector.PostSpans method. If fields
// are added to the type in the future, this can be converted to an actual class.
//
// It may seem like Void could be used instead but gRPC does not allow response values to be
// null.
enum PostSpansResponse {
  INSTANCE;
}
