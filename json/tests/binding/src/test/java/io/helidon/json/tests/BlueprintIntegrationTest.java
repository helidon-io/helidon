package io.helidon.json.tests;

import io.helidon.builder.api.Prototype;
import io.helidon.json.binding.JsonBinding;
import io.helidon.testing.junit5.Testing;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

@Testing.Test
public class BlueprintIntegrationTest {

    private final JsonBinding jsonBinding;

    BlueprintIntegrationTest(JsonBinding jsonBinding) {
        this.jsonBinding = jsonBinding;
    }

    @Test
    public void testBlueprintIntegration() {
        String expectedJson = "{\"firstName\":\"Test\",\"lastName\":\"Name\"}";
        MyTestPerson expectedPerson = MyTestPerson.builder().firstName("Test").lastName("Name").build();

        String json = jsonBinding.serialize(expectedPerson);
        assertThat(json, is(expectedJson));

        MyTestPerson actualPerson = jsonBinding.deserialize(json, MyTestPerson.class);
        assertThat(actualPerson, notNullValue());
        assertThat(actualPerson, is(expectedPerson));
    }

    @Prototype.Blueprint
    @Prototype.Annotated("io.helidon.json.binding.Json.Entity")
    interface MyTestPersonBlueprint {

        String firstName();

        String lastName();

    }

}
