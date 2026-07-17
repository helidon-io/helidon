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

import io.grpc.ForwardingServerCall;
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
        return create(GrpcValidationConfig.create(config), name);
    }

    static GrpcValidation create(GrpcValidationConfig config, String name) {
        return new GrpcValidation(config.enabled(), name);
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

        AtomicBoolean callClosed = new AtomicBoolean();
        AtomicBoolean validationFailed = new AtomicBoolean();
        ServerCall<ReqT, RespT> validationCall = new ValidationCall<>(call, callClosed, validationFailed);
        try {
            return new ValidationListener<>(next.startCall(validationCall, headers), validationCall, validationFailed);
        } catch (ValidationException e) {
            validationFailed.set(true);
            validationCall.close(status(e), new Metadata());
            return new ServerCall.Listener<>() {
            };
        }
    }

    private static Status status(ValidationException exception) {
        Status status = Status.INVALID_ARGUMENT.withCause(exception);
        String message = exception.getMessage();
        return message == null ? status : status.withDescription(message);
    }

    private static ValidationException validationException(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof ValidationException validationException) {
                return validationException;
            }
            current = current.getCause();
        }
        return null;
    }

    private static final class ValidationCall<ReqT, RespT>
            extends ForwardingServerCall.SimpleForwardingServerCall<ReqT, RespT> {
        private final AtomicBoolean closed;
        private final AtomicBoolean validationFailed;

        private ValidationCall(ServerCall<ReqT, RespT> delegate,
                               AtomicBoolean closed,
                               AtomicBoolean validationFailed) {
            super(delegate);
            this.closed = closed;
            this.validationFailed = validationFailed;
        }

        @Override
        public void close(Status status, Metadata trailers) {
            Status mappedStatus = status;
            ValidationException exception = validationException(status.getCause());
            if (exception != null) {
                validationFailed.set(true);
                mappedStatus = status(exception);
            }
            if (closed.compareAndSet(false, true)) {
                super.close(mappedStatus, trailers);
            }
        }
    }

    private static final class ValidationListener<ReqT, RespT>
            extends ForwardingServerCallListener.SimpleForwardingServerCallListener<ReqT> {
        private final ServerCall<ReqT, RespT> call;
        private final AtomicBoolean validationFailed;

        private ValidationListener(ServerCall.Listener<ReqT> delegate,
                                   ServerCall<ReqT, RespT> call,
                                   AtomicBoolean validationFailed) {
            super(delegate);
            this.call = call;
            this.validationFailed = validationFailed;
        }

        @Override
        public void onMessage(ReqT message) {
            if (validationFailed.get()) {
                return;
            }

            try {
                super.onMessage(message);
            } catch (ValidationException e) {
                close(e);
            }
        }

        @Override
        public void onHalfClose() {
            if (validationFailed.get()) {
                return;
            }

            try {
                super.onHalfClose();
            } catch (ValidationException e) {
                close(e);
            }
        }

        @Override
        public void onCancel() {
            try {
                super.onCancel();
            } catch (ValidationException e) {
                close(e);
            }
        }

        @Override
        public void onComplete() {
            try {
                super.onComplete();
            } catch (ValidationException e) {
                close(e);
            }
        }

        @Override
        public void onReady() {
            if (validationFailed.get()) {
                return;
            }

            try {
                super.onReady();
            } catch (ValidationException e) {
                close(e);
            }
        }

        private void close(ValidationException exception) {
            if (validationFailed.compareAndSet(false, true)) {
                call.close(status(exception), new Metadata());
            }
        }
    }
}
