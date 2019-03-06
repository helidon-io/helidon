/*
 * Copyright (c) 2016 Oracle and/or its affiliates. All rights reserved.
 */
package io.helidon.grpc.server;

/**
 * @author jk  2018.06.13
 */
public enum ClientRequestAttribute
    {
        METHOD_TYPE,
        METHOD_NAME,
        DEADLINE,
        COMPRESSOR,
        AUTHORITY,
        ALL_CALL_OPTIONS,
        HEADERS
    }
