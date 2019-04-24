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
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * {@link GrpcHelper} unit tests
 */
public class GrpcHelperTest {

    @Test
    public void shouldExtractServiceName() {
        String fullName = "Foo/1234/Bar";

        assertThat(GrpcHelper.extractServiceName(fullName), is("Foo"));
    }

    @Test
    public void shouldExtractMethodName() {
        String fullName = "Foo/1234/Bar";

        assertThat(GrpcHelper.extractMethodName(fullName), is("Bar"));
    }

    @Test
    public void shouldExtractNamePrefix() {
        String fullName = "Foo/1234/Bar";

        assertThat(GrpcHelper.extractNamePrefix(fullName), is("Foo/1234"));
    }

    @Test
    public void shouldConvertAbortedStatusException() {
        StatusException exception = Status.ABORTED.asException();
        Http.ResponseStatus status = GrpcHelper.toHttpResponseStatus(exception);

        assertThat(status.code(), is(500));
        assertThat(status.reasonPhrase(), is("Internal Server Error"));
    }

    @Test
    public void shouldConvertAbortedStatusExceptionWithDescription() {
        StatusException exception = Status.ABORTED.withDescription("Oops!").asException();
        Http.ResponseStatus status = GrpcHelper.toHttpResponseStatus(exception);

        assertThat(status.code(), is(500));
        assertThat(status.reasonPhrase(), is("Oops!"));
    }

    @Test
    public void shouldConvertAlreadyExistsStatusException() {
        StatusException exception = Status.ALREADY_EXISTS.asException();
        Http.ResponseStatus status = GrpcHelper.toHttpResponseStatus(exception);

        assertThat(status.code(), is(412));
        assertThat(status.reasonPhrase(), is("Precondition Failed"));
    }

    @Test
    public void shouldConvertAlreadyExistsStatusExceptionWithDescription() {
        StatusException exception = Status.ALREADY_EXISTS.withDescription("Oops!").asException();
        Http.ResponseStatus status = GrpcHelper.toHttpResponseStatus(exception);

        assertThat(status.code(), is(412));
        assertThat(status.reasonPhrase(), is("Oops!"));
    }

    @Test
    public void shouldConvertOkStatusException() {
        StatusException exception = Status.OK.asException();
        Http.ResponseStatus status = GrpcHelper.toHttpResponseStatus(exception);

        assertThat(status.code(), is(200));
        assertThat(status.reasonPhrase(), is("OK"));
    }

    @Test
    public void shouldConvertOkStatusExceptionWithDescription() {
        StatusException exception = Status.OK.withDescription("Good!").asException();
        Http.ResponseStatus status = GrpcHelper.toHttpResponseStatus(exception);

        assertThat(status.code(), is(200));
        assertThat(status.reasonPhrase(), is("Good!"));
    }

    @Test
    public void shouldConvertInvalidArgumentStatusException() {
        StatusException exception = Status.INVALID_ARGUMENT.asException();
        Http.ResponseStatus status = GrpcHelper.toHttpResponseStatus(exception);

        assertThat(status.code(), is(400));
        assertThat(status.reasonPhrase(), is("Bad Request"));
    }

    @Test
    public void shouldConvertInvalidArgumentStatusExceptionWithDescription() {
        StatusException exception = Status.INVALID_ARGUMENT.withDescription("Oops!").asException();
        Http.ResponseStatus status = GrpcHelper.toHttpResponseStatus(exception);

        assertThat(status.code(), is(400));
        assertThat(status.reasonPhrase(), is("Oops!"));
    }

    @Test
    public void shouldConvertDeadlineExceededStatusException() {
        StatusException exception = Status.DEADLINE_EXCEEDED.asException();
        Http.ResponseStatus status = GrpcHelper.toHttpResponseStatus(exception);

        assertThat(status.code(), is(408));
        assertThat(status.reasonPhrase(), is("Request Timeout"));
    }

    @Test
    public void shouldConvertDeadlineExceededStatusExceptionWithDescription() {
        StatusException exception = Status.DEADLINE_EXCEEDED.withDescription("Oops!").asException();
        Http.ResponseStatus status = GrpcHelper.toHttpResponseStatus(exception);

        assertThat(status.code(), is(408));
        assertThat(status.reasonPhrase(), is("Oops!"));
    }

