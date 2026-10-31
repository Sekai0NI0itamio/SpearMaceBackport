# SpearMaceBackport (Forge 1.12.2)

Backport of the modern **Mace**, **Wind Charge** and **Diamond Spear** to
Minecraft 1.12.2, plus a 1-second **easy-swap** window that applies BOTH the
sword hit and the mace smash with no vanilla attribute-swapping.

## Contents (diamond tier only, per request)

- **Mace** (creative-only) — base 6 damage, 0.6 attack speed, 500
  durability, repaired with breeze rods. Falling smash (at 1.5+ blocks
  fallen, cooldown ignored): +4 damage per block for the first 3 blocks, +2
  for the next 5, +1 beyond, vertical momentum reset, landing only counts
  fall damage below the smash height, and the official knockback formula
  within 3.5 blocks (fixed +0.7 lift, doubled past 5 fallen blocks).
  Successful smashes burst modern-style particles: an explosion flash plus
  block-dust of the block under a grounded victim, and landing dust when
  you touch down. Enchants via anvil: **Density** (+0.5/block/level,
  max 5), **Breach** (−15% armor effectiveness/level, max 4),
  **Wind Burst** (3.5-block explosion at the attacker with per-level
  multipliers 1.2/1.75/2.2, shoves nearby mobs too, no fall protection,
  max 3, treasure-only), plus Smite, Bane, Fire Aspect, Unbreaking,
  Mending, Curse of Vanishing. Density/Breach/Smite/Bane are mutually
  exclusive.
- **Wind Charge** (creative-only) — right-click throws a straight-flying
  projectile (10-tick cooldown, 64-stack, no trail particles like modern).
  Direct hits deal 1 damage; any impact sets off the official 1.2-radius
  wind burst (2.4-block cutoff, +22% knockback, knockback-resistance aware) — shooting your own feet launches you ~6 blocks, and launched
  players land damage-free down to the burst height. Block impacts toggle
  wooden doors/trapdoors, fence gates, levers and buttons (never iron);
  dispensers can fire it too.
- **Breeze Rod** (creative-only) — repair material for the mace.
- **Diamond Spear** (craftable — the only survival recipe) — jab 4 damage
  on a full 1.05s attack meter, 1561 durability, 4.5-block reach with a hard
  2-block dead zone, never crits. Right-click jabs (multi-target,
  wall-checked); moving at 4.6+ blocks/s closing speed turns it into a
  kinetic charge: floor(speed × 1.075) + 4 + Sharpness, knockback at 5.1+.
  Enchant **Lunge** (max 3): every jab propels you 0.458 blocks/tick/level
  horizontally for 1 durability + hunger. Sword enchants
  (Sharpness/Smite/Bane/Fire Aspect/Knockback/Looting/Unbreaking/Mending)
  via **anvil**; Lunge via table or anvil.
- **Easy-swap** — land a sword hit, then a mace smash on the same target
  within 1.0s, and the stored sword damage is added to the smash. Included in
  this mod (no separate mod needed).
- Crafting: only the Diamond Spear (diagonal diamond + 2 sticks, plus a
  mirror recipe). Diamond Spear + Wind Burst book also appear in dungeon /
  End City chests (~50% per chest).

The item textures in this repo are the official Mojang textures (mace, wind
charge, breeze rod), committed by owner decision in v1.1.0; the legacy
`tools/apply_official_textures.sh` overlay script is retired.

Backport limits: no hold-to-charge stages/mount-dismount, single charge
damage roll, enchanting-table exclusivity between Density/Breach/Smite/Bane
is enforced at the anvil only; wind charges don't break decorated pots or
chorus flowers, have no crouch knockback bonus, drag slightly in water
(1.12.2 projectile physics vs modern inertia 1.0), can't be deflected, and
no breeze mob drops them; smash/wind-burst particles and sounds are the
closest 1.12.2 equivalents, not the modern gust/smash sprites.

## Build rule (hard)

**Never build locally.** Everything compiles on GitHub Actions
(Temurin Java 8, Gradle 4.9, ForgeGradle 2.3). The repo intentionally has no
`gradle-wrapper.jar`; CI uses system Gradle 4.9.

## Verify

Watch the `build` workflow; the green run must publish a non-empty
`build/libs/spearmacebackport-*.jar` artifact.
