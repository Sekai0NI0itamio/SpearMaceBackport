# Criteria: SpearMaceBackport (Forge 1.12.2)

- [ ] Single Forge 1.12.2 mod (Java 8, ForgeGradle 2.3) with Diamond Spear + Mace only — no other tiers required
- [ ] Mace base 6 damage, slow cooldown (~0.6 attack speed); smash bonus +3 per fallen block; Density +1/block/level (max 5); Breach 15% armor-effectiveness reduction/level (max 4); Wind Burst launch on smash (max 3)
- [ ] Diamond Spear jab 4 damage, 0.95 attack speed, 1561 durability; extended-reach jab + sprint charge attack; min-range dead zone documented
- [ ] Crafting recipes all 1.12.2-obtainable: Heavy Core surrogate + Breeze Rod surrogate -> Mace; diagonal diamond+sticks -> Diamond Spear
- [ ] Enchants registered: Density/Breach/WindBurst (mace-only), Lunge (spear-only); vanilla Sword enchants apply to spear (minus Sweeping Edge), mace takes Smite/Bane/FireAspect/Unbreaking/Mending-type
- [ ] 1-second easy-swap: sword hit then mace smash within 1.0s applies BOTH hits (stored sword damage as bonus); no vanilla attribute-swap needed
- [ ] Original 16x16 textures + item models + en_us.lang; NO Mojang asset copies
- [ ] Cloud build only: `gh` used for repo + workflow runs; no local `./gradlew`/compile; GitHub Actions (Temurin 8, Gradle 4.9, `gradle build`) green with non-empty JAR
- [ ] Peer review of diff done; open disagreements reported, not silently settled
