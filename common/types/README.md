Types
----

Language types abstraction used during annotation processing (and instead of reflection if needed at runtime).
As types are required for annotation processors, they cannot be generated using annotation processors for builder.
To work around this cyclic dependency problem, there is a module `builder/tests/common-types` that contains the correct
blueprints and static methods to generate the code required for this module.
If a change is needed, generate the code using that module, and copy all the types (blueprint, static methods, and generated classes) here.