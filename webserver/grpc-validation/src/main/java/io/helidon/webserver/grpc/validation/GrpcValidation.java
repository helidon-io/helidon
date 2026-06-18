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

package io.helidon.webserver.grpc.validation;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import io.helidon.config.Config;
import io.helidon.grpc.core.InterceptorWeights;
import io.helidon.grpc.core.WeightedBag;
import io.helidon.validation.ValidationException;
import io.helidon.webserver.grpc.spi.GrpcServerService;

import io.grpc.ForwardingServerCallListener;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;

/**
 * gRPC validation support.
 * <p>
 * Maps {@link ValidationException} thrown while handling a gRPC call to {@link Status#INVALID_ARGUMENT}.
 */
public final class GrpcValidation implements ServerInterceptor, GrpcServerService {
    static final String TYPE = "validation";
    static final int WEIGHT = InterceptorWeights.USER + 1;

    private static final GrpcValidation DEFAULT_INSTANCE = new GrpcValidation(true, TYPE);

    private final boolean enabled;
    private final String name;

    private GrpcValidation(boolean enabled, String name) {
        this.enabled = enabled;
        this.name = Objects.requireNonNull(name);
    }

    /**
     * Create gRPC validation support.
     *
     * @return gRPC validation support
     */
    public static GrpcValidation create() {
        return DEFAULT_INSTANCE;
    }

    static GrpcValidation create(Config config, String name) {
        return new GrpcValidation(config.get("enabled")
                                          .asBoolean()
                                          .orElse(true),
                                  name);
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public WeightedBag<ServerInterceptor> interceptors() {
        WeightedBag<ServerInterceptor> interceptors = WeightedBag.create();
        if (enabled) {
            interceptors.add(this, WEIGHT);
        }
        return interceptors;
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call,
                                                                 Metadata headers,
                                                                 ServerCallHandler<ReqT, RespT> next) {
        if (!enabled) {
            return next.startCall(call, headers);
        }

        try {
            return new ValidationListener<>(next.startCall(call, headers), call);
        } catch (ValidationException e) {
            call.close(status(e), new Metadata());
            return new ServerCall.Listener<>() {
            };
        }
    }

    private static Status status(ValidationException exception) {
        Status status = Status.INVALID_ARGUMENT.withCause(exception);
        String message = exception.getMessage();
        return message == null ? status : status.withDescription(message);
    }

    private static final class ValidationListener<ReqT, RespT>
            extends ForwardingServerCallListener.SimpleForwardingServerCallListener<ReqT> {
        private final ServerCall<ReqT, RespT> call;
        private final AtomicBoolean closed = new AtomicBoolean();

        private ValidationListener(ServerCall.Listener<ReqT> delegate, ServerCall<ReqT, RespT> call) {
            super(delegate);
            this.call = call;
        }

        @Override
        public void onMessage(ReqT message) {
            invoke(() -> super.onMessage(message));
        }

        @Override
        public void onHalfClose() {
            invoke(super::onHalfClose);
        }

        @Override
        public void onCancel() {
            invoke(super::onCancel);
        }

        @Override
        public void onComplete() {
            invoke(super::onComplete);
        }

        @Override
        public void onReady() {
            invoke(super::onReady);
        }

        private void invoke(Runnable runnable) {
            if (closed.get()) {
                return;
            }

            try {
                runnable.run();
            } catch (ValidationException e) {
                if (closed.compareAndSet(false, true)) {
                    call.close(status(e), new Metadata());
                }
            }
        }
    }
}
