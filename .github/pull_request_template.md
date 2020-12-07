# Description

Resolves #issue_reference

Description of your PR - changes done.

// the following section can be removed for bugfixes
# New Feature Checklist
If something is not relevant for your PR, just check the box as processed.

* [ ] Add `module-info.java`
* [ ] If module provides service loader service(s), make sure it is both in `module-info.java` and in `META-INF/services`   
* [ ] Add module to `bom/pom.xml`
* [ ] Version manage all third party dependencies used by users in `dependencies/pom.xml`
* [ ] Version manage all other third party dependencies in `pom.xml`
* [ ] Add an example to `examples` that demonstrates the new feature
* [ ] Update documentation
* [ ] Documentation - make sure that existing documentation is still up-to-date (esp. guides)
* [ ] Make sure module can be built using native-image (example in `examples` should be buildable; slack if in trouble)
* [ ] Make sure module can be used in a modularized application   
* [ ] Update native image documentation under docs (docs/se/aot/01_introduction.adoc and docs/mp/aot/01_introduction.adoc)
* [ ] Add module (or update it) to `FeatureCatalog` - native, experimental
