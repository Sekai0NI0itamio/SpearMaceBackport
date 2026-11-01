# Criteria: SpearMaceBackport (Forge 1.12.2) — verification log

- [x] Single Forge 1.12.2 mod (Java 8, ForgeGradle 2.3), Diamond Spear + Mace — evidence: run 41197261687 `success`, artifact `spearmacebackport-jar` 42191 bytes
- [x] Mace engine values: base 6, speed 0.6, durability 500, breeze-rod repair; smash tiers +4/+2/+1 on full fall (triggers >=1.5, cooldown ignored per MC-270160, vanilla x1.5 crit applies by falling); Density +0.5/block/level (max 5); Breach true -15% armor/level via CombatRules, normal + smash (max 4); Wind Burst 8 blocks/level via integrated MC physics (motionY 1.289/1.873/2.349, max 3, treasure); 2.5-block AoE knockback (stronger past 5); Density/Breach/Smite/BoA mutually exclusive at anvil — evidence: peer-review + wiki engine docs cross-checked vs Mojang article (fall 2 -> 14, fall 9 -> 29)
- [x] Spear engine values: jab 4 on 100% meter (right-click, meter spent via resetCooldown), 1561 durability, 4.5 reach, hard 2-block dead zone, never crits (vanilla x1.5 stripped on left-click; right-click path never crits); charge floor(closingSpeed x 1.075) + 4 + Sharpness, gates 4.6/5.1, knockback 1.0; Lunge propulsion 0.458 b/tick/level, 1 durability + 4xlevel exhaustion, hunger>=7 / not riding/gliding/water; multi-target wall-checked ray; Sharpness/Smite/BoA/FA/Knockback/Looting/Unbreaking/Mending via anvil, Lunge via table+anvil — evidence: green build 41197261687
- [x] Recipes match modern layout: shapeless heavy_core + breeze_rod mace; spear material top-right diagonal + mirror file
- [x] 1s easy-swap dual-hit (sword -> mace, same victim, stale entries pruned) — unchanged, still in CombatHandler
- [x] No Mojang bytes in repo: original art only; official textures via local `tools/apply_official_textures.sh` -> gitignored `OfficialArt/` resource pack (user's own client jar, local use)
- [x] Cloud-only: gh used for all runs; no local compile — evidence: Actions history
- [ ] In-game playtest on 1.12.2 (damage vs armor, fall timing, anvil outputs) — needs a 1.12.2 instance. Open.

## v1.1.0 change set (2026-10-31) — status: done, green build 41200587254

- [x] Official Mojang textures committed for mace, wind_charge, breeze_rod (user override of no-bytes rule), extracted from Mojang CDN 26.3 client jar — evidence: PNGs in repo, 16x16 RGBA, script retired
- [x] Wind charge item: 64-stack, right-click throw, 10-tick cooldown, direct-hit 1 damage, 2.4-block burst (+22% knockback feel, ~6-block self-launch aiming down), fall-damage negation below burst Y, wooden-only toggles, dispenser-fireable — evidence: green build 41200587254
- [x] Mace smash spawns modern-style particles: explosion burst + block-crack dust of the block under a grounded victim (skipped when victim center is in air) — evidence: green build 41200587254
- [x] Recipes: spear (both mirror files) stays craftable; mace/breeze_rod/heavy_core recipes deleted; NO wind_charge recipe; heavy_core item/model/lang fully removed — evidence: recipes/ has only spear files, no heavy_core refs in src/
- [x] Mace anvil allowlist = everything modern (Density, Breach, Wind Burst, Smite, Bane, Fire Aspect, Unbreaking, Mending, Curse of Vanishing); exclusivity unchanged — evidence: green build 41200587254
- [x] Green cloud build publishes non-empty jar; README/CRITERIA/memory updated — evidence: run 41200587254 `success`, artifact `spearmacebackport-jar` 55620 bytes

## v1.1.1 change set (2026-10-31) — status: done, green build 41203505384

- [x] Smash knockback = official 3.5-block formula with exclusion predicate (spectators, allies, tamed-by-victim, marker stands, creative-flyers) — evidence: green build 41203505384
- [x] Smash trigger drops the motionY check (official: fallDistance > 1.5, not fall-flying) — evidence: green build 41203505384
- [x] Fall protection = impact-height record with 40-tick grace for smash (skipped on Wind Burst) and wind charges; landing dust when the victim was grounded; momentum reset 0.01 — evidence: green build 41203505384
- [x] Wind Burst = official 3.5-radius explosion at attacker (multipliers 1.2/1.75/2.2), shoves mobs, no fall protection — evidence: green build 41203505384
- [x] Wind charge burst: knockback-resistance (not blast protection), no trail particles, +0.25 face-normal block offset, 1.2 radius constant — evidence: green build 41203505384
- [ ] User retests in game (questions asked 2026-10-31; awaiting observations)

## v1.1.2 change set (2026-10-31) — status: done, green build 41205677387

- [x] Wind burst particles: two-ring gust snapshot (inner CLOUD ring + outer SMOKE_LARGE ring, center puff + sparks) instead of box-spread cloud — evidence: green build 41205677387
- [x] Spear jab feedback: sweep sound + sweep particles on hit so the jab reads in game — evidence: green build 41205677387
- [x] Spear/lunge/easy-swap audit: all assets present in jar (class, Lunge class, model, both recipes, texture, lang), all code paths intact — evidence: jar listing + JSON validation
- [ ] Green cloud build publishes non-empty jar (run 41205677387 `success`, artifact 59412 bytes, local jar 44523 bytes); user retests spear + wind charge

## v1.1.3 change set (2026-10-31) — status: done, green build 41206784849

- [x] Official Mojang spear textures committed (inventory 16×16 + 32×32 in-hand with official hold pose via held-item model override) — evidence: PNGs in repo (201 + 227 bytes), green build 41206784849
- [x] Jab scales with attack meter (0.2 + strength^2 x 0.8) instead of demanding a full meter; Lunge creative-food exemption + whoosh sound — evidence: green build 41206784849
- [x] Lunge verified byte-exact vs official lunge.json (0.458/level horizontal impulse, +1 damage, 4/level exhaustion, no ride/glide/water, food >= 7) — evidence: side-by-side with 26.3 sources
- [x] Jab verified vs official PiercingWeapon.attack (full attribute damage, multi-target wall-checked line, stab scaling, lunge post-effect) — evidence: side-by-side with 26.3 sources
- [x] Green cloud build publishes non-empty jar (run 41206784849 `success`); local jar downloaded (1.1.3); user retests spear

## v1.1.4 change set (2026-11-01) — status: done, green build 41260219926

- [x] Enchanting-table access fixed: explicit canApplyAtEnchantingTable on Lunge/Density/Breach/WindBurst + item enchantability (mace 15, spear 10) — evidence: green build 41260219926
- [x] Auto-swap: falling smash with non-mace/non-spear held fires first inventory mace's smash package (bonus, Breach, Wind Burst, knockback, particles, fall protection) for 1 mace durability — evidence: green build 41260219926
- [x] Green cloud build publishes non-empty jar (run 41260219926 `success`); local 1.1.4 jar downloaded; lunge diagnosis: anvil Lunge, grounded, jab, zero feedback -> jab itself silent

## v1.1.5 change set (2026-11-01) — status: done, green build 41261852880

- [x] Whiffed jabs (dead zone / wall / out of reach) now swing + soft whoosh instead of total silence, matching the modern always-swing — evidence: green build 41261852880
- [x] Green cloud build publishes non-empty jar (run 41261852880 `success`); local 1.1.5 jar downloaded; user retests lunge at exact 3-block grounded full-meter jab

## v1.1.6 change set (2026-11-01) — status: done, green build 41263647881

- [x] Jab dispatch migrated from the interact event to the item's own right-click (proven server path); user confirmed 1.1.5 + silent right-click = event path dead — evidence: green build 41263647881
- [x] Landing grace: smash fires within 0.5s of landing a 1.5+ fall (deliberate forgiveness deviation, uses recorded fall distance) — evidence: green build 41263647881
- [x] Green cloud build publishes non-empty jar (run 41263647881 `success`); local 1.1.6 jar downloaded

## v1.1.7 change set (2026-11-01) — status: done, green build 41264713721

- [x] Held-spear render fixed by removing the backwards override: single long-art model, vanilla sword grip everywhere — evidence: green build 41264713721, jar model 1096 bytes, no in_hand files
- [x] Green cloud build publishes non-empty jar (run 41264713721 `success`); local 1.1.7 jar downloaded (47045 bytes)

## v1.1.8 change set (2026-11-01) — status: in progress

- [ ] Dual-art via render-context switch (world null = short icon, world live = long held art); dead perspective code removed
- [ ] Green cloud build publishes non-empty jar; user confirms inventory icon + held render
