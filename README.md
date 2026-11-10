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
- 🔒 Immediate kick-event redirects with ordered connection retries

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

## Immediate fallback handling

For server shutdowns, timeouts, and network failures, the plugin sets
`KickedFromServerEvent.RedirectPlayer` before Velocity processes its fallback
result. It no longer waits two seconds, pings destinations, or starts a separate
connection after Velocity's default redirect. The actual destination connection
is attempted immediately; network connection timeouts may still take time.

If a destination rejects or fails the connection, the next kick event tries the
next destination from the original source's list. Unknown servers, repeated
destinations, and the failed source itself are skipped, so a catch-all rule such
as `fallback-secondary: ['regex:.*']` cannot redirect `fallback-secondary` back to itself. Once the configured
chain is exhausted, the player is disconnected with `fallback.all_offline`.
Attempt state is cleared on successful connection or player disconnect. A chain
already in progress keeps its original rules and message across a reload.

With the kick filter omitted, sources with no destinations and unrelated kicks retain
Velocity's original handling. A failed voluntary switch while the player still
has a current server also retains Velocity's handling. This plugin does not
replace the proxy's initial login server selection. Other plugins that modify
kick results may affect routing.

## Filtering kick reasons

Velocity exposes an optional Adventure Component as the kick reason, not a
shutdown/ban enum. Backends and plugins can supply custom text; translatable
components may contain translation keys instead of displayed text.
Configure `kick-reasons` to classify your own messages:

```yaml
kick-reasons:
  whitelist: true
  fallback-on-no-reason: true
  reasons:
    - server_shutdown
    - 'Server closed'
    - 'Restart programmato'
    - 'regex:manutenzione|riavvio'
```

With `whitelist: true`, only matching non-empty reasons permit fallback.
With `false`, matching reasons forbid fallback and all other non-empty reasons
permit it. For example, a blacklist can contain `banned`, `punishment` and
`regex:sei stato (bannato|espulso)`. A blacklist cannot distinguish an unknown
punishment text from a custom shutdown; a whitelist is preferable when only
known shutdown messages should be recovered.

Plain strings match substrings without case sensitivity. `regex:` entries use
Java regex **search**, also case-insensitive; use `^` and `$` for an entire-reason
match. Matching uses plain component text (including untranslated keys where
present), ignoring component styling and legacy section-sign color codes. The
original Component is retained for a denied kick's disconnect message.

`fallback-on-no-reason` independently controls absent, empty or whitespace-only
reasons, commonly used for network failures; it applies in either list mode.
Whitelist plus `reasons: []` denies all textual reasons; blacklist plus an empty
list allows all textual reasons. A configured section requires a string list;
invalid lists, boolean types or regexes reject loading/reload atomically.

When this section is present, a denied reason explicitly disconnects with the
original reason, preventing Velocity's default fallback as well. The filter also
applies to failures during an active fallback chain, so a punishment on a
fallback destination cannot be bypassed by trying the next server. Failed
voluntary switches while still on a healthy current server retain native handling.
An ongoing chain keeps its original destination list but uses the latest kick
filter after reload, allowing punishment exclusions to take effect immediately.

If the section is omitted, the previous built-in shutdown/timeout matching and
retry behavior are preserved. New default configs include a commented example;
existing files are never rewritten. Apply changes with `/fallbackrouter reload`.

## Queue plugins that skip a fallback server

When the queue plugin redirects a connection from a queue server such as `fallback-default` to a
lobby, FallbackRouter records the selected destination in `ServerPreConnectEvent`.
A successful skip is left intact. If that replacement fails, the original
configured fallback is retried directly once before advancing the chain.
This preserves the limbo when a lobby goes offline between the queue plugin's checks
and its connection attempt, without globally disabling queue-server skipping.

The direct recovery attempt restores the original target after normal-priority
pre-connect handlers. Cancellation results are respected, and normal/manual
connections with no active fallback chain are untouched. If the original target
also fails, the next configured fallback is tried; the direct retry is bounded
so a stale queue check cannot cause an endless loop. Kick-reason restrictions
still apply before recovery, including punishment exclusions. Recovery state
is cleared on connection success or disconnect. No the queue plugin dependency is required.
Other plugins with later/same-priority handlers may still affect the final target.

## Expected connection-refused diagnostics

On the standard Velocity Log4j backend, connection-refused stack traces for
active FallbackRouter-owned attempts are replaced with a short translated warning
naming the actual destination after queue-plugin redirection. Full exception
details remain available at DEBUG level on the plugin logger.
Only Velocity's specific ConnectedPlayer connection-error message, a refused
connection exception, a matching player UUID and an active matching destination
are eligible. Unrelated/manual connections, other exceptions and other loggers
keep their original diagnostics. This changes logging only, not retry behavior.
The filter is removed on proxy shutdown. It depends on Velocity's provided
Log4j Core backend; if the backend or log format is incompatible, the original
error remains visible. Log4j is not bundled or replaced by the plugin.