    @Test
    public void shouldConvertNotFoundStatusException() {
        StatusException exception = Status.NOT_FOUND.asException();
        Http.ResponseStatus status = GrpcHelper.toHttpResponseStatus(exception);

        assertThat(status.code(), is(404));
        assertThat(status.reasonPhrase(), is("Not Found"));
    }

    @Test
    public void shouldConvertNotFoundStatusExceptionWithDescription() {
        StatusException exception = Status.NOT_FOUND.withDescription("Oops!").asException();
        Http.ResponseStatus status = GrpcHelper.toHttpResponseStatus(exception);

        assertThat(status.code(), is(404));
        assertThat(status.reasonPhrase(), is("Oops!"));
    }

    @Test
    public void shouldConvertPermissionDeniedStatusException() {
        StatusException exception = Status.PERMISSION_DENIED.asException();
        Http.ResponseStatus status = GrpcHelper.toHttpResponseStatus(exception);

        assertThat(status.code(), is(403));
        assertThat(status.reasonPhrase(), is("Forbidden"));
    }

    @Test
    public void shouldConvertPermissionDeniedStatusExceptionWithDescription() {
        StatusException exception = Status.PERMISSION_DENIED.withDescription("Oops!").asException();
        Http.ResponseStatus status = GrpcHelper.toHttpResponseStatus(exception);

        assertThat(status.code(), is(403));
        assertThat(status.reasonPhrase(), is("Oops!"));
    }

    @Test
    public void shouldConvertFailedPreconditionStatusException() {
        StatusException exception = Status.FAILED_PRECONDITION.asException();
        Http.ResponseStatus status = GrpcHelper.toHttpResponseStatus(exception);

        assertThat(status.code(), is(412));
        assertThat(status.reasonPhrase(), is("Precondition Failed"));
    }

    @Test
    public void shouldConvertFailedPreconditionStatusExceptionWithDescription() {
        StatusException exception = Status.FAILED_PRECONDITION.withDescription("Oops!").asException();
        Http.ResponseStatus status = GrpcHelper.toHttpResponseStatus(exception);

        assertThat(status.code(), is(412));
        assertThat(status.reasonPhrase(), is("Oops!"));
    }

    @Test
    public void shouldConvertOutOfRangeStatusException() {
        StatusException exception = Status.OUT_OF_RANGE.asException();
        Http.ResponseStatus status = GrpcHelper.toHttpResponseStatus(exception);

        assertThat(status.code(), is(400));
        assertThat(status.reasonPhrase(), is("Bad Request"));
    }

    @Test
    public void shouldConvertOutOfRangeStatusExceptionWithDescription() {
        StatusException exception = Status.OUT_OF_RANGE.withDescription("Oops!").asException();
        Http.ResponseStatus status = GrpcHelper.toHttpResponseStatus(exception);

        assertThat(status.code(), is(400));
        assertThat(status.reasonPhrase(), is("Oops!"));
    }

    @Test
    public void shouldConvertUnimplementedStatusException() {
        StatusException exception = Status.UNIMPLEMENTED.asException();
        Http.ResponseStatus status = GrpcHelper.toHttpResponseStatus(exception);

        assertThat(status.code(), is(501));
        assertThat(status.reasonPhrase(), is("Not Implemented"));
    }

    @Test
    public void shouldConvertUnimplementedStatusExceptionWithDescription() {
        StatusException exception = Status.UNIMPLEMENTED.withDescription("Oops!").asException();
        Http.ResponseStatus status = GrpcHelper.toHttpResponseStatus(exception);

        assertThat(status.code(), is(501));
        assertThat(status.reasonPhrase(), is("Oops!"));
    }

    @Test
    public void shouldConvertUnavailableStatusException() {
        StatusException exception = Status.UNAVAILABLE.asException();
        Http.ResponseStatus status = GrpcHelper.toHttpResponseStatus(exception);

        assertThat(status.code(), is(503));
        assertThat(status.reasonPhrase(), is("Service Unavailable"));
    }

    @Test
    public void shouldConvertUnavailableStatusExceptionWithDescription() {
        StatusException exception = Status.UNAVAILABLE.withDescription("Oops!").asException();
        Http.ResponseStatus status = GrpcHelper.toHttpResponseStatus(exception);

        assertThat(status.code(), is(503));
        assertThat(status.reasonPhrase(), is("Oops!"));
    }

    @Test
    public void shouldConvertUnauthenticatedStatusException() {
        StatusException exception = Status.UNAUTHENTICATED.asException();
        Http.ResponseStatus status = GrpcHelper.toHttpResponseStatus(exception);

        assertThat(status.code(), is(401));
        assertThat(status.reasonPhrase(), is("Unauthorized"));
    }

