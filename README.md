# FallbackRouter

A Velocity plugin that automatically redirects players to fallback servers if their current server goes offline. Includes full support for multiple languages and configurable fallback chains.

## ✨ Features

- 🔄 Automatically redirects players when disconnected from a backend server
- 🌍 Multi-language support (EN, DE included; easily extendable)
- ⚙️ Fully configurable `config.yml`:
  - Set default language
  - Define fallback chains per server
- 🧩 Modular codebase:
  - `FallbackRouterConfig` handles YAML
  - `Messages` manages translations and placeholders
- 🔒 Graceful fallback handling with ping checks and delay

## 📁 Example `config.yml`

```yaml
language: "en"

fallbacks:
  source-a:
    - fallback-a
    - lobby
  source-b:
    - fallback-b
    - lobby
```

## Inverse fallback configuration

The existing `fallbacks` format remains supported without changes. You can also
use `fallback-per-servers`: each key is a destination fallback server, and its
list contains the source servers whose players should be routed there.

This inverse configuration is equivalent to the example above:

```yaml
language: "en"

fallback-per-servers:
  fallback-a: [source-a]
  fallback-b: [source-b]
  lobby: [source-a, source-b]
```

For `source-a`, the plugin tries `fallback-a`, then `lobby`. For `source-b`, it tries `fallback-b`,
then `lobby`. Destination keys are processed from top to bottom; repeated source
names under the same destination do not add duplicate attempts. Use the server
names registered in Velocity.

Both formats can be combined. An explicit `fallbacks` entry **replaces** the
inverse list for that source, regardless of the order of the two sections:

```yaml
fallback-per-servers:
  lobby: [source-a, source-b, creative]
  backup: [source-a, source-b, creative]

fallbacks:
  source-a: [fallback-a, lobby] # Explicit exception: only these destinations are tried.
  creative: []       # Disable fallback destinations for this source.
```

Here `source-b` uses `lobby`, then `backup`. To use only the inverse format, omit
`fallbacks` or set it to `{}`. Empty maps (`fallbacks: {}` or
`fallback-per-servers: {}`) are allowed; destination entries require YAML lists.
At least one of the two sections is required.

Existing configuration files are never rewritten. New installations receive
the original fallback rules plus a commented example of the inverse format.
Restart the proxy after changing `config.yml`.
