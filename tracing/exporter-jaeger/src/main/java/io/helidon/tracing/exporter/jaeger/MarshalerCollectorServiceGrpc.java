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
package io.helidon.tracing.exporter.jaeger;

import java.io.InputStream;

import com.google.common.util.concurrent.ListenableFuture;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.MethodDescriptor;
import io.grpc.stub.ClientCalls;
import io.opentelemetry.exporter.internal.grpc.MarshalerInputStream;
import io.opentelemetry.exporter.internal.grpc.MarshalerServiceStub;

import static io.grpc.MethodDescriptor.generateFullMethodName;

// Adapted from the protoc generated code for CollectorServiceGrpc.
final class MarshalerCollectorServiceGrpc {

  private static final String SERVICE_NAME = "jaeger.api_v2.CollectorService";

  private static final MethodDescriptor.Marshaller<PostSpansRequestMarshaler> REQUEST_MARSHALLER =
      new MethodDescriptor.Marshaller<PostSpansRequestMarshaler>() {
        @Override
        public InputStream stream(PostSpansRequestMarshaler value) {
          return new MarshalerInputStream(value);
        }

        @Override
        public PostSpansRequestMarshaler parse(InputStream stream) {
          throw new UnsupportedOperationException("Only for serializing");
        }
      };

  private static final MethodDescriptor.Marshaller<PostSpansResponse> RESPONSE_MARSHALER =
      new MethodDescriptor.Marshaller<PostSpansResponse>() {
        @Override
        public InputStream stream(PostSpansResponse value) {
          throw new UnsupportedOperationException("Only for parsing");
        }

        @Override
        public PostSpansResponse parse(InputStream stream) {
          return PostSpansResponse.INSTANCE;
        }
      };

  private static final MethodDescriptor<PostSpansRequestMarshaler, PostSpansResponse>
          GET_POST_SPANS_METHOD =
          MethodDescriptor.<PostSpansRequestMarshaler, PostSpansResponse>newBuilder()
              .setType(MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "PostSpans"))
              .setRequestMarshaller(REQUEST_MARSHALLER)
              .setResponseMarshaller(RESPONSE_MARSHALER)
              .build();

  static CollectorServiceFutureStub newFutureStub(
      Channel channel, String authorityOverride) {
    return CollectorServiceFutureStub.newStub(
        (c, options) -> new CollectorServiceFutureStub(c, options.withAuthority(authorityOverride)),
        channel);
  }

  static final class CollectorServiceFutureStub
      extends MarshalerServiceStub<
          PostSpansRequestMarshaler, PostSpansResponse, CollectorServiceFutureStub> {
    private CollectorServiceFutureStub(Channel channel, CallOptions callOptions) {
      super(channel, callOptions);
    }

    @Override
    protected CollectorServiceFutureStub build(
        Channel channel, CallOptions callOptions) {
      return new CollectorServiceFutureStub(channel, callOptions);
    }

    @Override
    public ListenableFuture<PostSpansResponse> export(PostSpansRequestMarshaler request) {
      return ClientCalls.futureUnaryCall(
              getChannel().newCall(GET_POST_SPANS_METHOD, getCallOptions()), request);
    }
  }

  private MarshalerCollectorServiceGrpc() {
  }
}
