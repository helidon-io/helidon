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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.helidon.webserver.Router;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.grpc.GrpcRouting;
import io.helidon.webserver.grpc.courses.CourseServiceGrpc;
import io.helidon.webserver.grpc.courses.Courses;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import com.google.protobuf.Empty;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
public class CourseServiceTest {
    private final int port;

    protected ManagedChannel channel;

    protected CourseServiceGrpc.CourseServiceBlockingStub blockingStub;

    protected CourseServiceGrpc.CourseServiceStub stub;

    CourseServiceTest(WebServer server) {
        this.port = server.port();
    }

    @SetUpRoute
    static void routing(Router.RouterBuilder<?> router) {
        router.addRouting(GrpcRouting.builder().service(new CourseService()));
    }

    @BeforeEach
    void beforeEach() {
        channel = ManagedChannelBuilder.forAddress("localhost", port)
                .usePlaintext()
                .build();
        blockingStub = CourseServiceGrpc.newBlockingStub(channel);
        stub = CourseServiceGrpc.newStub(channel);
    }

    @AfterEach
    void afterEach() throws InterruptedException {
        blockingStub = null;
        stub = null;
        channel.shutdown();
        if (!channel.awaitTermination(10, TimeUnit.SECONDS)) {
            System.err.println("Failed to terminate channel");
        }
        if (!channel.isTerminated()) {
            System.err.println("Channel is not terminated!!!");
        }
    }

    @Test
    void testGetCourses() throws InterruptedException {
        var observer = new TestObserver<Courses.CourseResponse>();
        CountDownLatch latch = observer.setLatch(1);
        stub.getCourses(Empty.getDefaultInstance(), observer);
        assertThat(latch.await(10, TimeUnit.SECONDS), is(true));
        assertThat(observer.getResponses().size(), is(1));
        assertThat(observer.getResponses().getFirst().getCoursesCount(), is(2));
    }

    @Test
    void testGetCoursesBlocking() {
        Courses.CourseResponse res = blockingStub.getCourses(Empty.getDefaultInstance());
        assertThat(res.getCoursesCount(), is(2));
    }
}
