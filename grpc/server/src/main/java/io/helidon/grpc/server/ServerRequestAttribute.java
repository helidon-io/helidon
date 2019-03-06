/*
 * Copyright (c) 2016 Oracle and/or its affiliates. All rights reserved.
 */
package io.helidon.grpc.server;

/**
 * An enum representing different types of gRPC request
 * attribute that can be added to tracing logs.
 *
 * @author jk  2018.06.13
 */
public enum ServerRequestAttribute
    {
    /**
     * Log the request headers.
     */
    HEADERS,

    /**
     * Log the method type.
     */
    METHOD_TYPE,

    /**
     * log the method name.
     */
    METHOD_NAME,

    /**
     * log the call attributes.
     */
    CALL_ATTRIBUTES
    }
