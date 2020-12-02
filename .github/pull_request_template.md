Resolves #xxxx

# Description
Description of your PR - changes done

# PR Checklist
If something is not valid for your PR, just check the box as processed.

* [ ] Add module to `bom/pom.xml`
* [ ] Version manage all third party dependencies used by users in `dependencies/pom.xml`
* [ ] Version manage all other third party dependencies in `pom.xml`
* [ ] Add an example to `examples` for new features
* [ ] Update documentation
* [ ] Documentation - do not forget guides using modified module
* [ ] Make sure module can be built using native-image (example in `examples` should be buildable; slack if in trouble)
* [ ] Update native image documentation under docs (docs/se/aot/01_introduction.adoc and docs/mp/aot/01_introduction.adoc)
* [ ] Add module (or update it) to `FeatureCatalog` - native, experimental
