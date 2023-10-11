# OCI Secrets MP Config Source Example

This example project demonstrates the use of the `integrations/oci/oci-secrets-mp-config-source` library.

## Prerequisites

* Java 21+
* Maven 3.8+
* An [Oracle Cloud Infrastructure (OCI) account](https://www.oracle.com/cloud/).

## Overview

This example shows how to configure and use a Microprofile Config configuration source, via Helidon's Microprofile
Config meta-configuration feature, that can transparently fetch text values from an OCI vault resource. It exercises the
classes present in the `integrations/oci/oci-secrets-mp-config-source` project elsewhere in this source code repository.

## Setup Steps

Because this example makes use of Oracle Cloud, you will need to set up some Oracle Cloud resources before you begin,
along with a way to authenticate to your Oracle Cloud account.

### Create OCI Resources and Gather OCI Information

These steps should only need to be performed once.

0. Open a text document on your computer where you can save some important information related to OCI resources as you
   go along. This example will presume this document is located in `/tmp/helidon-examples-ocids.txt`.
1. Set up authentication information on your computer to allow this example to authenticate to your Oracle Cloud
   account.
    1. Create a [`${HOME}/.oci/config`] file with the [required entries for authenticating to your Oracle Cloud
       account](https://docs.oracle.com/en-us/iaas/Content/API/Concepts/sdkconfig.htm#SDK_and_CLI_Configuration_File). Note
       the [Required Keys and
       OCIDs](https://docs.oracle.com/en-us/iaas/Content/API/Concepts/apisigningkey.htm#Required_Keys_and_OCIDs)
       documentation.
    2. (There are many ways to authenticate to your Oracle Cloud account that are supported by Helidon. This is just one
       of them that is particularly easy to set up.)
    3. At a minimum, in this file you'll need to have `fingerprint`, `key_file`, `region`, `tenancy` and `user`
       [entries](https://docs.oracle.com/en-us/iaas/Content/API/Concepts/sdkconfig.htm#ariaid-title3).
2. [Log in to Oracle Cloud](https://cloud.oracle.com/).
3. Create a compartment to host OCI resources related to this example and gather its OCID.
    1. Go to https://cloud.oracle.com/identity/compartments
    2. Press the *Create Compartment* button.
    3. Supply a name for the compartment in the Name text field. This example uses "helidon-examples", but it can be any
       name you like that uses alphanumeric characters and the hyphen (`-`) character.
    4. Supply a description for the compartment in the Description text field. This example uses "A compartment for
       Helidon Examples" but it can be any text you like.
    5. Select a parent compartment for your new compartment from the Parent Compartment drop-down list. It will probably
       be your account's root compartment, and may already be selected for you.
    6. Press the *Create Compartment* button.
    7. Click on your new compartment's hyperlink that now appears in the Compartments table.
    8. In the Compartment Information tab, note the *OCID* item. Click on the "Copy" hyperlink to copy the Oracle Cloud
       Identifier (OCID) of your new compartment to your computer's clipboard.
    9. In `/tmp/helidon-examples-ocids.txt`, on a new line, type `compartment-ocid: ` and paste the just-copied OCID.
3. Create a vault to host secret information related to this example and gather its OCID
    1. Click on the three-dash "hamburger" menu in the upper left of the navigation bar. Click on *Identity and Security*
       from the resulting menu.
    2. Click on *Vault* in the page that results, under the *Key Management & Secret Management* section.
    3. On the left of the page, under the List scope section, select your new compartment from the drop down list.
    3. Press the *Create Vault* button.
    4. Supply a name for the vault in the Name text field. This example uses "helidon-examples-vault", but it can be any
       name you like that uses alphanumeric characters and the hyphen (`-`) character.
    5. Press the *Create Vault* button toward the bottom of the page.
    6. After several seconds, your new vault should appear in the table with a *State* of *Creating*.
    7. Click on your new vault's hyperlink.
    8. In the Vault Information tab, note the *OCID* item. Click on the "Copy" hyperlink to copy the OCID of your new
       vault to your computer's clipboard.
    9. In `/tmp/helidon-examples-ocids.txt`, on a new line, type `vault-ocid: ` and paste the just-copied OCID.
4. Create a master encryption key in your newly-created vault to encrypt secret information hosted in the vault.
    1. Press the *Create Key* button.
    2. Supply a name for the new master encryption key in the Name text field. This example uses "Helidon Examples Master
       Encryption Key" but it can be any text you like.
    3. Press the *Create Key* button toward the bottom of the page.
    4. After several seconds, your new master encryption key will appear in the table with a *State* of *Creating*.
5. Create a secret that will be used by this example.
    1. On the left side of the page, in the *Resources* section, click on *Secrets*.
    2. Press the *Create Secret* button.
    3. Supply a name for the secret in the Name text field. This example uses "helidon-examples-secret", but it can be any
       name you like that uses alphanumeric characters and the hyphen (`-`) character.
    4. Supply a description for the secret in the Description text field. This example uses "A secret for Helidon
       Examples" but it can be any text you like.
    5. Choose the Helidon Examples Master Encryption Key in the Encryption Key drop down (the master encryption key you
       just created).
    6. Supply a plain text value for the secret in the Secret Contents text area. This example uses "secret value", but it
       can be any text you like.
    7. Press the *Create Secret* button toward the bottom of the page.
### Edit The Example's Meta-Configuration to Reference OCI Resources

This example relies on a Helidon configuration feature known as _meta-configuration_, configuration about
configuration. Since this example is Microprofile-based, it specifically relies on Helidon's Microprofile Config
meta-configuration, which is represented by the `mp-meta-config.yaml` classpath resource. You can find this file in this
example under the `src/main/resources` directory. Editing this file properly to reference the OCI resources you just
created above makes the example work.

These steps should only need to be performed once.

1. Edit this example's `src/main/resources/mp-meta-config-yaml` file to include the OCIDs of the compartment and vault you created above.
    1. Replace the occurrence of `compartment-ocid: ${compartment-ocid}` with the contents of the line you added in
       `/tmp/helidon-examples-ocids.txt` above that begins with `compartment-ocid: `. Ensure the YAML indentation is correct
       (`compartment-ocid` should appear at zero-based character position 4, under the `c` in `sources`).
    2. Replace the occurrence of `vault-ocid: ${vault-ocid}` with the contents of the line you added in
       `/tmp/helidon-examples-ocids.txt` above that begins with `vault-ocid: `. Ensure the YAML indentation is correct
       (`vault-ocid` should appear at zero-based character position 4, under the `c` in `sources`).
    3. Note the `accept-pattern: ` line. Its quoted value is `^helidon-examples-secret$`, which is a regular expression
       identifying the names of configuration properties whose values should be fetched from the identically-named secret
       you just created. If you used a name for your new secret other than `helidon-examples-secret` above, replace
       `helidon-examples-secret` with the name of the newly-created secret.

The resulting file should look similar to this:

```
add-default-sources: false
add-discovered-converters: false
add-discovered-sources: false

sources:
  - type: 'environment-variables'
  - type: 'system-properties'
  - type: 'oci-secrets'
    accept-pattern: '^helidon-examples-secret$'
    compartment-ocid: ocid1.compartment.oc1... # the OCID of the compartment you created above
    lazy: false
    vault-ocid: ocid1.vault.oc1... # the OCID of the vault you created above
```

The file instructs the Helidon MicroProfile Config implementation:
* not to add any default or discovered configuration sources via the normal discovery mechanisms (this file will instead specify
  exactly what configuration sources will apply for this example application)
* not to add any discovered converters (this application does not require them)
* to add a configuration source of type `oci-secrets` with characteristics
  * It will attempt to fetch a value for any configuration property name that matches the `accept-pattern`'s regular
    expression value, and none other.
  * It will look in the vault identified by the (OCID) value of the `vault-ocid` setting, in the compartment identified
    by the (OCID) value of the `compartment-ocid` setting.
  * It will use a `NodeConfigSource`-based configuration source rather than a `LazyConfigSource`-based configuration
    source. See Helidon's documentation about configuration sources for more information.

## Build The Example

`mvn clean verify`

## Run The Example

`java -jar target/TODO.jar`
