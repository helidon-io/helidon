package io.helidon.nima.common.tls;

import java.util.function.Consumer;

import io.helidon.builder.api.RuntimeType;
import io.helidon.common.config.Config;
import io.helidon.common.config.NamedService;

@RuntimeType.PrototypedBy(TlsManagerConfig.class)
public interface TlsManager extends NamedService, RuntimeType.Api<TlsManagerConfig> {

    static TlsManager create() {
        return builder().build();
    }

    static TlsManager create(Config config) {
        return builder().config(config).build();
    }

    static TlsManager create(TlsManagerConfig config) {
        // TODO: create the default TlsManager impl
        throw new UnsupportedOperationException();
    }

    static TlsManagerConfig.Builder builder() {
        return TlsManagerConfig.builder();
    }

    static TlsManager create(Consumer<TlsManagerConfig.Builder> consumer) {
        var builder = TlsManagerConfig.builder();
        consumer.accept(builder);
        return builder.build();
    }


    void reload();
    void register(Consumer<Tls> tlsConsumer);
    Tls tls();

}
