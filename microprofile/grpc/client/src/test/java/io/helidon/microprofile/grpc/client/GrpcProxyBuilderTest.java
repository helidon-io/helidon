package io.helidon.microprofile.grpc.client;

import java.util.Collection;
import java.util.concurrent.CompletionStage;

import io.helidon.microprofile.grpc.core.Unary;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class GrpcProxyBuilderTest {
    @Test
    public void shouldCreateProxyForMethodWithWithNestedGenerics() {
        TestService service = GrpcProxyBuilder.create(TestService.class).build();
        assertThat(service, is(notNullValue()));
    }

    public interface TestService {
       @Unary
       CompletionStage<Collection<String>> getBooks();
    }
}
