# Inventory Profiles Next — 1.12.2 Backport

Take control over your inventory. Sort. Move matching/all items. Throw all items. Locked slots. Gear sets! And much more!

A faithful backport of [Inventory Profiles Next](https://www.curseforge.com/minecraft/mc-mods/inventory-profiles-next) by [anti-ad / mirinimi](https://github.com/blackd/Inventory-Profiles) to **Minecraft 1.12.2 Forge**, rewritten from Kotlin to Java 8.

**100% client-side** — like the original, every inventory operation is executed through vanilla window-click packets, so it works on **vanilla servers** and any modded server. Nothing to install server-side.

## Features

### Sorting
- **Sort** (default `R`) — merge stacks and sort by item ID, name, or mod (configurable)
- **Sort in columns** (`C`) / **rows** (`V`) — groups item types into column/row blocks using the original's bin-packing algorithm
- Sort buttons in every container GUI, with scroll-wheel sort-order switching
- Hotbar restock — tops up partial hotbar stacks after sorting

### Inventory Profiles (Gear Sets)
- Save your hotbar, armor, and offhand loadout as a named profile
- Profile bar above the player inventory: `[◀] profile name [▶] [+] [x]`
  - `+` — save current loadout as a new profile (Shift-click: overwrite active profile)
  - `x` — delete active profile (Shift-click to confirm)
  - `◀ ▶` — cycle and apply profiles (also `,` / `.` keys)
- Profiles stored as JSON in `config/inventoryprofilesnext/profiles/`

### Locked Slots
- Lock any player inventory slot (`K` or Alt+Click) — locked slots are skipped by sorting, move-all, throw-all, refill, and shift-click/quick-move

### Quality of Life
- **Move all** (`G`) — transfer everything between player inventory and container
- **Throw all** (`T`) — throw every stack matching the hovered item
- **Continuous crafting** — auto-refills the crafting grid from your inventory with even distribution (toggle checkbox next to the crafting GUI)
- **Auto-refill** — replaces your held item when it runs out or your tool breaks (with configurable durability threshold)
- **Scroll transfer** — move items in and out of containers with the scroll wheel
- **Item highlight** — highlights all matching stacks when hovering an item
- Full in-game config GUI (`O` or Mods → Config)

## Controls (all rebindable, category "Inventory Profiles Next")

| Key | Action |
|-----|--------|
| `R` | Sort (hovered section) |
| `C` / `V` | Sort in columns / rows |
| `G` | Move all to other side |
| `T` | Throw all matching hovered item |
| `K` / Alt+Click | Toggle slot lock |
| `O` | Open config GUI |
| `,` / `.` | Previous / next profile |
| `;` | Save current loadout as profile |
| unbound | Apply profile 1 / 2 / 3 |

## Installation

1. Install [Minecraft Forge](https://files.minecraftforge.net/net/minecraftforge/forge/index_1.12.2.html) for 1.12.2 (built against 14.23.5.2860)
2. Drop the jar into your `mods` folder
3. Client-side only — safe to join vanilla and modded servers

## Building

```
./gradlew build
```

Requires JDK 8. Built with [RetroFuturaGradle](https://github.com/GTNewHorizons/retrofuturagradle), MCP stable_39 mappings. Jar lands in `build/libs/`.

## Differences from the Original

Backported for the 1.12.2 input/GUI systems:

- Original chord keybinds (e.g. `R,1` to apply profile 1) can't be expressed by 1.12.2's KeyBinding system — replaced with plain rebindable keys
- Not ported (out of 1.12.2 scope): villager trade bookmarks, in-game GUI editor, custom rule-file sort orders

## Credits & License

- Original mod: [Inventory Profiles Next](https://github.com/blackd/Inventory-Profiles) by **anti-ad / mirinimi** and contributors
- 1.12.2 backport: **XY**

Licensed under the [GNU Affero General Public License v3.0](LICENSE), same as the original.
