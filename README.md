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
