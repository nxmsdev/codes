# Codes

A flexible promotional/redeem codes plugin for PaperMC (Minecraft 1.21.1) with item/permission/rank rewards, per-code limits, per-code delay and optional global announcements.

## Features

- Create and redeem promo codes
- Reward types:
    - Items (from hand, or by material + amount)
    - Permissions (LuckPerms supported; fallback to temporary attachment if LP missing)
    - Ranks/Groups via LuckPerms (requires LuckPerms)
- Usage limits:
    - Global uses limit (server-wide)
    - Per-player uses limit
    - `0` = unlimited for a given limit
- Per-code delay (cooldown) between uses by the same player
    - Supported formats: `10s`, `1m`, `1m10s`, `1h`, `1d`, `1d2h3m30s` (also plain number = seconds)
- Optional global chat announcement per code (does not reveal the code)
- Used/expired codes are automatically moved to the “used codes” list
- Language support (Polish / English) selectable in `config.yml`
- Messages stored in separate files: `messages_pl.yml` and `messages_en.yml`, active file is `messages.yml`

## Permissions

| Permission | Description |
|:-|:-|
| `codes.player` | Allows redeeming codes |
| `codes.admin` | Allows managing codes (create/overwrite/delete/list/info/reload) |

## Commands

Main command is `/code` with alias: `/kod`.

### English commands (recommended: `/code`)

| Command | Description |
|:-|:-|
| `/code help` | Shows player help |
| `/code help admin` | Shows admin help |
| `/code <name>` | Redeems a code |
| `/code create <name> <global_uses> <player_uses> <delay> <announce> <reward>` | Creates a new code |
| `/code overwrite <name> <global_uses> <player_uses> <delay> <announce> <reward>` | Overwrites an existing code |
| `/code delete <name>` | Deletes a code |
| `/code list active` | Lists active codes |
| `/code list used` | Lists used/expired codes |
| `/code list used clear` | Clears used codes list |
| `/code info <name>` | Shows detailed info about a code |
| `/code reload` | Reloads config and messages |

### Polish commands (recommended: `/kod`)

| Komenda | Opis |
|:-|:-|
| `/kod pomoc` | Wyświetla pomoc gracza |
| `/kod pomoc admin` | Wyświetla pomoc admina |
| `/kod <nazwa>` | Wykorzystuje kod |
| `/kod stworz <nazwa> <użycia_ogólne> <użycia_gracza> <opóźnienie> <ogłoszenie> <nagroda>` | Tworzy nowy kod |
| `/kod nadpisz <nazwa> <użycia_ogólne> <użycia_gracza> <opóźnienie> <ogłoszenie> <nagroda>` | Nadpisuje istniejący kod |
| `/kod usun <nazwa>` | Usuwa kod |
| `/kod lista aktywne` | Lista aktywnych kodów |
| `/kod lista zuzyte` | Lista zużytych kodów |
| `/kod lista zuzyte wyczysc` | Czyści listę zużytych kodów |
| `/kod info <nazwa>` | Szczegóły kodu |
| `/kod przeladuj` | Przeładowuje konfigurację i wiadomości |

## Reward formats

### English

- `item` (item in your hand)
- `item:MATERIAL`
- `item:MATERIAL:amount`
- `permission:node`
- `rank:groupName` *(LuckPerms required)*

### Polish

- `przedmiot` (przedmiot w ręce)
- `przedmiot:MATERIAL`
- `przedmiot:MATERIAL:ilość`
- `permisja:node`
- `ranga:nazwaGrupy` *(wymaga LuckPerms)*

## Delay formats

Accepted examples:

- `10s`
- `1m`
- `1m10s`
- `1h`
- `1d`
- `1d2h3m30s`
- `60` *(seconds)*

## Announcement formats

Accepted examples:

- Polish: `tak` / `nie`
- English: `yes` / `no`
- (Optionally also `true` / `false` if enabled in parsing)

## English ↔ Polish mapping

| English subcommand | Polish subcommand |
|:-|:-|
| `help` | `pomoc` |
| `create` | `stworz` |
| `overwrite` | `nadpisz` |
| `delete` | `usun` |
| `list` | `lista` |
| `reload` | `przeladuj` |
| `info` | `info` |
| `active` | `aktywne` |
| `used` | `zuzyte` |
| `clear` | `wyczysc` |

## Configuration

### Language selection

In `config.yml`:

```yml
language: pl   # or en
```
## Other

Author: [@nxmsdev](https://github.com/nxmsdev)

License: [CC BY-NY 4.0](https://creativecommons.org/licenses/by-nc/4.0/legalcode)
