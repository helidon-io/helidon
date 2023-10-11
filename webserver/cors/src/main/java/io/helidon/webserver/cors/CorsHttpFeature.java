package io.helidon.webserver.cors;

import io.helidon.common.Weighted;
import io.helidon.webserver.http.HttpFeature;
import io.helidon.webserver.http.HttpRouting;

class CorsHttpFeature implements HttpFeature, Weighted {
    private final double weight;
    private final CorsSupport corsSupport;

    CorsHttpFeature(double weight, CorsSupport corsSupport) {
        this.weight = weight;
        this.corsSupport = corsSupport;
    }

    @Override
    public void setup(HttpRouting.Builder routing) {
        routing.register(corsSupport);
    }

    @Override
    public double weight() {
        return weight;
    }
}