    @Test
    public void shouldConvertUnauthenticatedStatusExceptionWithDescription() {
        StatusException exception = Status.UNAUTHENTICATED.withDescription("Oops!").asException();
        Http.ResponseStatus status = GrpcHelper.toHttpResponseStatus(exception);

        assertThat(status.code(), is(401));
        assertThat(status.reasonPhrase(), is("Oops!"));
    }

    @Test
    public void shouldConvertCancelledStatusException() {
        StatusException exception = Status.CANCELLED.asException();
        Http.ResponseStatus status = GrpcHelper.toHttpResponseStatus(exception);

        assertThat(status.code(), is(500));
        assertThat(status.reasonPhrase(), is("Internal Server Error"));
    }

    @Test
    public void shouldConvertCancelledStatusExceptionWithDescription() {
        StatusException exception = Status.CANCELLED.withDescription("Oops!").asException();
        Http.ResponseStatus status = GrpcHelper.toHttpResponseStatus(exception);

        assertThat(status.code(), is(500));
        assertThat(status.reasonPhrase(), is("Oops!"));
    }

    @Test
    public void shouldConvertDataLossStatusException() {
        StatusException exception = Status.DATA_LOSS.asException();
        Http.ResponseStatus status = GrpcHelper.toHttpResponseStatus(exception);

        assertThat(status.code(), is(500));
        assertThat(status.reasonPhrase(), is("Internal Server Error"));
    }

    @Test
    public void shouldConvertDataLossStatusExceptionWithDescription() {
        StatusException exception = Status.DATA_LOSS.withDescription("Oops!").asException();
        Http.ResponseStatus status = GrpcHelper.toHttpResponseStatus(exception);

        assertThat(status.code(), is(500));
        assertThat(status.reasonPhrase(), is("Oops!"));
    }

    @Test
    public void shouldConvertInternalStatusException() {
        StatusException exception = Status.INTERNAL.asException();
        Http.ResponseStatus status = GrpcHelper.toHttpResponseStatus(exception);

        assertThat(status.code(), is(500));
        assertThat(status.reasonPhrase(), is("Internal Server Error"));
    }

    @Test
    public void shouldConvertInternalStatusExceptionWithDescription() {
        StatusException exception = Status.INTERNAL.withDescription("Oops!").asException();
        Http.ResponseStatus status = GrpcHelper.toHttpResponseStatus(exception);

        assertThat(status.code(), is(500));
        assertThat(status.reasonPhrase(), is("Oops!"));
    }

    @Test
    public void shouldConvertResourceExhaustedStatusException() {
        StatusException exception = Status.RESOURCE_EXHAUSTED.asException();
        Http.ResponseStatus status = GrpcHelper.toHttpResponseStatus(exception);

        assertThat(status.code(), is(500));
        assertThat(status.reasonPhrase(), is("Internal Server Error"));
    }

    @Test
    public void shouldConvertResourceExhaustedStatusExceptionWithDescription() {
        StatusException exception = Status.RESOURCE_EXHAUSTED.withDescription("Oops!").asException();
        Http.ResponseStatus status = GrpcHelper.toHttpResponseStatus(exception);

        assertThat(status.code(), is(500));
        assertThat(status.reasonPhrase(), is("Oops!"));
    }

    @Test
    public void shouldConvertUnknownStatusException() {
        StatusException exception = Status.UNKNOWN.asException();
        Http.ResponseStatus status = GrpcHelper.toHttpResponseStatus(exception);

        assertThat(status.code(), is(500));
        assertThat(status.reasonPhrase(), is("Internal Server Error"));
    }

    @Test
    public void shouldConvertUnknownStatusExceptionWithDescription() {
        StatusException exception = Status.UNKNOWN.withDescription("Oops!").asException();
        Http.ResponseStatus status = GrpcHelper.toHttpResponseStatus(exception);

        assertThat(status.code(), is(500));
        assertThat(status.reasonPhrase(), is("Oops!"));
    }

    @Test
    public void shouldConvertAbortedStatusRuntimeException() {
        StatusRuntimeException exception = Status.ABORTED.asRuntimeException();
        Http.ResponseStatus status = GrpcHelper.toHttpResponseStatus(exception);

        assertThat(status.code(), is(500));
        assertThat(status.reasonPhrase(), is("Internal Server Error"));
    }

