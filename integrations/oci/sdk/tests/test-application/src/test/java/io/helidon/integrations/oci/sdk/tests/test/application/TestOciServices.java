/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.integrations.oci.sdk.tests.test.application;

import java.util.Optional;

import com.oracle.bmc.ailanguage.AIServiceLanguage;
import com.oracle.bmc.ailanguage.AIServiceLanguageAsync;
import com.oracle.bmc.ailanguage.AIServiceLanguageAsyncClient;
import com.oracle.bmc.ailanguage.AIServiceLanguageClient;
import com.oracle.bmc.circuitbreaker.OciCircuitBreaker;
import com.oracle.bmc.objectstorage.ObjectStorage;
import com.oracle.bmc.objectstorage.ObjectStorageAsync;
import com.oracle.bmc.objectstorage.ObjectStorageAsyncClient;
import com.oracle.bmc.objectstorage.ObjectStorageClient;
import com.oracle.bmc.streaming.Stream;
import com.oracle.bmc.streaming.StreamAdmin;
import com.oracle.bmc.streaming.StreamAdminClient;
import com.oracle.bmc.streaming.StreamAsync;
import com.oracle.bmc.streaming.StreamAsyncClient;
import com.oracle.bmc.streaming.StreamAsyncClientBuilder;
import com.oracle.bmc.streaming.StreamClient;
import com.oracle.bmc.streaming.StreamClientBuilder;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import static io.helidon.common.testing.junit5.OptionalMatcher.optionalEmpty;
import static io.helidon.common.testing.junit5.OptionalMatcher.optionalPresent;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * See {@code OciExtension} and {@code TestSpike} for a backgrounder.
 */
@Singleton
@SuppressWarnings("unused")
class TestOciServices {
    int postConstructCalls;

    // The service* parameters below are
    // arbitrary; pick another OCI service and
    // substitute the classes as appropriate
    // and this will still work (assuming of
    // course you also add the proper jar file
    // to this project's test-scoped
    // dependencies).
    @Inject Optional<AIServiceLanguage> serviceInterface;
    @Inject Optional<AIServiceLanguageClient> serviceClient;
    @Inject Optional<AIServiceLanguageClient.Builder> serviceClientBuilder;
    @Inject Optional<AIServiceLanguageAsync> serviceAsyncInterface;
    @Inject Optional<AIServiceLanguageAsyncClient> serviceAsyncClient;
    @Inject Optional<AIServiceLanguageAsyncClient.Builder> serviceAsyncClientBuilder;

    @Inject ObjectStorage objectStorage;
    @Inject ObjectStorageClient objectStorageClient;
    @Inject ObjectStorageClient.Builder objectStorageClientBuilder;
    @Inject ObjectStorageAsync objectStorageAsync;
    @Inject ObjectStorageAsyncClient objectStorageAsyncClient;
    @Inject ObjectStorageAsyncClient.Builder objectStorageAsyncClientBuilder;

    // This one is an example of something
    // that looks like a service client but
    // isn't; see the comments in the
    // constructor body below.  It should be
    // unsatisfied.
    @Inject Optional<OciCircuitBreaker> unresolvedJaxRsCircuitBreakerInstance;

    // Streaming turns out to be the only
    // convention-violating service in the
    // entire portfolio, and the violation is
    // extremely minor, and appears to be a
    // mistake.  Specifically, its root
    // subpackage features two main domain
    // objects (Stream, StreamAdmin) and only
    // one of them (StreamAdmin) fully follows
    // the service client pattern.  The other
    // one (Stream) features a builder class
    // that is not a nested class
    // (StreamClientBuilder) but maybe should
    // be.  We test this explicitly here
    // because, again, it is the only service
    // in the entire portfolio that breaks the
    // pattern.
    @Inject Stream streamingServiceInterface;
    @Inject StreamAdmin streamingAdminServiceInterface;
    @Inject StreamAdminClient streamingAdminServiceClient;
    @Inject StreamAdminClient.Builder streamingAdminServiceClientBuilder;
    @Inject StreamAsync streamingServiceAsyncInterface;
    @Inject StreamAsyncClient streamingServiceAsyncClient;
    @Inject StreamAsyncClientBuilder streamingServiceAsyncClientBuilder;
    @Inject StreamClient streamingServiceClient; // oddball
    @Inject StreamClientBuilder streamingServiceClientBuilder; // oddball

    @PostConstruct
    void verifyEverything() {
        postConstructCalls++;

        assertThat(serviceInterface, optionalPresent());
        assertThat(serviceClient, optionalPresent());
        assertThat(serviceClientBuilder, optionalPresent());
        assertThat(serviceAsyncInterface, optionalPresent());
        assertThat(serviceAsyncClient, optionalPresent());
        assertThat(serviceAsyncClientBuilder, optionalPresent());

        assertThat(objectStorage, notNullValue());
        assertThat(objectStorageClient, notNullValue());
        assertThat(objectStorageClientBuilder, notNullValue());
        assertThat(objectStorageAsync, notNullValue());
        assertThat(objectStorageAsyncClient, notNullValue());
        assertThat(objectStorageAsyncClientBuilder, notNullValue());

        assertThat(unresolvedJaxRsCircuitBreakerInstance, optionalEmpty());

        assertThat(streamingServiceInterface, notNullValue());
        assertThat(streamingAdminServiceInterface, notNullValue());
        assertThat(streamingAdminServiceClient, notNullValue());
        assertThat(streamingAdminServiceClientBuilder, notNullValue());
        assertThat(streamingServiceAsyncInterface, notNullValue());
        assertThat(streamingServiceAsyncClient, notNullValue());
        assertThat(streamingServiceAsyncClientBuilder, notNullValue());
        assertThat(streamingServiceClient, notNullValue());
        assertThat(streamingServiceClientBuilder, notNullValue());
    }

    void assertCalledOnce() {
        assertThat(postConstructCalls, is(1));
    }

}
