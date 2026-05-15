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

package io.helidon.webserver.tests.grpc;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import io.helidon.webserver.WebServer;
import io.helidon.webserver.grpc.GrpcReflectionFeature;
import io.helidon.webserver.grpc.GrpcRouting;
import io.helidon.webserver.grpc.GrpcService;
import io.helidon.webserver.grpc.courses.CourseServiceGrpc;
import io.helidon.webserver.grpc.courses.Courses;

import com.google.protobuf.Empty;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.reflection.v1.ExtensionRequest;
import io.grpc.reflection.v1.ServerReflectionGrpc;
import io.grpc.reflection.v1.ServerReflectionRequest;
import io.grpc.reflection.v1.ServerReflectionResponse;
import io.grpc.reflection.v1.ServiceResponse;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReflectionIsolationTest {
    private static final long TIMEOUT_SECONDS = 10;
    private static final String COURSE_SERVICE = "CourseService";
    private static final String COURSES_PROTO = "courses.proto";
    private static final String STRING_MESSAGE = "StringMessage";
    private static final int STRING_EXTENSION_NUMBER = 100;

    @Test
    void reflectionDoesNotExposeRoutesFromOtherServersOnDefaultSocket() throws InterruptedException {
        WebServer stringServer = null;
        WebServer courseServer = null;
        ManagedChannel stringChannel = null;
        ManagedChannel courseChannel = null;

        try {
            stringServer = startServer(new StringService());
            courseServer = startServer(new CourseService());
            stringChannel = ManagedChannelBuilder.forAddress("localhost", stringServer.port())
                    .usePlaintext()
                    .build();
            courseChannel = ManagedChannelBuilder.forAddress("localhost", courseServer.port())
                    .usePlaintext()
                    .build();

            Courses.CourseResponse courseResponse = CourseServiceGrpc.newBlockingStub(courseChannel)
                    .withDeadlineAfter(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .getCourses(Empty.getDefaultInstance());
            assertThat(courseResponse.getCoursesCount(), is(2));

            ServerReflectionResponse stringExtension = reflectionResponse(stringChannel, stringExtensionRequest());
            assertThat(stringExtension.hasFileDescriptorResponse(), is(true));
            ServerReflectionResponse leakedExtensionOnCourseServer = reflectionResponse(courseChannel, stringExtensionRequest());
            assertThat(leakedExtensionOnCourseServer.hasErrorResponse(), is(true));
            assertThat(leakedExtensionOnCourseServer.getErrorResponse().getErrorCode(),
                       is(Status.NOT_FOUND.getCode().value()));

            ServerReflectionResponse courseDescriptor = reflectionResponse(
                    courseChannel,
                    ServerReflectionRequest.newBuilder()
                            .setFileContainingSymbol(COURSE_SERVICE)
                            .build());
            assertThat(courseDescriptor.hasFileDescriptorResponse(), is(true));
            ServerReflectionResponse courseFile = reflectionResponse(courseChannel,
                                                                     ServerReflectionRequest.newBuilder()
                                                                             .setFileByFilename(COURSES_PROTO)
                                                                             .build());
            assertThat(courseFile.hasFileDescriptorResponse(), is(true));

            io.grpc.reflection.v1alpha.ServerReflectionResponse courseV1AlphaDescriptor =
                    reflectionV1AlphaResponse(courseChannel,
                                              io.grpc.reflection.v1alpha.ServerReflectionRequest.newBuilder()
                                                      .setFileContainingSymbol(COURSE_SERVICE)
                                                      .build());
            assertThat(courseV1AlphaDescriptor.hasFileDescriptorResponse(), is(true));
            io.grpc.reflection.v1alpha.ServerReflectionResponse courseV1AlphaFile =
                    reflectionV1AlphaResponse(courseChannel,
                                              io.grpc.reflection.v1alpha.ServerReflectionRequest.newBuilder()
                                                      .setFileByFilename(COURSES_PROTO)
                                                      .build());
            assertThat(courseV1AlphaFile.hasFileDescriptorResponse(), is(true));

            io.grpc.reflection.v1alpha.ServerReflectionResponse stringV1AlphaExtension =
                    reflectionV1AlphaResponse(stringChannel, stringV1AlphaExtensionRequest());
            assertThat(stringV1AlphaExtension.hasFileDescriptorResponse(), is(true));
            io.grpc.reflection.v1alpha.ServerReflectionResponse leakedV1AlphaExtensionOnCourseServer =
                    reflectionV1AlphaResponse(courseChannel, stringV1AlphaExtensionRequest());
            assertThat(leakedV1AlphaExtensionOnCourseServer.hasErrorResponse(), is(true));
            assertThat(leakedV1AlphaExtensionOnCourseServer.getErrorResponse().getErrorCode(),
                       is(Status.NOT_FOUND.getCode().value()));

            ManagedChannel stringServerChannel = stringChannel;
            StatusRuntimeException courseOnStringServer = assertThrows(StatusRuntimeException.class,
                                                                       () -> CourseServiceGrpc
                                                                               .newBlockingStub(stringServerChannel)
                                                                               .withDeadlineAfter(TIMEOUT_SECONDS,
                                                                                                  TimeUnit.SECONDS)
                                                                               .getCourses(Empty.getDefaultInstance()));
            assertThat(courseOnStringServer.getStatus().getCode(), is(Status.Code.UNIMPLEMENTED));

            ServerReflectionResponse listResponse = reflectionResponse(stringChannel,
                                                                       ServerReflectionRequest.newBuilder()
                                                                               .setListServices("*")
                                                                               .build());
            Set<String> names = listResponse
                    .getListServicesResponse()
                    .getServiceList()
                    .stream()
                    .map(ServiceResponse::getName)
                    .collect(Collectors.toSet());

            assertThat(names, hasItem("StringService"));
            assertThat(names, not(hasItem(COURSE_SERVICE)));

            ServerReflectionResponse leakedDescriptor = reflectionResponse(
                    stringChannel,
                    ServerReflectionRequest.newBuilder()
                            .setFileContainingSymbol(COURSE_SERVICE)
                            .build());
            assertThat(leakedDescriptor.hasErrorResponse(), is(true));
            assertThat(leakedDescriptor.getErrorResponse().getErrorCode(), is(Status.NOT_FOUND.getCode().value()));
            ServerReflectionResponse leakedFile = reflectionResponse(stringChannel,
                                                                     ServerReflectionRequest.newBuilder()
                                                                             .setFileByFilename(COURSES_PROTO)
                                                                             .build());
            assertThat(leakedFile.hasErrorResponse(), is(true));
            assertThat(leakedFile.getErrorResponse().getErrorCode(), is(Status.NOT_FOUND.getCode().value()));

            io.grpc.reflection.v1alpha.ServerReflectionResponse v1AlphaListResponse =
                    reflectionV1AlphaResponse(stringChannel,
                                              io.grpc.reflection.v1alpha.ServerReflectionRequest.newBuilder()
                                                      .setListServices("*")
                                                      .build());
            Set<String> v1AlphaNames = v1AlphaListResponse
                    .getListServicesResponse()
                    .getServiceList()
                    .stream()
                    .map(io.grpc.reflection.v1alpha.ServiceResponse::getName)
                    .collect(Collectors.toSet());

            assertThat(v1AlphaNames, hasItem("StringService"));
            assertThat(v1AlphaNames, not(hasItem(COURSE_SERVICE)));

            io.grpc.reflection.v1alpha.ServerReflectionResponse v1AlphaLeakedDescriptor =
                    reflectionV1AlphaResponse(stringChannel,
                                              io.grpc.reflection.v1alpha.ServerReflectionRequest.newBuilder()
                                                      .setFileContainingSymbol(COURSE_SERVICE)
                                                      .build());
            assertThat(v1AlphaLeakedDescriptor.hasErrorResponse(), is(true));
            assertThat(v1AlphaLeakedDescriptor.getErrorResponse().getErrorCode(),
                       is(Status.NOT_FOUND.getCode().value()));
            io.grpc.reflection.v1alpha.ServerReflectionResponse v1AlphaLeakedFile =
                    reflectionV1AlphaResponse(stringChannel,
                                              io.grpc.reflection.v1alpha.ServerReflectionRequest.newBuilder()
                                                      .setFileByFilename(COURSES_PROTO)
                                                      .build());
            assertThat(v1AlphaLeakedFile.hasErrorResponse(), is(true));
            assertThat(v1AlphaLeakedFile.getErrorResponse().getErrorCode(), is(Status.NOT_FOUND.getCode().value()));
        } finally {
            if (courseChannel != null) {
                close(courseChannel);
            }
            if (stringChannel != null) {
                close(stringChannel);
            }
            if (courseServer != null) {
                courseServer.stop();
            }
            if (stringServer != null) {
                stringServer.stop();
            }
        }
    }

    private static WebServer startServer(GrpcService service) {
        return WebServer.builder()
                .port(0)
                .shutdownHook(false)
                .addFeature(GrpcReflectionFeature.builder()
                                    .enabled(true)
                                    .build())
                .addRouting(GrpcRouting.builder().service(service))
                .build()
                .start();
    }

    private static ServerReflectionResponse reflectionResponse(ManagedChannel channel, ServerReflectionRequest request)
            throws InterruptedException {
        TestObserver<ServerReflectionResponse> responses = new TestObserver<>(1);
        StreamObserver<ServerReflectionRequest> requests = ServerReflectionGrpc.newStub(channel)
                .serverReflectionInfo(responses);
        requests.onNext(request);
        requests.onCompleted();

        assertThat(responses.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), is(true));
        List<ServerReflectionResponse> responseList = responses.getResponses();
        assertThat(responseList.size(), is(1));
        return responseList.getFirst();
    }

    private static ServerReflectionRequest stringExtensionRequest() {
        return ServerReflectionRequest.newBuilder()
                .setFileContainingExtension(
                        ExtensionRequest.newBuilder()
                                .setContainingType(STRING_MESSAGE)
                                .setExtensionNumber(STRING_EXTENSION_NUMBER)
                                .build())
                .build();
    }

    private static io.grpc.reflection.v1alpha.ServerReflectionResponse reflectionV1AlphaResponse(
            ManagedChannel channel,
            io.grpc.reflection.v1alpha.ServerReflectionRequest request) throws InterruptedException {
        TestObserver<io.grpc.reflection.v1alpha.ServerReflectionResponse> responses = new TestObserver<>(1);
        StreamObserver<io.grpc.reflection.v1alpha.ServerReflectionRequest> requests =
                io.grpc.reflection.v1alpha.ServerReflectionGrpc.newStub(channel)
                        .serverReflectionInfo(responses);
        requests.onNext(request);
        requests.onCompleted();

        assertThat(responses.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), is(true));
        List<io.grpc.reflection.v1alpha.ServerReflectionResponse> responseList = responses.getResponses();
        assertThat(responseList.size(), is(1));
        return responseList.getFirst();
    }

    private static io.grpc.reflection.v1alpha.ServerReflectionRequest stringV1AlphaExtensionRequest() {
        return io.grpc.reflection.v1alpha.ServerReflectionRequest.newBuilder()
                .setFileContainingExtension(
                        io.grpc.reflection.v1alpha.ExtensionRequest.newBuilder()
                                .setContainingType(STRING_MESSAGE)
                                .setExtensionNumber(STRING_EXTENSION_NUMBER)
                                .build())
                .build();
    }

    private static void close(ManagedChannel channel) {
        channel.shutdownNow();
        try {
            channel.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