    @Test
    public void shouldConvertAbortedStatusRuntimeExceptionWithDescription() {
        StatusRuntimeException exception = Status.ABORTED.withDescription("Oops!").asRuntimeException();
        Http.ResponseStatus status = GrpcHelper.toHttpResponseStatus(exception);

        assertThat(status.code(), is(500));
        assertThat(status.reasonPhrase(), is("Oops!"));
    }

    @Test
    public void shouldConvertAlreadyExistsStatusRuntimeException() {
        StatusRuntimeException exception = Status.ALREADY_EXISTS.asRuntimeException();
        Http.ResponseStatus status = GrpcHelper.toHttpResponseStatus(exception);

        assertThat(status.code(), is(412));
        assertThat(status.reasonPhrase(), is("Precondition Failed"));
    }

    @Test
    public void shouldConvertAlreadyExistsStatusRuntimeExceptionWithDescription() {
        StatusRuntimeException exception = Status.ALREADY_EXISTS.withDescription("Oops!").asRuntimeException();
        Http.ResponseStatus status = GrpcHelper.toHttpResponseStatus(exception);

        assertThat(status.code(), is(412));
        assertThat(status.reasonPhrase(), is("Oops!"));
    }

    @Test
    public void shouldConvertOkStatusRuntimeException() {
        StatusRuntimeException exception = Status.OK.asRuntimeException();
        Http.ResponseStatus status = GrpcHelper.toHttpResponseStatus(exception);

        assertThat(status.code(), is(200));
        assertThat(status.reasonPhrase(), is("OK"));
    }

    @Test
    public void shouldConvertOkStatusRuntimeExceptionWithDescription() {
        StatusRuntimeException exception = Status.OK.withDescription("Good!").asRuntimeException();
        Http.ResponseStatus status = GrpcHelper.toHttpResponseStatus(exception);

        assertThat(status.code(), is(200));
        assertThat(status.reasonPhrase(), is("Good!"));
    }

    @Test
    public void shouldConvertInvalidArgumentStatusRuntimeException() {
        StatusRuntimeException exception = Status.INVALID_ARGUMENT.asRuntimeException();
        Http.ResponseStatus status = GrpcHelper.toHttpResponseStatus(exception);

        assertThat(status.code(), is(400));
        assertThat(status.reasonPhrase(), is("Bad Request"));
    }

    @Test
    public void shouldConvertInvalidArgumentStatusRuntimeExceptionWithDescription() {
        StatusRuntimeException exception = Status.INVALID_ARGUMENT.withDescription("Oops!").asRuntimeException();
        Http.ResponseStatus status = GrpcHelper.toHttpResponseStatus(exception);

        assertThat(status.code(), is(400));
        assertThat(status.reasonPhrase(), is("Oops!"));
    }

    @Test
    public void shouldConvertDeadlineExceededStatusRuntimeException() {
        StatusRuntimeException exception = Status.DEADLINE_EXCEEDED.asRuntimeException();
        Http.ResponseStatus status = GrpcHelper.toHttpResponseStatus(exception);

        assertThat(status.code(), is(408));
        assertThat(status.reasonPhrase(), is("Request Timeout"));
    }

    @Test
    public void shouldConvertDeadlineExceededStatusRuntimeExceptionWithDescription() {
        StatusRuntimeException exception = Status.DEADLINE_EXCEEDED.withDescription("Oops!").asRuntimeException();
        Http.ResponseStatus status = GrpcHelper.toHttpResponseStatus(exception);

        assertThat(status.code(), is(408));
        assertThat(status.reasonPhrase(), is("Oops!"));
    }

    @Test
    public void shouldConvertNotFoundStatusRuntimeException() {
        StatusRuntimeException exception = Status.NOT_FOUND.asRuntimeException();
        Http.ResponseStatus status = GrpcHelper.toHttpResponseStatus(exception);

        assertThat(status.code(), is(404));
        assertThat(status.reasonPhrase(), is("Not Found"));
    }

    @Test
    public void shouldConvertNotFoundStatusRuntimeExceptionWithDescription() {
        StatusRuntimeException exception = Status.NOT_FOUND.withDescription("Oops!").asRuntimeException();
        Http.ResponseStatus status = GrpcHelper.toHttpResponseStatus(exception);

        assertThat(status.code(), is(404));
        assertThat(status.reasonPhrase(), is("Oops!"));
    }

