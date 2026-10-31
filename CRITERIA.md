# Criteria: SpearMaceBackport (Forge 1.12.2) — verification log

- [x] Single Forge 1.12.2 mod (Java 8, ForgeGradle 2.3) with Diamond Spear + Mace only — evidence: `build.gradle` (FG 2.3, 1.12.2-14.23.5.2838, stable_39), GitHub Actions run 41195742340 `success`, artifact `spearmacebackport-jar` 38196 bytes
- [x] Mace base 6 damage (5.0 modifier + 1.0 player base), slow ~0.6 attack speed; smash +3 per fallen block (cooldown-scaled); Density +1/block/level (max 5); Breach true armor reduction 15%/level via CombatRules (max 4); Wind Burst launch (max 3); smash negates fall (fallDistance reset + <6-block landing cancel within 1s) — evidence: green build + peer review round 1, all six findings fixed
- [x] Diamond Spear jab 4 (3.0 modifier + 1.0 base), 0.95 speed, 1561 durability; right-click jab 2–4.5 blocks (wall-checked), soft dead zone under 2 blocks; sprint/velocity charge + Lunge — evidence: `CombatHandler.raycastLiving` + `blockLimitedReach`, peer-reviewed
- [x] Crafting all 1.12.2-obtainable: Heavy Core (obsidian+diamond), Breeze Rod (feather+blaze+stick), Mace (core+rod+stick), Diamond Spear (diagonal) — evidence: 4 recipe JSONs, vanilla-only ingredients
- [x] Enchants registered with correct max levels; Density/Breach/Lunge table-available, Wind Burst treasure-only (dungeon/End City book ~50%); sword enchants incl. Knockback on spear via anvil — evidence: enchant classes + anvil handler + loot injection, compile-verified
- [x] 1-second easy-swap: sword hit then mace smash on same victim within 1.0s adds stored sword damage; stale entries pruned — evidence: `CombatHandler` swap block, victimId + timestamp check
- [x] Original 16x16 textures + models + en_us.lang; NO Mojang assets — evidence: generated PNGs in `textures/items/`
- [x] Cloud build only: `gh` used for repo + runs; zero local compiles — evidence: 4 pushed commits, 4 Actions runs, no local gradle invocation
- [x] Peer review done, disagreements applied (round 1 of max 2; round 2 folded into green rebuild) — evidence: peer-reviewer task findings all addressed in commit 2c54859
- [ ] In-game playtest on a 1.12.2 client/server (damage numbers vs armor, fall timing, anvil outputs) — NOT done here; needs a 1.12.2 instance. Open item for the user.
