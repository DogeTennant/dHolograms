# dHolograms

**A lightweight, feature-rich hologram plugin for Paper 1.21+ powered entirely by modern Display entities.**

No armor stands. No fake players. No packet hacks. dHolograms uses the native `TextDisplay` and `ItemDisplay` entities introduced in Minecraft 1.19.4, giving you crisp, perfectly rendered holograms that look great at any render distance and never cause entity-count bloat.

---

## Features

- **Modern Display entities** - uses `TextDisplay` and `ItemDisplay` (no legacy entity tricks)
- **Text & Item lines** - mix floating text with spinning, bobbing item displays in the same hologram
- **5 built-in text animations** - Rainbow, Wave, Burn, Typewriter, Scroll, each fully configurable
- **Multi-page holograms** - pages with interactive click-to-navigate arrows spawned automatically
- **Click actions** - right, left, shift-right, and shift-left each support a fully chainable action pipeline
- **Tags** - group holograms for bulk management
- **Per-hologram flags** - selectively disable updating, placeholders, animations, or click actions
- **Per-line permissions** - hide individual lines from players who lack a permission node
- **PlaceholderAPI support** - any PAPI placeholder works in text lines, refreshed on a configurable interval
- **BungeeCord CONNECT action** - send players to another server on click
- **YML & MySQL storage** - choose flat-file or a full MySQL backend with HikariCP connection pooling
- **Multi-language** - ships with `en_us` and `cs_cz`; drop in your own `.yml` translation file
- **Developer API** - clean `DHologramsAPI` class for other plugins to create, query, and delete holograms
- **Full tab-completion** - every argument of every subcommand is tab-completed

---

## Requirements

| Requirement | Version |
|---|---|
| Server software | Paper (or fork) |
| Minecraft version | 1.21+ |
| Java | 21+ |
| PlaceholderAPI | 2.x (optional) |

> Spigot is **not** supported. Display entities require Paper's API.

---

## Installation

1. Download `dHolograms.jar` and drop it into your `plugins/` folder.
2. Start or restart your server.
3. Edit `plugins/dHolograms/config.yml` if you want MySQL storage or to tune defaults.
4. Optionally install [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) for placeholder support.

---

## Configuration

`plugins/dHolograms/config.yml`

```yaml
# Language file to use (must exist in plugins/dHolograms/translations/)
language: en_us

storage:
  # Storage backend: yml or mysql
  type: yml
  mysql:
    host: localhost
    port: 3306
    database: dholograms
    username: root
    password: ''
    pool-size: 5
    connection-timeout: 30000   # ms
    idle-timeout: 600000         # ms
    max-lifetime: 1800000        # ms

holograms:
  # How often (ticks) to refresh PlaceholderAPI values and re-check visibility.
  # 20 ticks = 1 second. Lower is more responsive; higher saves CPU.
  refresh-ticks: 20

  # Default view distance (blocks) for newly created holograms.
  default-range: 64.0

  # Default vertical gap between lines (blocks) for newly created holograms.
  default-line-height: 0.3

  # Anti-spam cooldown between click-action executions per player (ticks).
  # Set to 0 to disable.
  click-cooldown-ticks: 10
```

---

## Permissions

| Node | Description | Default |
|---|---|---|
| `dholograms.admin` | Full access to all dHolograms commands | `op` |

A per-hologram **view permission** can optionally be set with `/dh display` - players without that node simply won't see the hologram at all. Individual lines also support their own permission node via `/dh linepermission`.

---

## Commands

The base command is `/dholograms` (alias `/dh`). All subcommands require `dholograms.admin`.

### Hologram Management

