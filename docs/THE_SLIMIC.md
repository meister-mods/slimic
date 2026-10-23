# The Slimic

English | [한국어](THE_SLIMIC.kr.md) · [Gameplay](GAMEPLAY.md) · [Guidebook](GUIDEBOOK.md)

[Fallen Sanctum](FALLEN_SANCTUM.md) describes kingdom-map fragments, the five-zone royal sanctuary, its surviving watch, and the shared core used for [Royal synthesis](SYNTHESIS.md).

The Slimic is a separate dimension shaped like a weathered Overworld, with gel-covered ground and traces of a lost slime kingdom. Its single biome, **Damp Plains**, spans hills, caves, and waterways. It follows the ordinary day and night cycle and supports beds and respawn anchors according to Overworld rules: beds work; charged anchors explode.

## Slime portals

In the Overworld, build a vertical rectangle of **vanilla slime blocks** with the same geometry as a Nether portal. The interior is 2–21 blocks wide and 3–21 blocks tall; the smallest outer frame is 4×5 and the largest is 23×23. Corners are optional. Keep the interior empty, hold a slimeball, and right-click a frame face that points into the opening. Survival uses one slimeball; Creative preserves it. Both hands work.

Stand inside for about four seconds in Survival; Creative players and unmounted entities cross immediately. Portals connect only the Overworld and The Slimic, using a **1:1 coordinate scale**. Dismount before traveling. Portal cooldown prevents immediate return trips; leave the portal before re-entering. Breaking a required frame block closes the portal. Repair and reactivate it to reopen the route.

The destination reuses a valid, safe portal within 128 horizontal blocks. Otherwise it searches nearby terrain and creates a slime-block frame with a safe landing platform, including above water where needed. Existing containers and protected placements are preserved. Temporary companion lights do not block activation. If no safe exit can be placed, the entity remains in its original dimension; players receive an explanation. Return portals persist with the world.

## Damp Plains

Green Slimic Stone and deep Slimic Slate lie under damp grass, soil, silt, clay, mud, and scattered congealed gel. Caves and familiar ores occur underground. Gel can change movement, so mark the return portal and bring ordinary tools, food, and building supplies.

Wild Slimes can spawn on the surface. Frogs inhabit the plains, bats inhabit caves, and familiar hostile mobs can appear in darkness. Their ordinary difficulty and mob-spawning rules still apply. Tiny wild Slimes support golden-apple taming just as they do in the Overworld.

## Building materials

The **Slimic Blocks** Creative tab has **54 entries**: 32 independent base blocks, 8 stairs, 8 slabs, and 6 walls. The ordinary Slimic companion tab retains companions and tools. Stone uses pickaxes, soil uses shovels, and infused wood uses axes. Gel Glass needs Silk Touch to drop itself. Slimic Stone normally drops Slimic Cobblestone; smelt it to recover stone.

A stonecutter processes Slimic Stone into the masonry palette. Stairs, slabs, and walls also have matching crafting recipes. Additional recipes include slime-infused native planks, slime clay, hardened gel, luminous gel, dried gel, and smelted silt glass. JEI shows the exact ingredients and outputs. Slimic Soil, Damp Grass Block, and Slime Mud can be tilled into ordinary farmland when the space above is clear; they also support ordinary plants.