    @Test
    public void shouldConvertPermissionDeniedStatusRuntimeException() {
        StatusRuntimeException exception = Status.PERMISSION_DENIED.asRuntimeException();
        Http.ResponseStatus status = GrpcHelper.toHttpResponseStatus(exception);

        assertThat(status.code(), is(403));
        assertThat(status.reasonPhrase(), is("Forbidden"));
    }

    @Test
    public void shouldConvertPermissionDeniedStatusRuntimeExceptionWithDescription() {
        StatusRuntimeException exception = Status.PERMISSION_DENIED.withDescription("Oops!").asRuntimeException();
        Http.ResponseStatus status = GrpcHelper.toHttpResponseStatus(exception);

        assertThat(status.code(), is(403));
        assertThat(status.reasonPhrase(), is("Oops!"));
    }

    @Test
    public void shouldConvertFailedPreconditionStatusRuntimeException() {
        StatusRuntimeException exception = Status.FAILED_PRECONDITION.asRuntimeException();
        Http.ResponseStatus status = GrpcHelper.toHttpResponseStatus(exception);

        assertThat(status.code(), is(412));
        assertThat(status.reasonPhrase(), is("Precondition Failed"));
    }

    @Test
    public void shouldConvertFailedPreconditionStatusRuntimeExceptionWithDescription() {
        StatusRuntimeException exception = Status.FAILED_PRECONDITION.withDescription("Oops!").asRuntimeException();
        Http.ResponseStatus status = GrpcHelper.toHttpResponseStatus(exception);

        assertThat(status.code(), is(412));
        assertThat(status.reasonPhrase(), is("Oops!"));
    }

    @Test
    public void shouldConvertOutOfRangeStatusRuntimeException() {
        StatusRuntimeException exception = Status.OUT_OF_RANGE.asRuntimeException();
        Http.ResponseStatus status = GrpcHelper.toHttpResponseStatus(exception);

        assertThat(status.code(), is(400));
        assertThat(status.reasonPhrase(), is("Bad Request"));
    }

    @Test
    public void shouldConvertOutOfRangeStatusRuntimeExceptionWithDescription() {
        StatusRuntimeException exception = Status.OUT_OF_RANGE.withDescription("Oops!").asRuntimeException();
        Http.ResponseStatus status = GrpcHelper.toHttpResponseStatus(exception);

        assertThat(status.code(), is(400));
        assertThat(status.reasonPhrase(), is("Oops!"));
    }

    @Test
    public void shouldConvertUnimplementedStatusRuntimeException() {
        StatusRuntimeException exception = Status.UNIMPLEMENTED.asRuntimeException();
        Http.ResponseStatus status = GrpcHelper.toHttpResponseStatus(exception);

        assertThat(status.code(), is(501));
        assertThat(status.reasonPhrase(), is("Not Implemented"));
    }

    @Test
    public void shouldConvertUnimplementedStatusRuntimeExceptionWithDescription() {
        StatusRuntimeException exception = Status.UNIMPLEMENTED.withDescription("Oops!").asRuntimeException();
        Http.ResponseStatus status = GrpcHelper.toHttpResponseStatus(exception);

        assertThat(status.code(), is(501));
        assertThat(status.reasonPhrase(), is("Oops!"));
    }

    @Test
    public void shouldConvertUnavailableStatusRuntimeException() {
        StatusRuntimeException exception = Status.UNAVAILABLE.asRuntimeException();
        Http.ResponseStatus status = GrpcHelper.toHttpResponseStatus(exception);

        assertThat(status.code(), is(503));
        assertThat(status.reasonPhrase(), is("Service Unavailable"));
    }

    @Test
    public void shouldConvertUnavailableStatusRuntimeExceptionWithDescription() {
        StatusRuntimeException exception = Status.UNAVAILABLE.withDescription("Oops!").asRuntimeException();
        Http.ResponseStatus status = GrpcHelper.toHttpResponseStatus(exception);

        assertThat(status.code(), is(503));
        assertThat(status.reasonPhrase(), is("Oops!"));
    }

    @Test
    public void shouldConvertUnauthenticatedStatusRuntimeException() {
        StatusRuntimeException exception = Status.UNAUTHENTICATED.asRuntimeException();
        Http.ResponseStatus status = GrpcHelper.toHttpResponseStatus(exception);

        assertThat(status.code(), is(401));
        assertThat(status.reasonPhrase(), is("Unauthorized"));
    }

