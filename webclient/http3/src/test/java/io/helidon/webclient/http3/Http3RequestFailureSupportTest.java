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

package io.helidon.webclient.http3;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import io.helidon.http.http3.Http3GoAway;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;

class Http3RequestFailureSupportTest {
    @Test
    void shouldPreserveExactSessionTerminationFailure() {
        CompletionException primary =
                new CompletionException("session termination failed", new IllegalStateException("transport failure"));
        RuntimeException secondary = new RuntimeException("token cleanup failed");
        primary.addSuppressed(secondary);
        CompletableFuture<Void> termination = new CompletableFuture<>();
        termination.completeExceptionally(primary);

        Throwable observed =
                Http3RequestFailureSupport.completionFailure(termination.minimalCompletionStage());

        assertThat(observed, sameInstance(primary));
        assertThat(observed.getSuppressed()[0], sameInstance(secondary));
    }

    @Test
    void shouldClassifyExplicitStreamRejectionAsRequestRejected() {
        Throwable cause = new IOException("stream reset");

        Throwable classified = Http3RequestFailureSupport.classify(cause, true, false, false);

        assertThat(classified, instanceOf(Http3RequestFailureSupport.RequestRejectedException.class));
        assertThat(classified.getCause(), sameInstance(cause));
        assertThat(Http3RequestFailureSupport.isRetryable(classified), is(true));
        assertThat(Http3RequestFailureSupport.isRetryableRequestFailure(classified), is(true));
    }

    @Test
    void shouldKeepConnectionCloseAmbiguous() {
        Throwable cause = new IOException("connection closed");

        Throwable classified = Http3RequestFailureSupport.classify(cause, false, false, false);

        assertThat(classified, sameInstance(cause));
        assertThat(Http3RequestFailureSupport.isRetryable(classified), is(false));
        assertThat(Http3RequestFailureSupport.isRetryableRequestFailure(classified), is(false));
    }

    @Test
    void shouldClassifyGoAwayRejectedStreamAsConnectionRetired() {
        Throwable cause = new IOException("goaway close");

        Throwable classified = Http3RequestFailureSupport.classify(cause, false, false, true);

        assertThat(classified, instanceOf(Http3RequestFailureSupport.ConnectionRetiredException.class));
        assertThat(classified.getCause(), sameInstance(cause));
        assertThat(Http3RequestFailureSupport.isRetryable(classified), is(true));
        assertThat(Http3RequestFailureSupport.isRetryableRequestFailure(classified), is(true));
        Http3GoAway goAway = Http3GoAway.requestStream(12);
        assertThat(Http3RequestFailureSupport.goAwayRejectsStream(goAway, 12), is(true));
        assertThat(Http3RequestFailureSupport.goAwayRejectsStream(goAway, 16), is(true));
    }

    @Test
    void shouldNotTreatAcceptedStreamOnRetiredConnectionAsRetryable() {
        Throwable cause = new IOException("graceful close");

        Throwable classified = Http3RequestFailureSupport.classify(cause, false, false, false);

        assertThat(classified, sameInstance(cause));
        assertThat(Http3RequestFailureSupport.isRetryable(classified), is(false));
        assertThat(Http3RequestFailureSupport.isRetryableRequestFailure(classified), is(false));
        assertThat(Http3RequestFailureSupport.goAwayRejectsStream(Http3GoAway.requestStream(12), 8), is(false));
        assertThat(Http3RequestFailureSupport.goAwayRejectsStream(null, 12), is(false));
    }

    @Test
    void shouldReuseAlreadyClassifiedFailures() {
        IllegalStateException cause = Http3RequestFailureSupport.connectionRetired();

        Throwable classified = Http3RequestFailureSupport.classify(cause, false, false, false);

        assertThat(classified, sameInstance(cause));
        assertThat(Http3RequestFailureSupport.isRetryable(classified), is(true));
        assertThat(Http3RequestFailureSupport.isRetryableRequestFailure(classified), is(true));
        assertThat(classified.getMessage(), equalTo("HTTP/3 connection no longer accepts new requests."));
    }

    @Test
    void shouldTreatWrappedClassifiedFailuresAsRetryable() {
        IllegalStateException cause = Http3RequestFailureSupport.connectionRetired();
        CompletionException wrapped = new CompletionException(cause);

        assertThat(Http3RequestFailureSupport.isRetryable(wrapped), is(true));
        assertThat(Http3RequestFailureSupport.isRetryableRequestFailure(wrapped), is(true));
    }

    @Test
    void shouldClassifyStreamVersionFallbackSeparatelyFromHttp3Retry() {
        Throwable cause = new IOException("version fallback");

        Throwable classified = Http3RequestFailureSupport.classify(cause, false, true, false);

        assertThat(classified, instanceOf(Http3RequestFailureSupport.VersionFallbackException.class));
        assertThat(classified.getCause(), sameInstance(cause));
        assertThat(Http3RequestFailureSupport.isRetryable(classified), is(true));
        assertThat(Http3RequestFailureSupport.isRetryableRequestFailure(classified), is(false));
        assertThat(Http3RequestFailureSupport.isVersionFallback(classified), is(true));
    }

