Config bean tests
----

We need to handle combination of the following annotations:

- `@ConfigDriven.WantDefault`
- `@ConfigDriven.AtLeastOne`
- `@ConfigDriven.Repeatable`

The types handling these combinations:

- `AConfigBlueprint`: no annotation (just `@ConfigBean`)
- `BConfigBlueprint`: `@WantDefault`
- `CConfigBlueprint`: `@WantDefault`, `@AtLeastOne`
- `DConfigBlueprint`: `@WantDefault`, `@Repeatable`
- `EConfigBlueprint`: `@WantDefault`, `@AtLeastOne`, `@Repeatable`
- `FConfigBlueprint`: `@AtLeastOne`
- `GConfigBlueprint`: `@AtLeastOne`, `@Repeatable`
- `HConfigBlueprint`: `@Repeatable`

The configuration key is `config-a`, `config-b` etc.

In addition there is `IConfigBlueprint`, `IService` and `IContract` that makes sure `@PostConstruct` is called correctly,
 and that we can add another qualifier that will be honored. Also it does not have a declared constructor, to validate we can
create a config-driven bean even for such a case.