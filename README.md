# SpearMaceBackport (Forge 1.12.2)

Backport of the modern **Mace**, **Wind Charge** and **Diamond Spear** to
Minecraft 1.12.2, plus a 1-second **easy-swap** window that applies BOTH the
sword hit and the mace smash with no vanilla attribute-swapping.

## Contents (diamond tier only, per request)

- **Mace** (creative-only) — base 6 damage, 0.6 attack speed, 500
  durability, repaired with breeze rods. Falling smash (at 1.5+ blocks
  fallen, cooldown ignored): +4 damage per block for the first 3 blocks, +2
  for the next 5, +1 beyond, landing negates your fall damage and knocks back
  everything within 2.5 blocks (harder past 5 blocks). Successful smashes
  burst modern-style particles: an explosion flash plus block-dust of the
  block under a grounded victim. Enchants via anvil: **Density**
  (+0.5/block/level, max 5), **Breach** (−15% armor effectiveness/level, max
  4), **Wind Burst** (+8 blocks launch height/level, max 3, treasure-only),
  plus Smite, Bane, Fire Aspect, Unbreaking, Mending, Curse of Vanishing.
  Density/Breach/Smite/Bane are mutually exclusive.
- **Wind Charge** (creative-only) — right-click throws a straight-flying
  projectile (10-tick cooldown, 64-stack). Direct hits deal 1 damage; any
  impact sets off a 2.4-block wind burst that knocks entities back (+22%
  over a matching raw explosion, so shooting your own feet launches you
  ~6 blocks) and lets launched players land damage-free down to the burst
  height. Block impacts toggle wooden doors/trapdoors, fence gates, levers
  and buttons; dispensers can fire it too.
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
chorus flowers, have no crouch knockback bonus, and no breeze mob drops
them; smash/wind-burst particles are the closest 1.12.2 equivalents, not the
modern gust/smash sprites.

## Build rule (hard)

**Never build locally.** Everything compiles on GitHub Actions
(Temurin Java 8, Gradle 4.9, ForgeGradle 2.3). The repo intentionally has no
`gradle-wrapper.jar`; CI uses system Gradle 4.9.

## Verify

Watch the `build` workflow; the green run must publish a non-empty
`build/libs/spearmacebackport-*.jar` artifact.
