# Inventory Profiles Next — 1.12.2 Backport

Take control over your inventory. Sort. Move matching/all items. Throw all items. Locked slots. Gear sets! And much more!

A faithful backport of [Inventory Profiles Next](https://www.curseforge.com/minecraft/mc-mods/inventory-profiles-next) by [anti-ad / mirinimi](https://github.com/blackd/Inventory-Profiles) to **Forge 1.12.2**.

**100% client-side**, every inventory operation is executed through vanilla window-click packets, so it works on **vanilla servers** and any modded server.

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
- Items can't **enter** empty locked slots either: shift-clicks route around them, and picked-up drops are instantly relocated to a free unlocked slot
- Stacks already in a locked slot still top up normally (shift-click merge and pickup), so a locked food/block stack keeps refilling

### Quality of Life
- **Move all** (`G`) — transfer everything between player inventory and container
- **Throw all** (`T`) — throw every stack matching the hovered item
- **Continuous crafting** — auto-refills the crafting grid from your inventory with even distribution (toggle checkbox next to the crafting GUI)
- **Auto-refill** — replaces your held item when it runs out or your tool breaks (with configurable durability threshold)
- **Scroll transfer** — move items in and out of containers with the scroll wheel
- **Item highlight** — highlights all matching stacks when hovering an item
- Full in-game config GUI (`O` or Mods → Config)

## Mod Compatibility

- **Quark** — Quark's chest search bar sits across the top of chest/shulker GUIs and used to overlap the sort buttons. When Quark is installed, the top chest sort row is now lifted above the GUI automatically so the two never collide. Toggle with `autoAdjustForQuark` in the `gui` config.
- **Button position** — `sortButtonOffsetX` / `sortButtonOffsetY` (config category `gui`) nudge the sort/move buttons by any pixel amount to dodge other mods' overlays. Manual offsets stack on top of the Quark auto-adjust.

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

## Differences from the Original

Backported for the 1.12.2 input/GUI systems:

- Original chord keybinds (e.g. `R,1` to apply profile 1) can't be expressed by 1.12.2's KeyBinding system — replaced with plain rebindable keys
- Not ported (out of 1.12.2 scope): villager trade bookmarks, in-game GUI editor, custom rule-file sort orders

## Credits & License

- Original mod: [Inventory Profiles Next](https://github.com/blackd/Inventory-Profiles) by **anti-ad / mirinimi** and contributors
- 1.12.2 backport: **XY**

Licensed under the [GNU Affero General Public License v3.0](LICENSE).
