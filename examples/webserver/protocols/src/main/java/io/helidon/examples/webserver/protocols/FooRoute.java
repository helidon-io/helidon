package io.helidon.examples.webserver.protocols;

import java.util.function.Supplier;

import io.helidon.http.HttpPrologue;
import io.helidon.http.PathMatchers;
import io.helidon.webserver.http.Handler;
import io.helidon.webserver.http.HttpRoute;

public class FooRoute implements HttpRoute, Supplier<? extends HttpRoute> {
    @Override
    public PathMatchers.MatchResult accepts(HttpPrologue prologue) {
        return prologue.
    }

    @Override
    public Handler handler() {
        return null;
    }

    @Override
    public HttpRoute get() {
        return null;
    }
}
