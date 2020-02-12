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

package io.helidon.grpc.core;

import io.helidon.common.http.Http;

import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;

/**
 * Helper methods for common gRPC tasks.
 */
public final class GrpcHelper {

    /**
     * Private constructor for utility class.
     */
    private GrpcHelper() {
    }

    /**
     * Extract the gRPC service name from a method full name.
     *
     * @param fullMethodName  the gRPC method full name
     *
     * @return  the service name extracted from the full name
     */
    public static String extractServiceName(String fullMethodName) {
        int index = fullMethodName.indexOf('/');
        return index == -1 ? fullMethodName : fullMethodName.substring(0, index);
    }

    /**
     * Extract the name prefix from from a method full name.
     * <p>
     * The prefix is everything upto the but not including the last
     * '/' character in the full name.
     *
     * @param fullMethodName  the gRPC method full name
     *
     * @return  the name prefix extracted from the full name
     */
    public static String extractNamePrefix(String fullMethodName) {
        int index = fullMethodName.lastIndexOf('/');
        return index == -1 ? fullMethodName : fullMethodName.substring(0, index);
    }

    /**
     * Extract the gRPC method name from a method full name.
     *
     * @param fullMethodName  the gRPC method full name
     *
     * @return  the method name extracted from the full name
     */
    public static String extractMethodName(String fullMethodName) {
        int index = fullMethodName.lastIndexOf('/');
        return index == -1 ? fullMethodName : fullMethodName.substring(index + 1);
    }

    /**
     * Convert a gRPC {@link StatusException} to a {@link Http.ResponseStatus}.
     *
     * @param ex  the gRPC {@link StatusException} to convert
     *
     * @return  the gRPC {@link StatusException} converted to a {@link Http.ResponseStatus}
     */
    public static Http.ResponseStatus toHttpResponseStatus(StatusException ex) {
        return toHttpResponseStatus(ex.getStatus());
    }

    /**
     * Convert a gRPC {@link StatusRuntimeException} to a {@link Http.ResponseStatus}.
     *
     * @param ex  the gRPC {@link StatusRuntimeException} to convert
     *
     * @return  the gRPC {@link StatusRuntimeException} converted to a {@link Http.ResponseStatus}
     */
    public static Http.ResponseStatus toHttpResponseStatus(StatusRuntimeException ex) {
        return toHttpResponseStatus(ex.getStatus());
    }

    /**
     * Convert a gRPC {@link Status} to a {@link Http.ResponseStatus}.
     *
     * @param status  the gRPC {@link Status} to convert
     *
     * @return  the gRPC {@link Status} converted to a {@link Http.ResponseStatus}
     */
    public static Http.ResponseStatus toHttpResponseStatus(Status status) {
        Http.ResponseStatus httpStatus;

        switch (status.getCode()) {
        case OK:
            httpStatus = Http.ResponseStatus.create(200, status.getDescription());
            break;
        case INVALID_ARGUMENT:
            httpStatus = Http.ResponseStatus.create(400, status.getDescription());
            break;
        case DEADLINE_EXCEEDED:
            httpStatus = Http.ResponseStatus.create(408, status.getDescription());
            break;
        case NOT_FOUND:
            httpStatus = Http.ResponseStatus.create(404, status.getDescription());
            break;
        case ALREADY_EXISTS:
            httpStatus = Http.ResponseStatus.create(412, status.getDescription());
            break;
        case PERMISSION_DENIED:
            httpStatus = Http.ResponseStatus.create(403, status.getDescription());
            break;
        case FAILED_PRECONDITION:
            httpStatus = Http.ResponseStatus.create(412, status.getDescription());
            break;
        case OUT_OF_RANGE:
            httpStatus = Http.ResponseStatus.create(400, status.getDescription());
            break;
        case UNIMPLEMENTED:
            httpStatus = Http.ResponseStatus.create(501, status.getDescription());
            break;
        case UNAVAILABLE:
            httpStatus = Http.ResponseStatus.create(503, status.getDescription());
            break;
        case UNAUTHENTICATED:
            httpStatus = Http.ResponseStatus.create(401, status.getDescription());
            break;
        case ABORTED:
        case CANCELLED:
        case DATA_LOSS:
        case INTERNAL:
        case RESOURCE_EXHAUSTED:
        case UNKNOWN:
        default:
            httpStatus = Http.ResponseStatus.create(500, status.getDescription());
        }

        return httpStatus;
    }

    /**
     * Ensure that a {@link Throwable} is either a {@link StatusRuntimeException} or
     * a {@link StatusException}.
     *
     * @param thrown  the {@link Throwable} to test
     * @param status  the {@link Status} to use if the {@link Throwable} has to be converted
     * @return  the {@link Throwable} if it is a {@link StatusRuntimeException} or a
     *          {@link StatusException}, or a new {@link StatusException} created from the
     *          specified {@link Status} with the {@link Throwable} as the cause.
     */
    public static Throwable ensureStatusException(Throwable thrown, Status status) {
        if (thrown instanceof StatusRuntimeException || thrown instanceof StatusException) {
            return thrown;
        } else {
            return status.withCause(thrown).asException();
        }
    }

    /**
     * Ensure that a {@link Throwable} is a {@link StatusRuntimeException}.
     *
     * @param thrown  the {@link Throwable} to test
     * @param status  the {@link Status} to use if the {@link Throwable} has to be converted
     * @return  the {@link Throwable} if it is a {@link StatusRuntimeException} or a new
     *          {@link StatusRuntimeException} created from the specified {@link Status}
     *          with the {@link Throwable} as the cause.
     */
    public static StatusRuntimeException ensureStatusRuntimeException(Throwable thrown, Status status) {
        if (thrown instanceof StatusRuntimeException) {
            return (StatusRuntimeException) thrown;
        } else if (thrown instanceof StatusException) {
            StatusException ex = (StatusException) thrown;
            return new StatusRuntimeException(ex.getStatus(), ex.getTrailers());
        } else {
            return status.withCause(thrown).asRuntimeException();
        }
    }
}
