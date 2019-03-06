package io.helidon.grpc.server;


import io.opentracing.Tracer;

import java.io.Serializable;


/**
 * Configuration class for the {@link GrpcServer} implementations.
 */
public class GrpcServerBasicConfig
        implements GrpcServerConfiguration, Serializable
    {
    // ---- constructors ------------------------------------------------

    /**
     * Default constructor for serialization.
     */
    // ToDo: (JK) Does this class need to be serializable, the Web Server config is not?
    public GrpcServerBasicConfig()
        {
        }

    /**
     * Construct {@link GrpcServerBasicConfig} instance with native transport and TLS disabled.
     *
     * @param name  the server name
     * @param port  the port to listen on
     */
    GrpcServerBasicConfig(String name, int port)
        {
        this(name, port, false, false, null, null, null, null);
        }

    /**
     * Construct {@link GrpcServerBasicConfig} instance.
     *
     * @param name            the server name
     * @param port            the port to listen on
     * @param nativeTransport {@code true} to enable native transport for
     *                        the server
     * @param tls             {@code true} to enable TLS for the server
     * @param tlsCert         the location of the TLS certificate file
     *                        (required if tls is enabled)
     * @param tlsKey          the location of the TLS key file (required if
     *                        tls is enabled)
     * @param tlsCaCert       the location of the optional TLS CA cert file
     */
    public GrpcServerBasicConfig(String name, int port, boolean nativeTransport, boolean tls, String tlsCert, String tlsKey, String tlsCaCert, Tracer tracer)
        {
        this.name = name;
        this.port = port;
        this.nativeTransport = nativeTransport;
        this.tls = tls;
        this.tlsCert = tlsCert;
        this.tlsKey = tlsKey;
        this.tlsCaCert = tlsCaCert;
        this.tracer = tracer;
        }

    // ---- accessors ---------------------------------------------------

    /**
     * Get the server name.
     *
     * @return the server name
     */
    @Override
    public String name()
        {
        return name;
        }

    /**
     * Get the server port.
     *
     * @return the server port
     */
    @Override
    public int port()
        {
        return port;
        }

    /**
     * Determine whether use native transport if possible.
     * <p/>
     * If native transport support is enabled, gRPC server will use epoll on
     * Linux, or kqueue on OS X. Otherwise, the standard NIO transport will
     * be used.
     *
     * @return {@code true} if native transport should be used
     */
    @Override
    public boolean useNativeTransport()
        {
        return nativeTransport;
        }

    /**
     * Determine whether TLS is enabled.
     *
     * @return {@code true} if TLS is enabled
     */
    @Override
    public boolean isTLS()
        {
        return tls;
        }

    /**
     * Obtain the location of the TLS certs file to use.
     *
     * @return the location of the TLS certs file to use
     */
    @Override
    public String tlsCert()
        {
        return tlsCert;
        }

    /**
     * Obtain the location of the TLS key file to use.
     *
     * @return the location of the TLS key file to use
     */
    @Override
    public String tlsKey()
        {
        return tlsKey;
        }

    /**
     * Obtain the location of the TLS CA certs file to use.
     *
     * @return the location of the TLS CA certs file to use
     */
    @Override
    public String tlsCaCert()
        {
        return tlsCaCert;
        }

    @Override
    public Tracer tracer()
        {
        return tracer;
        }

    // ---- data members ------------------------------------------------

    private String name;

    private int port;

    private boolean nativeTransport;

    private boolean tls;

    private String tlsCert;

    private String tlsKey;

    private String tlsCaCert;

    private Tracer tracer;
    }
