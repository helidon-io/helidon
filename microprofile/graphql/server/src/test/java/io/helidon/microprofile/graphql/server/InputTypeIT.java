package io.helidon.microprofile.graphql.server;

import java.io.IOException;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import io.helidon.microprofile.graphql.server.test.types.SimpleContactInputType;
import io.helidon.microprofile.graphql.server.test.types.SimpleContactInputTypeWithAddress;
import io.helidon.microprofile.graphql.server.test.types.SimpleContactInputTypeWithName;
import io.helidon.microprofile.graphql.server.test.types.SimpleContactInputTypeWithNameValue;

import org.jboss.weld.junit5.WeldInitiator;
import org.jboss.weld.junit5.WeldJunit5Extension;
import org.jboss.weld.junit5.WeldSetup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(WeldJunit5Extension.class)
public class InputTypeIT extends AbstractGraphQLIT {

    @WeldSetup
    private final WeldInitiator weld = WeldInitiator.of(WeldInitiator.createWeld()
                                                                .addBeanClass(SimpleContactInputType.class)
                                                                .addBeanClass(SimpleContactInputTypeWithName.class)
                                                                .addBeanClass(SimpleContactInputTypeWithNameValue.class)
                                                                .addBeanClass(SimpleContactInputTypeWithAddress.class)
                                                                .addExtension(new GraphQLCdiExtension()));


    @Test
    public void testInputType() throws IOException {
        setupIndex(indexFileName, SimpleContactInputType.class, SimpleContactInputTypeWithName.class,
                   SimpleContactInputTypeWithNameValue.class, SimpleContactInputTypeWithAddress.class);
        ExecutionContext executionContext =  new ExecutionContext(defaultContext);
        Schema schema = executionContext.getSchema();
        assertThat(schema.getInputTypes().size(), is(5));
        assertThat(schema.containsInputTypeWithName("MyInputType"), is(true));
        assertThat(schema.containsInputTypeWithName("SimpleContactInputTypeInput"), is(true));
        assertThat(schema.containsInputTypeWithName("NameInput"), is(true));
        assertThat(schema.containsInputTypeWithName("SimpleContactInputTypeWithAddressInput"), is(true));
        assertThat(schema.containsInputTypeWithName("AddressInput"), is(true));
    }
}
