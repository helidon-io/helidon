# Description

Resolves #issue_reference

Description of your PR - changes done.

# Checklist

// Please choose one of the following:

<details>
  <summary>Small change/bugfix</summary>
  
  * [ ] Followed the dev guidelines: https://github.com/oracle/helidon/blob/master/DEV-GUIDELINES.md
  * [ ] Updated copyrights of changed files. New files have the current year, old files have one or two years (`2019, 2021`)
  * [ ] Followed the code style of Helidon
  * [ ] Run checkstyle (`./etc/scripts/checkstyle.sh` or `mvn package -Pcheckstyle`) - will also run as part of pipeline
  * [ ] Run copyright plugin (`./etc/scripts/copyright.sh` or `mvn package -Pcopyright`) - will also run as part of pipeline
  * [ ] Run the spotbugs plugin (`mvn verify -Pspotbugs`) - will also run as part of pipeline
  * [ ] When changing dependency versions or adding dependencies, please expect some delay, as these are subject to approval 
</details>

<details>
  <summary>New feature</summary>
  
If something is not relevant for your PR, just check the box as processed.

* [ ] Followed the dev guidelines: https://github.com/oracle/helidon/blob/master/DEV-GUIDELINES.md
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

</details>
