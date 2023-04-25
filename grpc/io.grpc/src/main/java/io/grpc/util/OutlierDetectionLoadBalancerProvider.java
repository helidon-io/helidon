package io.grpc.util;

import io.grpc.LoadBalancerProvider;

/**
 * A dummy class to allow the re-packaging of grpc-java
 * with a module-info.java file to work.
 * <p>
 * This file will be replaced by the real implementation from grpc-java
 * as part of the re-packaging process.
 */
public class OutlierDetectionLoadBalancerProvider
        extends LoadBalancerProvider {
}
