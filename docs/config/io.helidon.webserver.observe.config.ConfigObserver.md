# io.helidon.webserver.observe.config.ConfigObserver

## Description

Configuration of Config Observer

## Configuration options

<style>
    code {
        white-space: nowrap !important;
    }
</style>

<table>
<thead>
<tr>
<th>Key</th><th>Type</th><th>Default Value</th><th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td><code>endpoint</code></td>
<td><code>String</code></td>
<td><code>config</code></td>
<td>Endpoint this observer is available on</td>
</tr>
<tr>
<td><code>permit-all</code></td>
<td><code>Boolean</code></td>
<td></td>
<td>Permit all access, even when not authorized</td>
</tr>
<tr>
<td><code>secrets</code></td>
<td><code>List&lt;String&gt;</code></td>
<td><code>.*password, .*passphrase, .*secret</code></td>
<td>Secret patterns (regular expressions) to exclude from output</td>
</tr>
</tbody>
</table>


## Usages

- [`server.features.observe.observers.config`](io.helidon.webserver.observe.spi.Observer.md#config)

---

See the [manifest](manifest.md) for all available types.
