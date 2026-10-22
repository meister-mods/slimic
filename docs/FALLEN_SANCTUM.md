# Fallen Sanctum

English | [한국어](FALLEN_SANCTUM.kr.md) · [The Slimic](THE_SLIMIC.md) · [Royal synthesis](SYNTHESIS.md)

The Fallen Sanctum is an abandoned royal command sanctuary in The Slimic. Its broken defenses, contradictory orders, laboratory, and surviving watch connect exploration to a new companion generation. A map helps locate it; entry does not require one.

## Finding a sanctum

New collapsed-mine supply chests contain **one Torn Kingdom Map Fragment**. New workshop, depot, archive, and royal-ward loot tables can also provide a fragment, with a default independent 25% chance. Already filled chests keep their contents. Unopened loot-table chests use the current table when first opened. Combine **three fragments shapelessly** to make one Ancient Kingdom Map.

Use an unbound map in The Slimic. It searches nearby valid native `slimic:fallen_sanctum` placement candidates and binds once to an anchor and dimension. Default radius is **160 chunks**, configurable through `sanctumMapSearchRadiusChunks` in the saved world's server configuration. The bounded search predicts terrain and reads saved metadata without requesting chunks or force-loading them. A failed search preserves the map; move elsewhere and retry.

Bound maps retain their destination through containers, travel, logout, and server restarts. Native map markers show the approximate site center and the player's relative position and facing. Distance guidance is rounded to about 32 blocks. Outside The Slimic, the item identifies the required dimension. Combine a bound map with blank vanilla maps to copy its destination. Copies share no new reward entitlement.

## Generation and the five zones

Sanctums generate in **new damp-plains chunks**, using five connected native structure pieces across an approximately 64×80 footprint. Placement defaults to **96-chunk spacing and 32-chunk separation** in the structure-set datapack. These are placement parameters, not guaranteed travel distances. Unsuitable wet or steep terrain is rejected. Existing full chunks without this structure and foreign construction or block entities prevent placement; old chunks are not regenerated. Nearby small-ruin and collapsed-mine generation reserves the sanctum footprint.

| Zone | Landmarks and route | Watch and progress |
| --- | --- | --- |
| Outer approach | Broken road, royal markers, fallen banners, gate silhouettes | A small ancient watch |
| Courtyard | Ruined defenses and a mineralized miner | Shield remnants; courtyard relay remembers seal A |
| Command gallery | Damaged command furniture and the central opening | Commander and escorts; gallery relay remembers seal B and grants a survey compass |
| Synthesis laboratory | Gel crystals, basins, and a damaged synthesis apparatus | Shield watch; records explain synthesis |
| Deep core | Descending central passage and the final command relay | Elite guardian; royal core after both chamber memories and this watch are cleared |

Use the **gold-topped Sanctum Relay** in each chamber after clearing its watch. Seals A and B become persistent site memories as well as physical keepsakes. Losing, storing, or discarding the keepsakes does not block progression. The laboratory's ruined apparatus cannot perform synthesis; craft a new [Royal Synthesis Station](SYNTHESIS.md).

The gallery's **Royal Survey Compass** points horizontally toward the next inner chamber when used near the sanctum. Its message indicates direction or proximity, without exposing an exact height or hidden-door coordinate. It cannot teleport and has no useful sanctum bearing far away or outside The Slimic.

The ruins may be dismantled or remodeled. A destroyed relay can be crafted again from one Old Command Fragment above three stone bricks and placed in its original socket. Moving or replacing it does not reset the site ledger.

## Royal remnants

Existing ancient entities take site-bound equipment and roles. Shield remnants raise shields against visible nearby targets; wardens guard their rooms; the gallery commander alerts nearby allies. The deep elite has 160 base health and can call one shield reinforcement after falling to half health. The watch stays near its post and does not load distant chunks.

Combat follows existing ancient alliance, home-range, damage-event, and companion protection rules. In **Peaceful**, the relays allow noncombat progression, including both seals and the final core. Revisiting or reloading a chamber does not repeatedly populate its watch.

## Rewards and records

Each sanctum provides **one shared Royal Synthesis Core for the saved world**, awarded to the player who successfully claims its deep relay. Both chamber memories and a cleared deep watch are required. This is a site reward, not a reward per player or map. Find another sanctum for another core. The recipient can retry an interrupted delivery at that relay. Breaking blocks, copying maps, revisiting chunks, or restarting the server does not create a second core.

Guaranteed ordinary chest supplies cover the station and first synthesis reagents: three gold ingots, six stone bricks, four redstone, an Ancient Command Seal, six slimeballs, and three Old Command Fragments. These also cover crafting one ordinary restoration station and restoring the courtyard mining core. Bring or raise the ordinary Mining Slime parent separately. A spare pickaxe and cobblestone support the journey. Optional loot is controlled by the four `chests/sanctum_*` tables.

Four bilingual records remain: **The Gate Still Holds**, **Two Final Orders**, **A Body for Two Memories**, and **The Order That Remains**. Their slime authors describe nearby paths and the kingdom's failing supply and command network. References to `XXX` leave the absent figure's identity and fate unresolved. Breaking the courtyard's mineralized miner yields a core for the existing [mine recovery](MINE_RECOVERY.md) procedure.
