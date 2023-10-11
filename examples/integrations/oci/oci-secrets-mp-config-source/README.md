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

1. Create a new, empty `${HOME}/.oci/config` file whose contents you will fill in shortly. This example presumes that
   you do not already have one. If you do have one, you may need to edit it carefully rather than following these
   instructions.

    1. (There are many ways to authenticate to your Oracle Cloud account that are supported by Helidon. This is just one
       of them that is particularly easy to set up, which is why it is used in this example.)

2. [Log in to Oracle Cloud](https://cloud.oracle.com/) (open this link in a new brower tab or window).

3. Create a user for this example if needed.

    1. Click on the three-dash "hamburger" menu in the upper left of the navigation bar. Click on *Identity and Security*
       from the resulting menu.

    2. Click on Users under the **Identity** section.

    3. Press the **Create User** button.

    4. Supply a name for the user in the Name text field. This example uses "helidon-examples-user", but it can be any
       name you like that conforms to OCI's valid character requirements.

    5. Supply a description for the user in the Description text field. This example uses "A user for Helidon examples",
       but it can be any text you like.

    5. Press the **Create** button.

    4. Fill in the contents of your `${HOME}/.oci/config` file with your new user's information.

        1. Click on **API Keys** in the Resources section.

        2. Press the **Add API Key** button.

        3. Press the **Download Private Key** button and take note of where the file is saved.

        4. Press the **Download Public Key** button and take note of where the file is saved.

        5. Press the **Add** button.

        6. Take note of the contents of the Configuration File Preview text area. This will become part of the contents
           of your `${HOME}/.oci/config` file.

        7. Click the "Copy" hyperlink to copy the contents of the Configuration File Preview text area to your
           computer's clipboard.

        8. Paste the copied contents into the currently empty `${HOME}/.oci/config` file you just created.

    9. Locate the private key file that you downloaded. Rename it to `${HOME}/.oci/oci_api_key.pem` and adjust its
       permissions so that it is read-only and only you can read it.

    10. Locate the public key file that you downloaded. Rename it to `${HOME}/.oci/oci_api_key_public.pem`.

    11. In your now non-empty `${HOME}/.oci/config` file, replace the occurrence of `<path to your private keyfile>
       #TODO` with `~/.oci/oci_api_key.pem`.

4. Create a group for your new user.

    1. Click on the three-dash "hamburger" menu in the upper left of the navigation bar. Click on *Identity and Security*
       from the resulting menu.

    2. Click on Groups under the **Identity** section.

    3. Press the **Create Group** button.

    4. Supply a name for the group in the Name text field. This example uses "helidon-examples-group", but it can be any
       name you like that conforms to OCI's valid character requirements.

    5. Supply a description for the user in the Description text field. This example uses "A group for Helidon
       examples", but it can be any text you like.

    6. Press the **Create** button.

    7. Press the **Add User to Group** button.

    8. Select your new user from the Users drop-down list.

    9. Press the **Add** button.

5. Create a compartment to host OCI resources related to this example and gather its OCID.

    1. Go to https://cloud.oracle.com/identity/compartments

    2. Press the **Create Compartment** button.

    3. Supply a name for the compartment in the Name text field. This example uses "helidon-examples", but it can be any
       name you like that conforms to OCI's valid character requirements.

    4. Supply a description for the compartment in the Description text field. This example uses "A compartment for
       Helidon Examples" but it can be any text you like.

    5. Select a parent compartment for your new compartment from the Parent Compartment drop-down list. It will probably
       be your account's root compartment, and may already be selected for you.

    6. Press the **Create Compartment** button.

    7. Click on your new compartment's hyperlink that now appears in the Compartments table.

    8. In the Compartment Information tab, note the *OCID* item. Click on the "Copy" hyperlink to copy the Oracle Cloud
       Identifier (OCID) of your new compartment to your computer's clipboard.

    9. In `/tmp/helidon-examples-ocids.txt`, on a new line, type `compartment-ocid: ` and paste the just-copied OCID.

6. Create a vault to host secret information related to this example and gather its OCID.

    1. Click on the three-dash "hamburger" menu in the upper left of the navigation bar. Click on *Identity and Security*
       from the resulting menu.

    2. Click on *Vault* in the page that results, under the *Key Management & Secret Management* section.

    3. On the left of the page, under the List scope section, select your new compartment from the drop-down list.

    4. Press the **Create Vault** button.

    5. Supply a name for the vault in the Name text field. This example uses "helidon-examples-vault", but it can be any
       name you like that conforms to OCI's valid character requirements.

    6. Press the **Create Vault** button toward the bottom of the page.

    7. After several seconds, your new vault should appear in the table with a **State** of **Creating**.

    8. Click on your new vault's hyperlink.

    9. In the Vault Information tab, note the *OCID* item. Click on the "Copy" hyperlink to copy the OCID of your new
       vault to your computer's clipboard.

    10. In `/tmp/helidon-examples-ocids.txt`, on a new line, type `vault-ocid: ` and paste the just-copied OCID.

7. Create a master encryption key in your newly-created vault to encrypt secret information hosted in the vault.

    1. Press the **Create Key** button.

    2. Supply a name for the new master encryption key in the Name text field. This example uses "Helidon Examples Master
       Encryption Key" but it can be any text you like.

    3. Press the **Create Key** button toward the bottom of the page.

    4. After several seconds, your new master encryption key will appear in the table with a *State* of *Creating*.

8. Create a secret that will be used by this example.

    1. On the left side of the page, in the *Resources* section, click on *Secrets*.

    2. Press the **Create Secret** button.

    3. Supply a name for the secret in the Name text field. This example uses "helidon-examples-secret", but it can be any
       name you like that conform's to OCI's valid character requirements.

    4. Supply a description for the secret in the Description text field. This example uses "A secret for Helidon
       Examples" but it can be any text you like.

    5. Choose the Helidon Examples Master Encryption Key in the Encryption Key drop-down list (the master encryption key
       you just created).

    6. Supply a plain text value for the secret in the Secret Contents text area. This example uses "secret value", but it
       can be any text you like.

    7. Press the **Create Secret** button toward the bottom of the page.

9. Create a policy to allow your new user in your new group to read your new secret in your new vault.

    1. Click on the three-dash "hamburger" menu in the upper left of the navigation bar. Click on *Identity and Security*
       from the resulting menu.

    2. Click on Policies under the **Identity** section.

    3. Press the **Create Policy** button.

    4. Supply a name for this new policy in the Name text field. This example uses
       "helidon-examples-group-manipulate-secrets", but it can be any name you like that conforms to OCI's valid
       character requirements.

    5. Supply a description for this new policy in the Description text field. This example uses "Permits
       helidon-examples-group members to manipulate secrets in the helidon-examples compartment" but it can be any text
       you like.

    6. Select your new compartment from the Compartment drop-down list.

    7. In the **Policy Builder** area, select "Key and Secret Management" from the Policy use cases drop-down list.

    8. In the **Policy Builder** area, select "Let users read, update and rotate all secrets" from the Common policy
       template drop-down list.

    9. In the **Policy Builder** area, select your new group from the Groups drop-down list.

    10. In the **Policy Builder** area, select your new compartment from the Location drop-down list.

    11. Observe that the **Policy Statements** area now contains a statement like "Allow group
        **helidon-examples-group** to use secret-family in **compartment helidon-examples**".

    12. Press the **Create** button toward the bottom of the page.

To recap, you have, in dependency order:

* Created a user ("helidon-examples")

* Created a `${HOME}/.oci/config` file with that user's authentication information

* Created a group ("helidon-examples-group")

* Created a compartment ("helidon-examples")

* Created a vault in the compartment ("helidon-examples-vault")

* Created a secret in the vault ("helidon-examples-secret")

* Created a policy that lets your group's users manipulate secrets found in your compartment ("helidon-examples-group-manipulate-secrets")

You should be ready to go.

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
    accept-pattern: '^helidon-examples-secret$' # note the name of the secret you created above
    compartment-ocid: ocid1.compartment.oc1... # the OCID of the compartment you created above
    lazy: false
    vault-ocid: ocid1.vault.oc1... # the OCID of the vault you created above
```

The file instructs the Helidon MicroProfile Config implementation:
* not to add any default or discovered configuration sources via the normal discovery mechanisms (this file will instead specify
  exactly what configuration sources will apply for this example application)
* not to add any discovered converters (this application does not require them)
* to add the `environment-variables` configuration source first
* to add the `system-properties` configuration source second
* to add a configuration source of type `oci-secrets` third, with characteristics:
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
