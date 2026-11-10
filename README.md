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
Use `/fallbackrouter reload` after changing `config.yml`.

## Regex source selectors

Prefix a source selector with `regex:` to use a Java regular expression.
Regexes are supported for keys in `fallbacks` and list items in
`fallback-per-servers`. Quote selectors with YAML single quotes so backslashes
are preserved. Names without the prefix are always literal, even if they
contain characters such as `.` or `*`.

```yaml
fallback-per-servers:
  lobby:
    - 'regex:^survival-[0-9]+$'
    - creative
  backup:
    - 'regex:^survival-[0-9]+$'

fallbacks:
  survival-1: [special-lobby]  # Exact exception.
  'regex:^minigame-.*$': [games-lobby, lobby]
```

`survival-2` tries `lobby`, then `backup`; `survival-1` tries only
`special-lobby`; `minigame-bedwars` tries `games-lobby`, then `lobby`.

Resolution priority is independent of section order:

1. An exact `fallbacks` entry wins, including an explicit empty list.
2. Otherwise, matching regex entries in `fallbacks` are combined in YAML order,
   keeping the first occurrence of each destination. If any direct regex
   matches, inverse rules are not used, even if the resulting list is empty.
3. Otherwise, matching `fallback-per-servers` destinations are tried in YAML
   key order. Exact and regex source selectors can be mixed in the same list;
   a destination is tried only once.

Matching is case-sensitive and covers the **entire** source name. Use `.*` when
needed; optional Java regex flags such as `(?i)` are supported. Patterns are
validated and compiled when the configuration is loaded, then matched when a
source server needs a fallback, including servers registered later. Empty or
invalid regexes stop configuration loading with an error naming the section.
Destination values are always concrete Velocity server names: regexes do not
expand destination lists. No matching rule means no fallback destinations.

## Reloading configuration

Run `/fallbackrouter reload` in game with the `fallbackrouter.reload` permission,
or `fallbackrouter reload` from the proxy console. The command reloads
`config.yml`, regex selectors, and language files from the plugin's `lang/`
directory. Command replies and routing messages are translated in English and
German. Missing keys fall back first to the bundled selected language, then to
English, so existing language files automatically get new message defaults
without being overwritten. Technical exception details in console remain diagnostic text.
Rules and messages are replaced together only after loading succeeds. If YAML,
regex, or language loading fails, the previous settings remain active and the
console reports the error. Reload does not move currently connected players.
A new plugin JAR still requires a proxy restart.
