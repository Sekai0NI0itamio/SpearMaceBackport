# SpearMaceBackport (Forge 1.12.2)

Backport of the modern **Mace** and **Diamond Spear** to Minecraft 1.12.2,
plus a 1-second **easy-swap** window that applies BOTH the sword hit and the
mace smash with no vanilla attribute-swapping.

## Contents (diamond tier only, per request)

- **Mace** — base 6 damage, slow ~0.6 attack speed. Falling smash: +3 damage
  per fallen block (scaled by 1.12 attack cooldown). Landing a smash negates
  accumulated fall damage. Enchants: **Density** (+1/block/level, max 5),
  **Breach** (+15% smash damage/level, max 4 — 1.12 armor-model approximation),
  **Wind Burst** (upward launch on smash, max 3, treasure).
- **Diamond Spear** — jab 4 damage, 0.95 attack speed, 1561 durability.
  Right-click jab reaches 2–4.5 blocks; under 2 blocks damage falls off hard
  (soft dead zone). Sprinting charge adds velocity bonus damage + knockback.
  Enchant **Lunge** (charge damage + reach, max 3). Sword enchants
  (Sharpness/Smite/Bane/Fire Aspect/Looting/Unbreaking/Mending) apply via
  **anvil**; Lunge via table or anvil.
- **Easy-swap** — land a sword hit, then a mace smash on the same target
  within 1.0s, and the stored sword damage is added to the smash. Included in
  this mod (no separate mod needed).
- Crafting (all 1.12.2-obtainable): Heavy Core (obsidian ring + diamond),
  Breeze Rod (feather + blaze rod + stick), Mace (core + rod + stick),
  Diamond Spear (diagonal diamond + 2 sticks). Diamond Spear + Wind Burst book
  also appear in dungeon / End City chests (~50% per chest).

All textures are original creations for this mod, not Mojang assets.

## Build rule (hard)

**Never build locally.** Everything compiles on GitHub Actions
(Temurin Java 8, Gradle 4.9, ForgeGradle 2.3). The repo intentionally has no
`gradle-wrapper.jar`; CI uses system Gradle 4.9.

## Verify

Watch the `build` workflow; the green run must publish a non-empty
`build/libs/spearmacebackport-*.jar` artifact.
