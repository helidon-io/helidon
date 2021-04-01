package io.helidon.integrations.oci.telemetry;

class OciMetricsImpl implements OciMetrics {
    private final OciMetricsRx delegate;

    OciMetricsImpl(OciMetricsRx delegate) {
        this.delegate = delegate;
    }

    @Override
    public PostMetricData.Response postMetricData(PostMetricData.Request request) {
        return delegate.postMetricData(request).await();
    }
}