| Base block | Resource ID |
| --- | --- |
| Slimic Stone | `slimic:slimic_stone` |
| Slimic Cobblestone | `slimic:slimic_cobblestone` |
| Mossy Slimic Cobblestone | `slimic:mossy_slimic_cobblestone` |
| Slimic Stone Bricks | `slimic:slimic_stone_bricks` |
| Cracked Slimic Stone Bricks | `slimic:cracked_slimic_stone_bricks` |
| Mossy Slimic Stone Bricks | `slimic:mossy_slimic_stone_bricks` |
| Chiseled Slimic Stone Bricks | `slimic:chiseled_slimic_stone_bricks` |
| Slimic Tiles | `slimic:slimic_tiles` |
| Cracked Slimic Tiles | `slimic:cracked_slimic_tiles` |
| Ancient Foundation | `slimic:ancient_foundation` |
| Ancient Foundation Bricks | `slimic:ancient_foundation_bricks` |
| Pale Slimic Stone | `slimic:pale_slimic_stone` |
| Polished Slimic Stone | `slimic:polished_slimic_stone` |
| Slime Marble | `slimic:slime_marble` |
| Slime Marble Bricks | `slimic:slime_marble_bricks` |
| Chiseled Slime Marble | `slimic:chiseled_slime_marble` |
| Black Slime Stone | `slimic:black_slime_stone` |
| Polished Black Slime Stone | `slimic:polished_black_slime_stone` |
| Slimic Slate | `slimic:slimic_slate` |
| Slimic Slate Bricks | `slimic:slimic_slate_bricks` |
| Ruined Bricks | `slimic:ruined_bricks` |
| Slimic Soil | `slimic:slimic_soil` |
| Damp Grass Block | `slimic:damp_grass_block` |
| Slime Silt | `slimic:slime_silt` |
| Slimic Clay | `slimic:slimic_clay` |
| Slime Mud | `slimic:slime_mud` |
| Congealed Slime | `slimic:congealed_slime` |
| Dried Slime | `slimic:dried_slime` |
| Hardened Slime | `slimic:hardened_slime` |
| Luminous Slime | `slimic:luminous_slime` |
| Gel Glass | `slimic:gel_glass` |
| Slime-Infused Planks | `slimic:slime_infused_planks` |

Stairs and slabs: Slimic Stone, Slimic Cobblestone, Slimic Stone Bricks, Mossy Slimic Stone Bricks, Slimic Tiles, Slime Marble, Black Slime Stone, and Slime-Infused Planks. Walls: Slimic Cobblestone, Mossy Slimic Cobblestone, Slimic Stone Bricks, Mossy Slimic Stone Bricks, Slimic Slate Bricks, and Slime Marble Bricks.

