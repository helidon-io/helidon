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

package io.helidon.lra.coordinator.client.narayana;

import java.net.URI;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

import io.helidon.lra.coordinator.client.CoordinatorConnectionException;
import io.helidon.lra.coordinator.client.PropagatedHeaders;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NarayanaClientTest {

    @Test
    void parseBaseUriNormalizesDefaultPorts() {
        assertThat(NarayanaClient.parseBaseUri("http://example.com:80/lra-coordinator/1"),
                   is(URI.create("http://example.com/lra-coordinator")));
        assertThat(NarayanaClient.parseBaseUri("https://example.com:443/lra-coordinator/1"),
                   is(URI.create("https://example.com/lra-coordinator")));
    }

    @Test
    void parseBaseUriNormalizesCaseAndTrailingSlash() {
        assertThat(NarayanaClient.parseBaseUri("HTTP://EXAMPLE.COM:8080/lra-coordinator/1"),
                   is(URI.create("http://example.com:8080/lra-coordinator")));
        assertThat(NarayanaClient.parseBaseUri("http://example.com/lra-coordinator//1"),
                   is(URI.create("http://example.com/lra-coordinator")));
    }

    @Test
    void parseBaseUriRejectsDotSegments() {
        assertThrows(RuntimeException.class,
                     () -> NarayanaClient.parseBaseUri("http://example.com/other/../lra-coordinator/1"));
        assertThrows(RuntimeException.class,
                     () -> NarayanaClient.parseBaseUri("http://example.com/other/%2e%2e/lra-coordinator/1"));
    }

    @Test
    void validateCoordinatorNormalizesConfiguredCoordinator() {
        NarayanaClient client = client();

        assertThat(client.validateCoordinator(URI.create("HTTP://EXAMPLE.COM:80/lra-coordinator/1")),
                   is(Optional.empty()));
    }

    @Test
    void validateCoordinatorNormalizesAllowedCoordinator() {
        NarayanaClient client = client();
        client.allowCoordinator(URI.create("http://coordinator.example.com/lra-coordinator/"));

        assertThat(client.validateCoordinator(URI.create("HTTP://COORDINATOR.EXAMPLE.COM:80/lra-coordinator/1")),
                   is(Optional.empty()));
    }

    @Test
    void validateCoordinatorRejectsDotSegments() {
        NarayanaClient client = client();

        Optional<CoordinatorConnectionException> validationError = client.validateCoordinator(
                URI.create("http://example.com/other/%2e%2e/lra-coordinator/1"));

        assertThat(validationError.isPresent(), is(true));
        assertThat(validationError.get().status(), is(412));
    }

    @Test
    void coordinatorOperationsRejectForeignLraId() {
        NarayanaClient client = client();
        URI lraId = URI.create("http://foreign.example.com/lra-coordinator/1");

        assertPreconditionFailure(() -> client.cancel(lraId, PropagatedHeaders.noop()).await(1, TimeUnit.SECONDS));
        assertPreconditionFailure(() -> client.close(lraId, PropagatedHeaders.noop()).await(1, TimeUnit.SECONDS));
        assertPreconditionFailure(() -> client.status(lraId, PropagatedHeaders.noop()).await(1, TimeUnit.SECONDS));
    }

    private static NarayanaClient client() {
        NarayanaClient client = new NarayanaClient();
        client.init(() -> URI.create("http://example.com/lra-coordinator"), 1, TimeUnit.SECONDS);
        return client;
    }

    private static void assertPreconditionFailure(Runnable invocation) {
        CompletionException exception = assertThrows(CompletionException.class, invocation::run);

        assertThat(exception.getCause() instanceof CoordinatorConnectionException, is(true));
        assertThat(((CoordinatorConnectionException) exception.getCause()).status(), is(412));
    }
}