| Command | Description |
|---|---|
| `/dh create <name> [text]` | Create a hologram at your eye position, optionally with an initial line |
| `/dh delete <name>` | Permanently delete a hologram |
| `/dh list` | List all holograms with their world/coordinates |
| `/dh info <name>` | Show detailed info (location, lines, pages, range, tag, permission) |
| `/dh rename <name> <newname>` | Rename a hologram |
| `/dh move <name>` | Move a hologram to your current eye position |
| `/dh tp <name>` | Teleport to a hologram's location |
| `/dh near <distance>` | Find all holograms within `<distance>` blocks, sorted by proximity |
| `/dh clone <name> <newname>` | Clone a hologram (copies all lines, pages, display settings, animations) |
| `/dh center <name>` | Snap hologram X/Z to the center of its current block |
| `/dh disable <name>` | Hide the hologram from all players without deleting it |
| `/dh enable <name>` | Re-enable a disabled hologram |
| `/dh reload` | Reload config, language, and respawn all holograms |
| `/dh language <code>` | Switch language at runtime |
| `/dh help [page]` | Show paginated command help |

### Line Editing

Lines are **1-indexed** in all commands.

| Command | Description |
|---|---|
| `/dh addline <name> <content>` | Append a line to page 1 |
| `/dh setline <name> <#> <content>` | Replace an existing line |
| `/dh removeline <name> <#>` | Delete a line |
| `/dh insertline <name> <#> <content>` | Insert a line before position `#` |
| `/dh swaplines <name> <#1> <#2>` | Swap two lines by index |
| `/dh lineinfo <name> <#>` | Show type, content, offsets, permission, and animation for a line |
| `/dh lineheight <name> <#> <blocks>` | Fine-tune a line's Y offset relative to the hologram anchor |
| `/dh linexoffset <name> <#> <blocks>` | Shift a line left/right (X-axis) |
| `/dh linezoffset <name> <#> <blocks>` | Shift a line forward/back (Z-axis) |
| `/dh linepermission <name> <#> [node]` | Require a permission to see that line (omit node to clear) |
| `/dh lineanim <name> <#> <type> [speed] [color1] [color2]` | Set a text animation on a line |
| `/dh linerotation <name> <#> <deg/tick> [bob]` | Set rotation speed and bob amplitude for an item line |

#### Line Content Syntax

```
/dh addline myhologram Hello, <player>!       ← plain text / MiniMessage
/dh addline myhologram item:diamond_sword     ← spinning item line (prefix with "item:")
```