The **Slime Restoration Station** (`slimic:command_pedestal`) appears beside the ancient seal in the main Slimic Creative tab. See [core restoration](FORGOTTEN_ORDERS.md#restore-a-remnant) for its materials and procedure.

## Decorations

The separate **Slimic Decorations** Creative tab has **31 entries**. Most solid props face their placer, require a sturdy upper floor face, have matching collision shapes, and support waterlogging. Removing their support drops the prop and preserves contained water. Plants need suitable soil. Lighting follows each block's visible design; the Dormant Beacon is unlit.

Crates, urns, racks, basins, furniture, and broken workbenches are ornamental. Use an actual Slime Chest for item storage and companion logistics, and an ordinary crafting table for crafting.

| Decoration | Resource ID |
| --- | --- |
| Gel Lantern | `slimic:gel_lantern` |
| Ancient Brazier | `slimic:ancient_brazier` |
| Dormant Beacon | `slimic:dormant_beacon` |
| Cracked Slime Restoration Station | `slimic:cracked_command_pedestal` |
| Fallen Pillar | `slimic:fallen_pillar` |
| Broken Pillar | `slimic:broken_pillar` |
| Royal Waymarker | `slimic:royal_waymarker` |
| Weathered Waymarker | `slimic:weathered_waymarker` |
| Royal Crest Stone | `slimic:royal_crest` |
| Stone Tablet | `slimic:stone_tablet` |
| Cracked Stone Tablet | `slimic:cracked_stone_tablet` |
| Abandoned Crate | `slimic:abandoned_crate` |
| Broken Crate | `slimic:broken_crate` |
| Supply Urn | `slimic:supply_urn` |
| Cracked Supply Urn | `slimic:cracked_supply_urn` |
| Tool Rack | `slimic:tool_rack` |
| Broken Workbench | `slimic:broken_workbench` |
| Ruined Bench | `slimic:ruined_bench` |
| Low Stone Table | `slimic:low_stone_table` |
| Stone Bench | `slimic:stone_bench` |
| Gel Basin | `slimic:gel_basin` |
| Dried Gel Basin | `slimic:dried_gel_basin` |
| Relic Shard Pile | `slimic:relic_shard_pile` |
| Fallen Masonry Pile | `slimic:fallen_masonry_pile` |
| Gel Crystal Cluster | `slimic:gel_crystal_cluster` |
| Small Gel Crystal Cluster | `slimic:small_gel_crystal_cluster` |
| Old Slime Banner | `slimic:old_slime_banner` |
| Slime Roots | `slimic:slime_roots` |
| Slime Tuft | `slimic:slime_tuft` |
| Gel Cap | `slimic:gel_cap` |
| Glowing Bloom | `slimic:glowing_bloom` |

## Ruins and records

Scattered workshops, supply depots, road fragments, archives, and royal wards use local masonry, broken furniture, lamps, and weathered markers. Newly generated occupied ruins contain ancient guards, carriers, and sometimes command slimes still obeying the lost kingdom's orders. Their actual Slime Chests move supplies, while ordinary chests contain supplies and translated records, with a chance to find a command seal.

Defeat a remnant and restore its core at an intact Slime Restoration Station, or use a seal to specialize an existing Combat Slime. See [Forgotten Orders](FORGOTTEN_ORDERS.md) for both acquisition paths, occupied depots, archives, and old royal wards. Opening an ancient Slime Chest removes its carrier link. The intact pedestal now restores cores; other ornamental furniture retains its decorative role. Records describe the absent XXX, the kingdom's fading supply routes, and the last watch.

## Collapsed mines

Collapsed mines can appear in newly generated chunks of The Slimic, on dry ground with little surface slope. Look for timber entrance frames, abandoned equipment, or a visibly open quarry shaft. Existing chunks are not regenerated.

- **Tunnel sites** use a descending entrance and a 3×3 horizontal passage, with support frames, rubble, cobwebs, and a solid unfinished face.
- **Chunk quarries** expose a 16×16 excavation 12–18 blocks deep, leaving the Mining Slime’s two-block-wide perimeter stairs. A remaining lower ledge marks an interrupted layer. Stair protection persists with the world and applies to later mining companions.

Mineralized corpses drop one damaged core when broken. Recover Old Command Fragments and slimeballs from the mine’s supply chest, then restore Ancient Mining or Ancient Tunnel Slimes in the Overworld. Remaining workers and Mine Guardians attack intruders. See [Collapsed Mine Recovery](MINE_RECOVERY.md) for the full procedure and older empty remains. Slimeballs cannot revive a corpse directly; owned companions retain their ordinary revival procedure.

Generation stays within one chunk and rejects wet sites, existing block entities, unbreakable blocks, and non-terrain construction before carving.

## Companion travel

Unmounted companions can enter slime portals. Their UUID, owner, health, stamina, stat upgrades, equipment, and cargo are preserved. **Every companion arrives in Wait.** Work origins follow each profession's relocation rules; miners establish a new quarry plan in the new dimension. Inspect the destination with a Slime Scanner and explicitly start work when ready.

Recovery and item release also work across dimensions. Release starts in Wait and follows the existing [preservation rules](GAMEPLAY.md#recovery-and-preserved-traits). Shared-owner and Command population rules continue to apply across the entire save.

## Abandoned assembly chambers

[Assembly Chambers](AIMLESS_COLONY.md) add a visible surface stair and an underground arena to new Damp Plains terrain. Their world-owned encounters, observer records and personal rewards lead to reversible [Colony Bonding](COLONY_BONDING.md). Existing sanctums, mines and old chunks retain their own progress and terrain.
