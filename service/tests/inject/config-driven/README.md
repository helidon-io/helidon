Config bean tests
----

We need to handle combination of the following annotations:

- `@ConfigDriven.WantDefault`
- `@ConfigDriven.AtLeastOne`
- `@ConfigDriven.Repeatable`

The types handling these combinations:

- `AConfigBlueprint`: no annotation (just `@ConfigBean`)              zero or one configured instance
- `BConfigBlueprint`: `@WantDefault`                                  one or two instance (default and configured, if present and
  not default name)
- `CConfigBlueprint`: `@WantDefault`, `@AtLeastOne`                   two instances (default and configured, if not default name)
- `DConfigBlueprint`: `@WantDefault`, `@Repeatable`                   default instance + zero or more configured instances
- `EConfigBlueprint`: `@WantDefault`, `@AtLeastOne`, `@Repeatable`    default instance + at least one configured instance
- `FConfigBlueprint`: `@AtLeastOne`                                   fails without config, single instance if configured
- `GConfigBlueprint`: `@AtLeastOne`, `@Repeatable`                    at least one configured instance
- `HConfigBlueprint`: `@Repeatable`                                   zero or more configured instances

The configuration key is `config-a`, `config-b` etc.

In addition, there is `IConfigBlueprint`, `IService` and `IContract` that makes sure `@PostConstruct` is called correctly,
and that we can add another qualifier that will be honored. Also it does not have a declared constructor, to validate we can
create a config-driven bean even for such a case.