    @Test
    void shouldExposeExplicitAttemptDisposition() {
        IOException beforeCommit = new IOException("before commit");
        Http3RequestFailureSupport.RequestAttemptException notProcessed =
                Http3RequestFailureSupport.attemptFailure(beforeCommit,
                                                          Http3RequestFailureSupport.AttemptDisposition.NOT_PROCESSED,
                                                          false);

        assertThat(Http3RequestFailureSupport.attemptDisposition(notProcessed),
                   is(Http3RequestFailureSupport.AttemptDisposition.NOT_PROCESSED));
        assertThat(Http3RequestFailureSupport.attemptCause(notProcessed), sameInstance(beforeCommit));
        assertThat(Http3RequestFailureSupport.sessionReusable(notProcessed), is(false));
        assertThat(Http3RequestFailureSupport.endpointFailure(notProcessed), is(false));

        Http3RequestFailureSupport.RequestAttemptException localFailure =
                Http3RequestFailureSupport.attemptFailure(beforeCommit,
                                                          Http3RequestFailureSupport.AttemptDisposition.NOT_PROCESSED,
                                                          true);
        assertThat(Http3RequestFailureSupport.sessionReusable(localFailure), is(true));
        assertThat(Http3RequestFailureSupport.sessionReusable(new CompletionException(localFailure)), is(true));

        IOException afterCommit = new IOException("after commit");
        Http3RequestFailureSupport.RequestAttemptException possiblyProcessed =
                Http3RequestFailureSupport.attemptFailure(afterCommit,
                                                          Http3RequestFailureSupport.AttemptDisposition.POSSIBLY_PROCESSED,
                                                          false);

        assertThat(Http3RequestFailureSupport.attemptDisposition(possiblyProcessed),
                   is(Http3RequestFailureSupport.AttemptDisposition.POSSIBLY_PROCESSED));
        assertThat(Http3RequestFailureSupport.attemptCause(possiblyProcessed), sameInstance(afterCommit));
    }

    @Test
    void shouldPreserveCallerSelectedAttemptDisposition() {
        Throwable rejected = Http3RequestFailureSupport.classify(new IOException("stream rejected"), true, false, false);

        Http3RequestFailureSupport.RequestAttemptException failure =
                Http3RequestFailureSupport.attemptFailure(
                        rejected,
                        Http3RequestFailureSupport.AttemptDisposition.POSSIBLY_PROCESSED,
                        false);

        assertThat(Http3RequestFailureSupport.attemptDisposition(failure),
                   is(Http3RequestFailureSupport.AttemptDisposition.POSSIBLY_PROCESSED));
        assertThat(Http3RequestFailureSupport.attemptCause(failure), sameInstance(rejected));
    }

    @Test
    void shouldIdentifyOnlyExplicitEndpointFailures() {
        IOException cause = new IOException("handshake failed");
        Http3RequestFailureSupport.RequestAttemptException failure =
                Http3RequestFailureSupport.attemptFailure(
                        Http3RequestFailureSupport.endpointUnavailable(cause),
                        Http3RequestFailureSupport.AttemptDisposition.NOT_PROCESSED,
                        false);

        assertThat(Http3RequestFailureSupport.endpointFailure(failure), is(true));
        assertThat(Http3RequestFailureSupport.endpointFailure(new CompletionException(failure)), is(true));
        assertThat(Http3RequestFailureSupport.attemptCause(failure), sameInstance(cause));
        assertThat(Http3RequestFailureSupport.endpointFailure(cause), is(false));

        Http3RequestFailureSupport.RequestAttemptException localFailure =
                Http3RequestFailureSupport.attemptFailure(
                        new IllegalStateException("local failure"),
                        Http3RequestFailureSupport.AttemptDisposition.NOT_PROCESSED,
                        false);
        assertThat(Http3RequestFailureSupport.endpointFailure(localFailure), is(false));
    }

    @Test
    void shouldTreatStreamRejectionAsNotProcessedAfterCommit() {
        Throwable rejected = Http3RequestFailureSupport.classify(new IOException("stream rejected"), true, false, false);
        Http3RequestFailureSupport.RequestAttemptException failure =
                Http3RequestFailureSupport.attemptFailure(
                        rejected,
                        Http3RequestFailureSupport.AttemptDisposition.NOT_PROCESSED,
                        false);

        assertThat(Http3RequestFailureSupport.attemptDisposition(failure),
                   is(Http3RequestFailureSupport.AttemptDisposition.NOT_PROCESSED));
    }
}
