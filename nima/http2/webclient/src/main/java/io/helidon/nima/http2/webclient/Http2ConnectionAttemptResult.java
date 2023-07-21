package io.helidon.nima.http2.webclient;

import io.helidon.nima.webclient.api.HttpClientResponse;

record Http2ConnectionAttemptResult(Result result,
                                    Http2ClientStream stream,
                                    HttpClientResponse response) {
    enum Result {
        HTTP_1,
        HTTP_2,
        UNKNOWN
    }
}
