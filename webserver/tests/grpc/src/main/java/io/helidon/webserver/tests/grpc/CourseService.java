/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

import io.helidon.webserver.grpc.GrpcService;
import io.helidon.webserver.grpc.courses.Courses;

import com.google.protobuf.Descriptors;
import com.google.protobuf.Empty;
import io.grpc.stub.StreamObserver;

class CourseService implements GrpcService {

    @Override
    public Descriptors.FileDescriptor proto() {
        return Courses.getDescriptor();
    }

    @Override
    public void update(Routing router) {
        router.unary("GetCourses", this::grpcGetCourses);
    }

    private void grpcGetCourses(Empty request, StreamObserver<Courses.CourseResponse> observer) {
        Courses.CourseResponse res = Courses.CourseResponse.newBuilder()
                .addCourses(Courses.Course.newBuilder().setName("First").build())
                .addCourses(Courses.Course.newBuilder().setName("Second").build())
                .build();
        observer.onNext(res);
        observer.onCompleted();
    }
}