    @Test
    public void shouldConvertUnauthenticatedStatusRuntimeExceptionWithDescription() {
        StatusRuntimeException exception = Status.UNAUTHENTICATED.withDescription("Oops!").asRuntimeException();
        Http.ResponseStatus status = GrpcHelper.toHttpResponseStatus(exception);

        assertThat(status.code(), is(401));
        assertThat(status.reasonPhrase(), is("Oops!"));
    }

    @Test
    public void shouldConvertCancelledStatusRuntimeException() {
        StatusRuntimeException exception = Status.CANCELLED.asRuntimeException();
        Http.ResponseStatus status = GrpcHelper.toHttpResponseStatus(exception);

        assertThat(status.code(), is(500));
        assertThat(status.reasonPhrase(), is("Internal Server Error"));
    }

    @Test
    public void shouldConvertCancelledStatusRuntimeExceptionWithDescription() {
        StatusRuntimeException exception = Status.CANCELLED.withDescription("Oops!").asRuntimeException();
        Http.ResponseStatus status = GrpcHelper.toHttpResponseStatus(exception);

        assertThat(status.code(), is(500));
        assertThat(status.reasonPhrase(), is("Oops!"));
    }

    @Test
    public void shouldConvertDataLossStatusRuntimeException() {
        StatusRuntimeException exception = Status.DATA_LOSS.asRuntimeException();
        Http.ResponseStatus status = GrpcHelper.toHttpResponseStatus(exception);

        assertThat(status.code(), is(500));
        assertThat(status.reasonPhrase(), is("Internal Server Error"));
    }

    @Test
    public void shouldConvertDataLossStatusRuntimeExceptionWithDescription() {
        StatusRuntimeException exception = Status.DATA_LOSS.withDescription("Oops!").asRuntimeException();
        Http.ResponseStatus status = GrpcHelper.toHttpResponseStatus(exception);

        assertThat(status.code(), is(500));
        assertThat(status.reasonPhrase(), is("Oops!"));
    }

    @Test
    public void shouldConvertInternalStatusRuntimeException() {
        StatusRuntimeException exception = Status.INTERNAL.asRuntimeException();
        Http.ResponseStatus status = GrpcHelper.toHttpResponseStatus(exception);

        assertThat(status.code(), is(500));
        assertThat(status.reasonPhrase(), is("Internal Server Error"));
    }

    @Test
    public void shouldConvertInternalStatusRuntimeExceptionWithDescription() {
        StatusRuntimeException exception = Status.INTERNAL.withDescription("Oops!").asRuntimeException();
        Http.ResponseStatus status = GrpcHelper.toHttpResponseStatus(exception);

        assertThat(status.code(), is(500));
        assertThat(status.reasonPhrase(), is("Oops!"));
    }

    @Test
    public void shouldConvertResourceExhaustedStatusRuntimeException() {
        StatusRuntimeException exception = Status.RESOURCE_EXHAUSTED.asRuntimeException();
        Http.ResponseStatus status = GrpcHelper.toHttpResponseStatus(exception);

        assertThat(status.code(), is(500));
        assertThat(status.reasonPhrase(), is("Internal Server Error"));
    }

    @Test
    public void shouldConvertResourceExhaustedStatusRuntimeExceptionWithDescription() {
        StatusRuntimeException exception = Status.RESOURCE_EXHAUSTED.withDescription("Oops!").asRuntimeException();
        Http.ResponseStatus status = GrpcHelper.toHttpResponseStatus(exception);

        assertThat(status.code(), is(500));
        assertThat(status.reasonPhrase(), is("Oops!"));
    }

    @Test
    public void shouldConvertUnknownStatusRuntimeException() {
        StatusRuntimeException exception = Status.UNKNOWN.asRuntimeException();
        Http.ResponseStatus status = GrpcHelper.toHttpResponseStatus(exception);

        assertThat(status.code(), is(500));
        assertThat(status.reasonPhrase(), is("Internal Server Error"));
    }

    @Test
    public void shouldConvertUnknownStatusRuntimeExceptionWithDescription() {
        StatusRuntimeException exception = Status.UNKNOWN.withDescription("Oops!").asRuntimeException();
        Http.ResponseStatus status = GrpcHelper.toHttpResponseStatus(exception);

        assertThat(status.code(), is(500));
        assertThat(status.reasonPhrase(), is("Oops!"));
    }
}
