# Fallen Sanctum

English | [한국어](FALLEN_SANCTUM.kr.md) · [The Slimic](THE_SLIMIC.md) · [Royal synthesis](SYNTHESIS.md)

The Fallen Sanctum is an abandoned royal command sanctuary in The Slimic. Its broken defenses, contradictory orders, laboratory, and surviving watch connect exploration to a new companion generation. A map helps locate it; entry does not require one.

## Finding a sanctum

New collapsed-mine supply chests contain **one Torn Kingdom Map Fragment**. New workshop, depot, archive, and royal-ward loot tables can also provide a fragment, with a default independent 25% chance. Already filled chests keep their contents. Unopened loot-table chests use the current table when first opened. Combine **three fragments shapelessly** to make one Ancient Kingdom Map.

Use a map without a destination in The Slimic. It searches nearby suitable sanctum locations and records one destination. Default radius is **160 chunks**, configurable through `sanctumMapSearchRadiusChunks` in the saved world's server configuration. The search uses nearby terrain predictions and saved structure locations without loading distant chunks. A failed search preserves the map; move elsewhere and retry.

Maps retain their recorded destination through containers, travel, logout, and server restarts. Native map markers show the approximate site center and the player's relative position and facing. Distance guidance is rounded to about 32 blocks. Outside The Slimic, the item identifies the required dimension. Combine a map that has a recorded destination with blank vanilla maps to copy its destination. Copies share no new reward entitlement.

## Generation and the five zones

Sanctums generate in **new Damp Plains chunks**, with five connected zones across a **112×112-block** footprint. This is about 2.45 times the ground area of the earlier 64×80 castle. Placement defaults to **96-chunk spacing and 32-chunk separation** in the structure-set datapack; these are placement parameters, not guaranteed travel distances. Wet or steep terrain is rejected. Existing full chunks without this structure, foreign construction and block entities prevent placement. Nearby small ruins and collapsed mines reserve the expanded footprint.

The fortress has a twin gatehouse, an inner dry defensive ditch crossed by a timber bridge, crenellated curtain walls and eight watchtowers. A tall slate-roofed keep and two side wings provide **three main storeys**, with a lower vault containing the final watch. Divided courtyards, barracks, record rooms, armories, galleries and laboratory furniture distinguish the spaces. Roofs, parapets and towers form a layered silhouette rather than one enclosing box.

The route deliberately turns away from the old straight central passage. Staggered gates, partitions, side rooms, bridges and multiple stairs create branches, loops and dead ends. A room visible across a hall may require approaching from another floor. Upper galleries and the wings offer alternate circuits; furnishings and banners help identify places already visited. Keep track of stairs and openings, and use the survey compass as a bearing rather than a turn-by-turn route. Blocks remain breakable and the ruins can still be remodeled.

This larger layout applies to **sanctums newly generated from 0.5.5**. Existing castles and previously saved unfinished structure pieces retain their earlier geometry, including their original routes. Recorded map destinations, relay sockets, encounters, chests and claimed reward records remain intact. The four inner chambers keep their existing progression:

| Zone | Landmarks and route | Watch and progress |
| --- | --- | --- |
| Outer approach | Dry ditch, timber bridge, twin gatehouse, royal markers | A small ancient watch |
| Courtyard | Ruined defenses and a mineralized miner | Shield remnants; courtyard relay remembers seal A |
| Command gallery | Damaged command furniture, record rooms and upper bridges | Ancient Command Slime and escorts; gallery relay remembers seal B and grants a survey compass |
| Synthesis laboratory | Gel crystals, basins, and a damaged synthesis apparatus | Shield watch; records explain synthesis |
| Deep core | East-side descent and the final command relay | Elite guardian; royal core after both chamber memories and this watch are cleared |

Use the **gold-topped Sanctum Relay** in each chamber after clearing its watch. Seals A and B become persistent site memories as well as physical keepsakes. Losing, storing, or discarding the keepsakes does not block progression. The laboratory's ruined apparatus cannot perform synthesis; craft a new [Royal Synthesis Station](SYNTHESIS.md).

The gallery's **Royal Survey Compass** points horizontally toward the next inner chamber when used near the sanctum. Its message indicates direction or proximity, without exposing an exact height or hidden-door coordinate. It cannot teleport and has no useful sanctum bearing far away or outside The Slimic.

The ruins may be dismantled or remodeled. A destroyed relay can be crafted again from one Old Command Fragment above three stone bricks and placed in its original socket. Moving or replacing it does not reset the site ledger.

## Royal remnants

Ancient slimes guard the sanctum with equipment suited to their posts. Shield remnants raise shields against visible nearby targets; wardens guard their rooms; the gallery’s Ancient Command Slime alerts nearby allies. The deep elite has 160 base health and can call one shield reinforcement at or below half health. The watch stays near its post and does not load distant chunks.

Combat follows existing ancient alliance, home-range, damage-event, and companion protection rules. In **Peaceful**, the relays allow noncombat progression, including both seals and the final core. Revisiting or reloading a chamber does not repeatedly populate its watch.

## Rewards and records

Each sanctum provides **one shared Royal Synthesis Core for the saved world**, awarded to the player who successfully claims its deep relay. Both chamber memories and a cleared deep watch are required. This is a site reward, not a reward per player or map. Find another sanctum for another core. The recipient can retry an interrupted delivery at that relay. Keep empty inventory slots for missing rewards. If the relay reports a storage failure, resolve the storage problem and retry; rewards already in the inventory need no additional slots to confirm delivery. Breaking blocks, copying maps, revisiting chunks, or restarting the server does not create a second core.

Guaranteed ordinary chest supplies cover the station and first synthesis reagents: three gold ingots, six stone bricks, four redstone dust, an Ancient Command Seal, six slimeballs, and three Old Command Fragments. These also cover crafting one Slime Restoration Station and restoring the courtyard mining core. Bring or raise the ordinary Mining Slime parent separately. A spare pickaxe and cobblestone support the journey. Optional loot is controlled by the four `chests/sanctum_*` tables.

Four localized records remain: **The Gate Still Holds**, **Two Final Orders**, **A Body for Two Memories**, and **The Order That Remains**. Their slime authors describe nearby paths and the kingdom's failing supply and command network. References to `XXX` leave the absent figure's identity and fate unresolved. Breaking the courtyard's mineralized miner yields a core for the existing [mine recovery](MINE_RECOVERY.md) procedure.