Text lines support full [MiniMessage](https://docs.advntr.dev/minimessage/format.html) formatting, e.g.:

```
<gradient:red:gold>Welcome</gradient>
<bold><aqua>Hello!</aqua></bold>
<rainbow>Colorful text</rainbow>
```

### Pages

Holograms start with one page. Adding a second page automatically spawns interactive **◀ ▶** navigation arrows below the hologram that each player can click individually - their page state is tracked per-player.

| Command | Description |
|---|---|
| `/dh page <name> add` | Add a new page |
| `/dh page <name> remove <#>` | Remove page `#` (cannot remove the last page) |
| `/dh page <name> info` | List all pages and their line counts |
| `/dh page <name> <#page> addline <content>` | Add a line to a specific page |
| `/dh page <name> <#page> setline <#line> <content>` | Edit a line on a specific page |
| `/dh page <name> <#page> removeline <#line>` | Remove a line from a specific page |
| `/dh page <name> <#page> insertline <#line> <content>` | Insert a line on a specific page |

### Display Settings

```
/dh display <name> <setting> <value>
```

| Setting | Values | Description |
|---|---|---|
| `billboard` | `FIXED` `VERTICAL` `HORIZONTAL` `CENTER` | How the display rotates to face the player. `CENTER` (default) always faces the viewer. |
| `align` | `LEFT` `CENTER` `RIGHT` | Text alignment within the display box |
| `scale` | `<x> <y> <z>` | Scale the display entity (e.g. `2.0 2.0 2.0` for double size) |
| `background` | `#AARRGGBB` or `none` | Background panel color in hex ARGB; `none` for no panel |
| `shadow` | `true` `false` | Text drop shadow |
| `range` | `<blocks>` | View distance - hologram is hidden beyond this range |
| `updaterange` | `<blocks>` or `-1` | Distance within which PAPI/content updates are pushed. `-1` = same as `range` |
| `linewidth` | `<pixels>` | Pixel width before text wraps (default `200`) |
| `facing` | `<degrees>` | Yaw rotation, useful with `FIXED` billboard |
| `downorigin` | `true` `false` | Stack lines downward from anchor (default) or upward when `true` |

### Visibility (Show/Hide)

```
/dh show <name> <player>   ← make visible to a specific player
/dh hide <name> <player>   ← hide from a specific player
```

This is a **per-player override** on top of any view permission or range check. Useful for scripted cutscenes, tutorials, or player-specific UI.

### Tags

Tags let you group holograms and manage them as a batch.

```
/dh tag <name> <tag>          ← assign tag to hologram
/dh tag <name> none           ← clear tag
/dh tag list <tag>            ← list all holograms with a tag
/dh tag delete <tag>          ← delete every hologram with a tag
```

### Flags

Flags disable specific subsystems on a per-hologram basis.

```
/dh flag <name> add <flag>
/dh flag <name> remove <flag>
/dh flag <name> list
```

| Flag | Effect |
|---|---|
| `DISABLE_UPDATING` | Skip all PAPI refresh ticks for this hologram |
| `DISABLE_PLACEHOLDERS` | Evaluate PAPI placeholders as literal strings |
| `DISABLE_ANIMATIONS` | Freeze all text animations |
| `DISABLE_ACTIONS` | Ignore all click interactions |

---

## Animations

Set animations on any **text line** with:

```
/dh lineanim <name> <#> <type> [speed] [color1] [color2]
```

`speed` is a positive integer - higher is **slower**. Default is `2`.
`color1` / `color2` accept `#RRGGBB` hex or Adventure named colors (e.g. `red`, `aqua`).

| Type | Description | Uses colors? |
|---|---|---|
| `RAINBOW` | Each character cycles through the full HSV hue wheel, phase-shifted per tick | No |
| `WAVE` | A two-color sine wave sweeps through the characters | Yes |
| `BURN` | A color front sweeps left-to-right, revealing `color2` from `color1` | Yes |
| `TYPEWRITER` | Characters revealed one-by-one, then resets after a pause | No |
| `SCROLL` | Text scrolls horizontally through a sliding window | No |
| `NONE` | Removes any existing animation | - |

**Examples:**

```
# Slow rainbow on line 1
/dh lineanim welcome 1 rainbow 4

# Blue-to-gold wave on line 2
/dh lineanim welcome 2 wave 3 #5555FF #FFAA00

# Burn effect from red to white
/dh lineanim welcome 3 burn 2 red white

# Typewriter reveal
/dh lineanim welcome 4 typewriter 1

# Remove animation
/dh lineanim welcome 1 none
```

> Animations are paused while the `DISABLE_ANIMATIONS` flag is active. Animated lines do **not** support PAPI placeholders while animating (the animation renders over live text).

---

## Click Actions

Assign an action pipeline to any of the four click types:

```
/dh click <name> set <right|left|shift-right|shift-left> <actions>
/dh click <name> clear [right|left|shift-right|shift-left|all]
```

Actions are a **semicolon-separated list** of action tokens. They execute in order, and a `PERMISSION` check will abort the chain if the player lacks the node.

The placeholder `{player}` in any action string is replaced with the clicking player's name.

### Action Reference

| Token | Syntax | Description |
|---|---|---|
| `PERMISSION` | `PERMISSION:<node>` | Stop the chain if player lacks `<node>` |
| `MESSAGE` | `MESSAGE:<text>` | Send a MiniMessage-formatted chat message |
| `SOUND` | `SOUND:<key>[:<volume>:<pitch>]` | Play a sound at the player's location |
| `COMMAND` | `COMMAND:<cmd>` | Execute a command **as the player** |
| `CONSOLE` | `CONSOLE:<cmd>` | Execute a command **as console** |
| `CONNECT` | `CONNECT:<server>` | Send the player to a BungeeCord server |
| `TELEPORT` | `TELEPORT:[world:]<x>:<y>:<z>[:<yaw>:<pitch>]` | Teleport the player |
| `NEXT_PAGE` | `NEXT_PAGE[:<hologram>]` | Advance to the next page (current or named hologram) |
| `PREV_PAGE` | `PREV_PAGE[:<hologram>]` | Go back to the previous page |
| `PAGE` | `PAGE:[<hologram>:]<#>` | Jump to a specific page (1-indexed) |

**Examples:**

```
# Right-click: permission gate, then run a command
/dh click shop set right PERMISSION:shop.use;COMMAND:shop open

# Left-click: play a sound and send a message
/dh click welcome set left SOUND:entity.experience_orb.pickup:1.0:1.5;MESSAGE:<green>Welcome, {player}!

# Teleport to coordinates in another world
/dh click portal set right TELEPORT:nether:100:64:-200

# Connect to lobby on BungeeCord
/dh click hub set right CONNECT:lobby

# Two-page hologram - arrows controlled via click actions too
/dh click info set shift-right NEXT_PAGE
/dh click info set shift-left PREV_PAGE
```

---

## PlaceholderAPI

If PlaceholderAPI is installed, any PAPI placeholder (e.g. `%player_name%`, `%server_online%`) works in text line content and is refreshed every `refresh-ticks` ticks (default: every second).

Placeholder resolution is skipped for a hologram if it has the `DISABLE_PLACEHOLDERS` flag, or globally if the `DISABLE_UPDATING` flag is set.

You can also configure a separate `updaterange` for PAPI updates - players farther than `updaterange` blocks won't have their display refreshed, saving performance on densely populated servers.

---

## Developer API

Add dHolograms as a dependency (soft or hard) and use `DHologramsAPI` - no internal classes required.

```java
// Check the plugin is loaded first
if (Bukkit.getPluginManager().getPlugin("dHolograms") != null) {

    // Create a hologram
    Hologram h = DHologramsAPI.createHologram("my_holo", player.getLocation());

    // Retrieve an existing hologram
    Hologram h = DHologramsAPI.getHologram("shop_hologram");

    // List all holograms
    Collection<Hologram> all = DHologramsAPI.getAllHolograms();

    // Check existence
    boolean exists = DHologramsAPI.hologramExists("my_holo");

    // Delete
    DHologramsAPI.deleteHologram("my_holo");

    // After modifying a hologram programmatically, save it
    DHologramsAPI.saveHologram(h);

    // Access the full manager for advanced operations
    HologramManager manager = DHologramsAPI.getHologramManager();
}
```

`plugin.yml` soft-dependency:
```yaml
softdepend:
  - dHolograms
```

---

## Storage Backends

### YML (default)

Holograms are stored in `plugins/dHolograms/holograms/` - one `.yml` file per hologram. Zero setup required.

### MySQL

Set `storage.type: mysql` in `config.yml` and fill in the connection details. dHolograms uses HikariCP for connection pooling and auto-reconnect. The table is created automatically on first start.

---

## Translations

Language files live in `plugins/dHolograms/translations/`. The plugin ships with:

| Code | Language |
|---|---|
| `en_us` | English (United States) |
| `cs_cz` | Czech |

To add your own language, copy `en_us.yml`, translate the values, save it as e.g. `de_de.yml`, and set `language: de_de` in `config.yml`. You can also switch language at runtime with `/dh language <code>`.

---

## License

This project is open source. See [LICENSE](LICENSE) for details.

---

## Issues & Contributions

Found a bug or want a feature? Open an issue or pull request on GitHub. Please include your Paper version, Java version, and steps to reproduce.
