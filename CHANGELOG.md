# Changelog

This project follows [Semantic Versioning](https://semver.org/).

## [Unreleased]

## [0.5.15] - 2026-10-26

### Changed

- Extend The Aimless Colony's sweeping gel into a visible arm, with a larger wind-up and elastic return. Strengthen charge compression, launch, braking and rebound.
- Keep the extended sweep visible when its body leaves the edge of the screen.

### Fixed

- Align the sweeping body with the moving attack edge after Minecraft's model-axis mirroring. Attack timing, reach and damage retain their existing values.

## [0.5.14] - 2026-10-26

### Changed

- Give The Aimless Colony a directional sweep that travels across its announced sector after the full warning. Swings can begin from either side; the moving edge shows the remaining attack area.
- Add distinct charge compression, stretching, braking and settling, sweeping body twists, and secondary movements for the other attack preparations. Eyes and cores follow the body deformation.
- Add a brief, harmless recovery after charges and sweeps before any follow-up warning.
- Require matching protocol 27 clients and servers for the synchronized swing direction and recovery motion. Existing saves remain readable; unfinished attacks reset on reload.

### Fixed

- Check charge damage along actual collision-clipped movement, including the final step and diagonal footprint corners. Prevent extra horizontal travel after the charge and stop it at the encounter boundary.
- Keep the attack-facing body pose aligned with the frozen warning direction instead of following a changed target.


## [0.5.13] - 2026-10-25

### Added

- Add Japanese and Spanish translations for items, companions, screens, status messages, JEI information, and slime records.
- Translate all seven categories, 73 entries, and 239 pages of the in-game companion guide into both languages. Unsupported languages continue to use English.

### Changed

- Validate matching language keys, formatting arguments, companion names, guide structures, links, and recipes across all four supported languages.
- Extend client checks to Japanese and Spanish guidebook pages, localized search, and records.

## [0.5.12] - 2026-10-25

### Changed

- Keep each colony member’s own growth-cost tier for unbinding. While bound, the colony uses the higher requirement; unbinding adds only the points actually earned together to each member’s original tier.
- Convert unfinished activity in proportion to each member’s next-point requirement when binding and unbinding.
- Clarify the difference between activity needed to earn a point and the one shared point spent on an upgrade. Differences in activity requirements do not grant refund points.
- Add English and Korean examples to the colony guide and in-game guidebook, retaining point averaging, copied balances, and cap-aware upgrades.

## [0.5.11] - 2026-10-25

### Changed

- Give bound Transport and Combat Slime Colonies one shared pool of unused stat points, starting at the two members’ arithmetic mean rounded down. Spending one point upgrades the chosen stat for each member below its cap, without resetting existing investments. Capped members remain unchanged; if both are capped, no point is spent.
- Halve colony activity growth while any stat used by the role can still upgrade both members together. Otherwise use the normal rate, including continued point storage at full caps. Keep the ordinary point-cost formula and preserve unfinished half units through saving and unbinding.
- Copy the remaining shared point balance to each original member when unbinding.
- Apply Creative unused-point edits to the shared pool. Direct stat editing retains the existing colony aggregation rules.
- Update the English and Korean documentation, guidebook entries, and bonding screen explanation for shared training and unbinding.

### Fixed

- Apply only edited Creative stat fields, preserving member stat differences and existing capacities when changing only unused points. Preserve odd Combat colony capacities and split Transport capacity in whole multiplier units.
- Reject colony capacities whose combined storage cannot be represented before changing any values, preventing oversized bags from becoming unreadable after saving.

## [0.5.10] - 2026-10-25

### Changed

- Give the Transport Slime Colony two separate moving bodies joined by a visible cord. Each body can collect cargo, deposit shared cargo, or deliver supplies while the other handles a different request.
- Coordinate worker requests and reserve fetched supplies so the second body cannot send an active delivery back to storage. Both bodies share the existing inventory, command, health, stamina, and growth records.
- Allow either body to open the colony's management tools or recover the entire colony, with the same ownership and interaction-distance checks. Wait stops both bodies; recovered colonies still return in Wait.
- Preserve existing member identities, stored cargo, and bonding data. The second body is recreated from the representative on loaded, safe terrain and has no separate saved inventory or summon item.
- Keep the combat colony's paired armor appearance unchanged. Update the English and Korean guides, guidebook entries, and transport colony description.
- Require matching network protocol 26 clients and servers for the linked body and its synchronized connection.

## [0.5.9] - 2026-10-25

### Fixed

- Reject recovery while a companion is still being admitted, preventing reentrant spawn callbacks from creating a second stored copy. Remove recovered or transferred originals before dropping their leads so item callbacks cannot recover them again.
- Preserve the original companion when cross-dimension gathering is canceled. Commit the move only after the destination copy passes admission, ownership, and landing checks.
- Release combat targets that have moved to another dimension, including targets pursued by Dragon Slimes.
- Report the actual missing pickaxe or shovel for ordinary and next-generation Tunnel Slimes.
- Respect world borders and block interaction permissions when repairing quarry stairs, including changes made during placement callbacks.
- Pause field work at unloaded terrain boundaries so harvesting and planting do not load neighboring chunks through block updates; resume when the surrounding terrain is available.
- Bound Scanner display geometry for very large Creative detection ranges and distant registered farms while preserving the actual stats and work data.
- Keep the Saddle Slime's previous command when a mount event is canceled.
- Charge Dragon Slime breath stamina and award activity when a hit reduces absorption hearts, even if normal health is unchanged. Canceled damage still has no cost.
- Reject harmful contact blocks at companion and Ender teleporter landing positions, including hazards introduced by teleport callbacks.
- Keep sanctum reward receipts in the saved player state so interrupted world acknowledgements cannot issue consumed rewards again. Preserve reward and colony recovery receipts when the player respawns.
- Preserve companions when admission of a dropped recovery item is canceled during collapse.
- Correct stale network protocol references in the Korean development guide.

## [0.5.8] - 2026-10-25

### Changed

- Enlarge the Aimless Colony with eight uneven body lobes, offset eyes, exposed gold cores, and a new pixel-art skin. Add crawling waves and attack-specific compression while retaining the readable, mostly opaque shell.
- Diversify its attacks and movement with marked sweeps, clustered eruptions, distance-aware attack selection, and short approach, circling, and retreat steps.
- Require matching network protocol 25 clients and servers for the expanded attack set. Existing encounter identities, companion records, cohesion, and rewards remain compatible.
- Update the English and Korean guides and texture documentation without revealing a detailed encounter walkthrough.

### Fixed

- Check the entire moving body and its supporting ground against obstacles, fluids, borders, and unloaded terrain. Apply the same landing checks to new fragments and forced movement.
- Prevent the colony from pulling targets through walls, and clear unfinished attacks and movement when loading a saved boss.
- Keep a displaced colony returning toward the chamber interior instead of stopping at its outer edge.

## [0.5.7] - 2026-10-25

### Added

- Add thirteen crafting and smelting routes for Slimic masonry, mossy variants, carved blocks, and cracked bricks and tiles. Existing stonecutter routes remain available.

### Changed

- Align English and Korean documentation, guidebook pages, item descriptions, JEI instructions, and status messages with consistent terminology and clear procedures.
- Group translation keys by function and keep the same key order in both language files for easier comparison and maintenance.
- Keep companion instructions factual and slime records in a simple, reflective voice. Clarify core restoration, map use, initial Wait mode, and work interruption notices.
- Shorten JEI descriptions around acquisition, use, and essential conditions. Replace detailed advance explanations of the Aimless Colony's attacks with exploration and encounter guidance.

### Fixed

- Correct outdated guidebook descriptions, Creative tab ordering in gameplay documentation, synthesis preparation instructions, and development protocol references.
- Correct Korean particles and distinguish companion recovery, cargo collection, colony bonding, and map destinations.
- Give seven decorations distinct recipes so wooden furnishings and luminous ornaments no longer compete for the same workbench inputs.

## [0.5.6] - 2026-10-24

### Changed

- Group Creative companions by role and upgrade line, followed by management tools, armor, functional stations, expedition supplies, records, and hostile spawn eggs. Keep each building material beside its stairs, slabs, and walls, and group decorations by use.
- Clarify English and Korean companion instructions, work interruptions, restoration materials, and reward requirements while retaining The Slimic as the dimension name.

### Fixed

- Remove duplicated direction suffixes from Korean survey-compass messages and distinguish cargo collection from companion recovery.
- Give assembly clues their own search and destination messages instead of describing a Fallen Sanctum.
- Replace basic specialization guidance on Glow, Command, and Ender Slime management screens with their own ability instructions.

## [0.5.5] - 2026-10-24

### Changed

- Expand newly generated Fallen Sanctums to a 112×112 fortress with three main storeys, a lower vault, eight watchtowers, a twin gatehouse and furnished side wings.
- Replace the straight expedition corridor with staggered entrances, branching rooms, cross-floor circuits, bridges and alternate stairs. Existing relay, watch and reward locations retain their progression rules.
- Save the new fortress as architecture version 4. Existing structures and saved unfinished version 0–3 pieces keep their original layout and bounds.
- Update the English and Korean exploration guides, in-game guidebook and slime records for the larger, winding layout.
- Increase the Aimless Colony body opacity to 85% and unify localized terminology, retaining The Slimic as the dimension name in both languages.

## [0.5.4] - 2026-10-24

### Changed

- Increase the opacity of the Aimless Colony and its fragments so their bodies remain visible while their internal cores can still be seen.
- Use 16×16 pixels for the eight world material textures and each of the portal's sixteen animation frames. Preserve the native Slime Chest UV atlas and pixel density.
- Update the English and Korean texture documentation and native atlas checks for the reduced material resolution.

## [0.5.3] - 2026-10-23

### Fixed

- Revalidate a boss target's dimension and distance before starting or chaining attacks, preventing follow-ups at a stale teleported position. Already announced crossing attacks keep their marked position.
- Match the Aimless Colony's charge warning to its directional collision area and keep its locked heading synchronized when the body is pushed or the target moves.
- Compact obsolete colony bag spans after explicit sorting so reduced Capacity does not leave empty pages between the two members' cargo. Ordinary transfers retain stable slot indices.
- Correct boss phase boundaries, configuration reload behavior and locked bag slot descriptions in the English and Korean documentation.

### Changed

- Network protocol 24 requires matching clients and servers for synchronized charge headings; existing companion data remains compatible.

## [0.5.2] - 2026-10-23

### Changed

- Add annular shockwaves, fixed crossing attacks and fully telegraphed follow-up sequences to the Aimless Colony while retaining encounter health, cohesion and rewards.
- Expand newly generated Fallen Sanctums with side record rooms, armories, covered passages and two upper-gallery bridges; saved architecture and site progress remain intact.
- Network protocol 23 requires matching 0.5.2 clients and servers for the added combat patterns and bag sorting action. Companion schema 1 and existing saved-world identities remain compatible.
- Add a management Sort action that merges matching cargo and organizes all bag pages, including both real member bags in a colony. Equipment and player inventories remain separate.
- Display the active armor on both Combat Slime Colony bodies, including material, leather dye and enchantment glint, while preserving the other member's stored equipment.
- Craft a Slime Tuner with a slimeball directly above an iron ingot in a shaped vertical recipe.
- Separate neutral guidebook instructions from slime-authored records. Rewrite ruin, royal, sanctum and assembly records as short recollections from the slimes’ perspective in English and Korean, preserving their clues and references to XXX.

### Fixed

- Keep JEI item information translated after changing the game language and localize the archive byline on sanctum records.
- Preserve translated parent names in newly synthesized companion items while retaining custom names and older saved text.

## [0.5.1] - 2026-10-23

### Fixed

- Show fractional management stats, including bonded attack and defense, without rounding them to whole numbers.

## [0.5.0] - 2026-10-23

### Added

- The Aimless Colony, a translucent slug-shaped boss with three phases, visible attack warnings, three fragment roles, conserved cohesion, limited reabsorption and personal rewards.
- Abandoned Assembly Chambers in new The Slimic terrain, permanent stair access, Assembly Clues, observer records, free failed-attempt retries, paid repeat encounters and a one-time Peaceful alternative.
- Colony Hearts, Cohesion Shards and reusable Colony Bonding Stations. Bind exact ordinary Transport or Combat pairs and restore the original two identities through safe unbinding.
- Member-owned cargo, equipment, growth and remaining health/stamina, independent membership and release records, recoverable pending delivery, bilingual guides and optional JEI/Patchouli information.

### Changed

- Network protocol 22 requires matching clients and servers. Existing companion identifiers, companion schema 1, sanctum progress and Royal Synthesis records remain compatible.
- Preserve stored cargo when Creative capacity is reduced; the insertion limit continues to follow the configured capacity.

### Validation

- Native integration and focused regressions cover companion behavior, conserved fragments, personal reward receipts, reversible membership, Forge cancellation and existing Royal Synthesis. Optional JEI/Patchouli combinations pass dedicated coverage.
- Native server restarts retain original identities, cargo, remaining health, weapon wear, release epochs and encounter mass. Nine automated survival scenarios cover one, two and four players with zero, five and twenty companions; their timings do not establish first-attempt human balance.
- English/Korean clients verify station transactions, both member appearances, boss phases, minimal-particle warnings, JEI and texture replacement/restoration. All 73 guide entries and 237 pages pass native default/uniform-font checks. Java 17 packaging, bilingual resources and Google Java Format checks pass.

## [0.4.9] - 2026-10-23

### Fixed

- Restore Next-Generation Tunnel bag/status labels without the unrelated base-companion specialization message or overlapping size controls.
- Preserve the pending Precision Prospecting destination after a temporary movement timeout, allowing an already excavated descent to resume.
- Recover older stranded precision saves when their missing next step is an adjacent, already cleared, safe shaft level.
- Skip ore-free loaded chunk sections using their block palettes instead of repeatedly inspecting an entire empty survey cube. Limit each tick to 32 section checks and 1,024 candidate blocks without loading terrain.
- Use consistent Wait, Follow, and stamina-recovery status text across professions in English and Korean. Update precision behavior in both documents and the in-game guide.

### Validation

- All 29 final native precision/core checks pass without optional mods; all seven stability checks pass with JEI and Patchouli. The expanded integration profile passes 193 cases. Coverage includes empty surveys, stalled and legacy descents, unsafe-recovery refusal, default-speed work, exhaustion recovery, section boundaries, live palettes, and ore filters.
- Native English/Korean clients verify Tunnel bag and size labels, all 30 shared status keys, actual synthesis/size packets, both JEI families, all 27 companion appearances, armor, and accessory resource replacement/restoration. Google Java Format, bilingual resources, packaged assets, and the Java 17 build pass verification.
- All 67 guide entries and 217 pages render in English, Korean, and fallback with both default and uniform fonts, with no text-layout problems.

## [0.4.8] - 2026-10-22

### Fixed

- Check the entire Next-Generation Tunnel face and lining against build height and the world border before modifying that face, preventing partial material use at invalid boundaries.
- Resume face inspection and excavation when a new block obstructs forward movement after the final scan or a saved movement phase.
- Calculate lining coordinates on demand instead of allocating the complete perimeter every work tick. Preserve the incremental scan and single-action limits.
- Synchronize current protocol references in the bilingual development guides and update tunnel boundary behavior in both documents and the in-game guide.

### Validation

- All 26 final native core checks pass without optional mods, including six independent review scenarios for boundaries, changed terrain, shared-face conservation, and odd/even bores in all horizontal directions.
- All 266 expanded native integration checks pass with JEI and Patchouli, covering synthesis, supply, recovery, tools, protection callbacks, and existing companion interactions.
- All 67 guide entries and 217 pages pass in English, Korean, and fallback with both fonts. Bilingual resources, packaged assets, Google Java Format, and the Java 17 build pass verification.

## [0.4.7] - 2026-10-22

### Added

- Next-Generation Tunnel Slimes, synthesized from matching ordinary and Ancient Tunnel parents. Configure a positive side length n for an n×n bore, with compact body/cart dimensions, goggles, and a dedicated caliper crest.
- Server-authoritative size controls, incremental excavation and lining, fluid-cell tracking beyond 32 cells, scanner previews, saved work progress, inherited ancient efficiency, and bilingual guidebook/JEI information. New and recovered companions start in Wait.

### Changed

- Enable Tunnel Royal Synthesis with the existing ownership, growth refund, inventory, transaction, and pending-result safeguards. Network protocol 21 requires matching clients and servers; persistent companion schema and existing registry IDs remain unchanged.

### Fixed

- Revalidate actual Tunnel tools and commands around block-break callbacks. Stop expanded faces at missing neighbor chunks before native block notifications, and bound fluid actions even when extreme Creative stamina rounds away a work cost.

### Validation

- All 260 focused native server checks pass with JEI and Patchouli; 20 final core checks pass without either optional mod. Coverage includes 1×1, 2×2, 5×5, and 7×7 bores, physical movement, exact tools/cargo, lining supply, interrupted saves, reentrant size changes, loaded boundaries, synthesis, and existing companion interactions.
- Native English/Korean clients perform Tunnel synthesis and real size packets, inspect both JEI families and all 27 companion appearances, and replace/restore accessory resources. All 67 guide entries and 217 pages pass in English, Korean, and fallback with both fonts. Bilingual resources, packed assets, Google Java Format, and the Java 17 build pass verification.

## [0.4.6] - 2026-10-22

### Fixed

- Use the shared flat Gold Block plate for Ancient Mining and Ancient Tunnel summon items while retaining their distinct core crests.
- Document the 64×64 prospecting-goggle atlas separately from the 256×256 accessory atlases and include it in both texture reference tables.

### Validation

- All 751 native server cases pass with JEI and Patchouli, including complete chunk excavation, safe exit stairs, precision prospecting, transport, restoration, synthesis, combat, riding, world generation, and saved companion interactions.
- Native clients verify all 26 summon models after English and Korean resource reloads: full-UV flat Gold Block plates, the shared slime base, and distinct ancient core crests. All 26 companion appearances, eight armor variants, and resource-pack replacement and restoration pass.
- All 66 guide entries and 213 pages render in English, Korean, and fallback with default and uniform fonts. Item lookups, internal links, localized searches, bilingual resources, packaged assets, Google Java Format, and the Java 17 build pass verification.

## [0.4.5] - 2026-10-22

### Changed

- Give Next-Generation Mining Slimes the ancient miner’s half-stamina-cost passive in every policy, and reduce their physical and rendered size to 75% of an ordinary companion.
- Replace Selective Ore with Precision Prospecting: physical 2×2 shafts, incremental through-wall ore surveys, supported 2×2 branch excavation, stepped routes, and exposed-ore priority. Avoid unsupported cavities, fluids, unsafe roofs, protected stairs, and containers; attempt lateral shaft bypasses and stop when no safe route exists.
- Preserve identity, growth, tools, cargo, ore masks, and policy ordinal 2. Save prospecting fronts and return trails, deploy recovered companions in Wait, and pause legacy Selective Ore workers before newly destructive excavation.
- Add textured safety goggles and move the royal crest to the helmet side while retaining the headlamp. Update policy descriptions, scanner previews, bilingual documents, and the in-game guide.

### Validation

- All 163 focused native server cases pass with JEI and Patchouli; 35 core cases pass without optional mods. Coverage includes physical 2×2 descent, hidden ores at different heights, safe returns, lava and cavity bypasses, gravity floors, protected blocks and stairs, real tool supply, full bags, live filters, interrupted saves, recovery, and no extra chunk tickets.
- Native clients verify compact size, goggles, the side crest, dye colors, bilingual policy controls, and synthesis. The revised guide renders in English, Korean, and fallback with both fonts. Bilingual resources, packaged assets, Google Java Format, and the Java 17 build pass verification.

## [0.4.4] - 2026-10-22

### Changed

- Add faded royal standards, projecting stone trim, entrance arches, a diamond-shaped glazed keep window, and watchtower corbels to newly generated Fallen Sanctums.
- Furnish barracks, gate chambers, watchtowers, supply awnings, record galleries, and laboratory benches. Add a timber well windlass, chain-hung chandeliers, floor inlays, and deep-chamber relic alcoves while retaining access to the expedition route and devices.
- Preserve saved castle pieces with their earlier layout through architecture version 2. Update the English and Korean exploration documents and in-game guide.

### Fixed

- Clear overlapping curtain-wall masonry from new watchtower ground rooms and add keep posterns to the inner curtain lanes, making the rear rooms accessible without dropping from the wall walks.

### Validation

- Seventy-three focused native server checks pass with JEI and Patchouli; six final architecture checks pass without optional mods. Four rotations preserve reachable devices, remains, upper walks, and all four tower ground rooms. Reversed clipping order, legacy layouts, protected construction, and companion navigation remain valid.
- Native clients inspect fourteen exterior and interior views plus four focused postern and rear-room views. The revised guide entry renders in English, Korean, and fallback with both fonts. Bilingual resources, packaged assets, Google Java Format, and the Java 17 build pass verification.

## [0.4.3] - 2026-10-22

### Changed

- Rebuild newly generated Fallen Sanctums as royal castles with a dry defensive ditch, timber bridge, twin gatehouse, curtain walls, four octagonal watchtowers, and a tall slate-roofed keep.
- Add usable wall stairs, tower ladders and landings, upper galleries, a barracks, a dry well, command furniture, hanging lamps, and a vaulted deep chamber. Concentrate collapse damage in the eastern defenses and laboratory roof while preserving the main expedition route and chamber devices.
- Preserve generated sanctums and saved unfinished structure pieces through a versioned architectural layout. New castles retain the existing footprint, five zones, maps, encounters, loot, and shared reward records.


### Validation

- All 72 focused native server cases pass with JEI and Patchouli, covering generation, maps, encounters, rewards, restoration supplies, synthesis, and the guide. Seven checks on another world seed and five final architecture checks pass without optional mods.
- Four rotations preserve a five-wide route, reachable devices and upper walks, unobstructed watch positions, and native companion navigation. Reversed chunk/piece order produces identical blocks; saved layouts and protected construction or containers remain intact.
- Native clients inspect exterior and interior views, and the updated castle entry renders in English, Korean, and fallback with both fonts. Bilingual documents, packaged assets, Google Java Format, and the Java 17 build pass verification.

## [0.4.2] - 2026-10-22

### Changed

- Name working slime types after their activities: Logging Slime, Signaling Slime, Command Slime, Ancient Command Slime, and Forgotten Mining Slime. Align English items, entities, spawn eggs, guide entries, and current documentation with the established Korean names.
- Rename Java classes, registry constants, menus, packets, and recipe kinds to action names while retaining persistent IDs, resource paths, configuration keys, and NBT keys. Decouple ancient core save names from enum names to preserve existing cores.
- Use the complete Next-Generation Mining Slime name in the guide and wrap its opening heading within the page.
- Document the naming convention and verify matching item, entity, and guide-entry names in both supported languages.

### Validation

- Native 0.4.1 data imports preserve all 26 companion world and stored-item payloads, waiting deployment, six hostile snapshots, six core roles, and the saved Command Slime population limit.
- All 154 focused server cases pass with JEI and Patchouli. Native guide rendering and localized searches pass for all 66 entries and 213 pages in English, Korean, and fallback text with both fonts.
- Native clients verify all 26 companion models and summon items, Command roster and relation packets, bilingual restoration screens, core names, specialization recipes, and JEI lookups.

## [0.4.1] - 2026-10-22

### Fixed

- Report storage failures accurately when retrying a Fallen Sanctum reward. Inventory-space refusals remain distinct, and a first claim without room does not reserve the shared core.
- Keep uncheckpointed sanctum rewards and synthesis results reserved rather than exposing movable items after a failed player save. Preserve existing inventory contents and recognize saved receipts on retry.
- Clarify reward retry instructions in English, Korean, and the in-game guide. Shorten two English guide headings to keep them inside the page with the default font.

### Validation

- All 52 focused native server cases pass with JEI and Patchouli present and absent. Cross-station reservations, live ore-filter changes, a forced-termination recovery, and a second cold restart also pass.
- Native English/Korean clients verify failed-save delivery, unchanged existing cargo, pending-result recovery, all 43 inherited points, and waiting deployment. The 66-entry guide is checked in English, Korean, and fallback text with both fonts; the shortened paragraph receives a focused final render check.

## [0.4.0] - 2026-10-22

### Added

- Torn kingdom maps lead to the Fallen Sanctum, a five-zone royal ruin in newly generated The Slimic terrain. Persistent chamber memories, royal remnants, a survey compass, and four bilingual records reveal its last orders.
- Each sanctum grants one shared Royal Synthesis Core. A reusable Royal Synthesis Station combines an owned ordinary Mining Slime and Ancient Mining Slime with four slimeballs and two Old Command Fragments.
- Next-Generation Mining Slimes inherit verified growth points as an unspent balance and start waiting with ordinary mining statistics. Their policies support normal quarrying, ore priority, and selected Forge ore tags.
- Synthesis previews, equipment and cargo checks, ancestry, consumed-parent records, and a recoverable result mailbox protect companion identity and stored items. Growth balances and menu synchronization preserve whole points beyond double precision.
- Unique 16×16 relic sprites, station models, optional JEI synthesis guidance, and English/Korean exploration and synthesis instructions.

### Changed

- Network protocol 20 requires matching clients and servers. Companion payload schema remains 1; existing numeric growth data and generated terrain remain compatible.

### Fixed

- Selective Ore scanner previews show the local search window inside the working chunk and detection height, without displaying a full quarry or a next excavation layer.

### Validation

- All 720 native GameTests pass without optional mods. A 719-case integration run also passes with JEI and Patchouli. Focused checks cover four structure rotations across two seeds, the exact first-synthesis resource budget, Forge spawn callbacks, and real companion combat against royal encounters.
- Native clients pass with both optional mods, either one, and neither. All 26 companion appearances and all 66 guidebook entries and 213 pages are checked in English, Korean, and fallback text. Seven loot records also pass in both fonts.
- Actual 0.3.14 save upgrades retain all 25 earlier companion professions, portals, equipment, cargo, large storage, and core records. Forced termination after a fusion commit and two cold restarts preserve one valid result and prevent consumed-parent reuse.
- A real-time hour with twenty companions preserves physical cargo and lawful growth through work pauses, saves, and actual replacement-tool delivery without acquiring extra chunk tickets.

## [0.3.14] - 2026-10-21

### Fixed

- Keep large recorded companion health visible in Commander rosters without integer truncation.
- Keep large bag page counters inside their controls and expose exact counts in paging tooltips.
- Clear invalid nonfinite absorption in older companion saves without healing or changing valid absorption.
- Keep outgoing companion damage finite when Creative Attack exceeds Minecraft’s float representation. Preserve the exact tuned base through saves and recovery while preventing nonfinite victim absorption during native melee, beams, and breath.

## [0.3.13] - 2026-10-21

- Reduce Guardian Angel and Royal Guard maximum health to four times common Health: initially 64, with 16 per ordinary Health upgrade. Normalize saved profession modifiers without stacking or healing.
- Remove Creative Slime Tuner gameplay ceilings. Values outside recommended ranges show a mild warning and remain applicable; ordinary training limits are unchanged.
- Preserve high Creative values through exact menu synchronization, recovery, saves, and companion records. Expanded bags use sparse storage and additional management pages while retaining cargo safety checks.
- Keep extreme Creative searches proportional to loaded chunks and accessible entities; preserve finite navigation radii and compact large-number displays.
- Review English and Korean guides, tooltips, and the in-game book; correct profession counts, health scaling, optional JEI restoration information, dimension travel, and technical documentation.

## [0.3.12] - 2026-10-21

### Changed

- Craft a Tunnel Slime from a Mining Slime and one regular minecart in a shapeless recipe. Update JEI, English/Korean gameplay instructions, and guidebook diagrams to match.

## [0.3.11] - 2026-10-21

### Changed

- Redraw the Ancient Command Seal at 16×16 with a stronger gold bevel, clipped dark outline, and larger purple spiral for clearer inventory visibility.

## [0.3.10] - 2026-10-21

### Fixed

- Replace the deprecated JEI restoration background API with explicit recipe dimensions, preserving the 168×121 layout.

## [0.3.9] - 2026-10-21

### Added

- A JEI Slime Restoration category for all six core roles, showing exact ingredients, companion results, the reusable restoration station, dimension requirements, and restored health, waiting state, and training. Natural and Creative cores resolve by role while retaining their information pages.
- English and Korean documentation and guidebook instructions for finding station restoration recipes.

### Changed

- Require JEI 15.62 or newer when the optional integration is installed, matching the supported recipe UI API.

### Validation

- Native JEI clients verify all six restoration paths, exact costs, natural and Creative core lookup, reagent and result searches, reusable station registration, and English/Korean screens with both fonts.
- A dedicated server passes eight focused checks without JEI or Patchouli. A native client without JEI renders all 58 guidebook entries and 188 pages in English, Korean, and English fallback.
- Google Java Format, the Java 17 build, bilingual documents, guidebook links, and packaged resources pass verification.

## [0.3.8] - 2026-10-21

### Fixed

- Standardize the Damaged Tunnel Slime Core name in English and Korean, documentation, internal model and texture paths, and client checks.

## [0.3.7] - 2026-10-21

### Added

- Collapsed Mine Recovery: mineralized mine corpses break into unique damaged cores; each new site provides command fragments and restoration supplies in a Slime Chest.
- Forgotten Miner and Forgotten Tunnel Slimes slowly work bounded old faces; Mine Guardians defend the site. All three roles have Creative spawn eggs and drop cores with command fragments when defeated by players or companions.
- Restore mining cores in the Overworld with one Old Command Fragment and one slimeball at a Slime Restoration Station. Ancient Mining Slimes prioritize visible active-layer ores and use half work and movement stamina; Ancient Tunnel Slimes use half work and movement stamina and mine tagged ancient masonry 50% faster with effective tools. Mine Guardians restore as Royal Guards.
- Bilingual acquisition, cooperation, and preservation guides, JEI information, and three new Patchouli entries. The guide now contains 58 entries and 188 pages per language.

### Changed

- Craft Slime Restoration Stations with a shaped workbench recipe of slimeball, gold, redstone, and Stone Bricks; remove the stonecutting recipe.
- Dedicated transparent 16×16 item textures for the Ancient Command Seal, generic and profession cores, and Old Command Fragments. A limited palette and clear pixels match vanilla inventory artwork.
- All newly restored ancient companions receive 12–28 invested upgrades randomly distributed among useful profession stats, with a fixed per-core result, ordinary level caps, and matching growth costs.
- Tamed ancient companions use white head insignia; hostile remnants retain purple insignia. New summon crests share the existing base with distinct ancient profession symbols.
- Network protocol 18 requires matching clients and servers for the added entity types. Existing companion and core payload schemas remain compatible.

### Fixed

- Keep old mine workers progressing through visible cells when rubble hides the first cell of their bounded work face.

### Validation

- More than two hours of independent review cover recovery, companion cooperation, rendering, documentation, and saved-world compatibility. All 664 native server regressions pass with JEI and Patchouli present and absent.
- Native cases verify corpse drops, exact restoration costs, one-time identities, random useful training, initial Wait, appropriate tools, canceled Forge events, and visible mining targets. A blocked-face regression fails before the correction and passes afterward.
- Actual generation samples 8,192 chunks, finding six tunnels and one quarry with twenty core-bearing remains. Separate generated-site checks verify both shapes, all four tunnel rotations, working hostile remnants, and fourteen preserved corpses.
- Independent native cooperation runs last thirty and fifteen minutes, delivering 384 and 835 cargo items respectively with three tool resupplies each. All shared stair treads remain protected. Four ancient tunnel workers also resume all rotated faces in a separate twenty-minute run.
- A two-hour preservation run completes 35,900 dimension transfers and 119 core restorations across all twenty-five professions, retaining trained stats, ownership, identity, vitals, gear, cargo, and duplicate-redemption protection. These are preservation checks with AI disabled; separate runs exercise actual work and waiting behavior.
- Actual server restarts preserve new cores, trained companions, supplies, and redemption data. A 0.3.6 save upgrade retains earlier companions and cores; eight empty remains in three old generated mines remain empty and keep their quarry stair protection.
- Native clients verify all twenty-five companion appearances, equipment, six core variants, naturally recovered core information in JEI, and the 16×16 item sprites. Both fonts render all 58 guidebook entries and 188 pages in English, Korean, and English fallback. Final guidebook prose passes its own native and client checks.
- A separate native AI observation checks all twenty-five newly summoned companions for more than 86 minutes across four day cycles, retaining Wait and stable positions without disabling AI.
- Google Java Format, the Java 17 build, bilingual documentation, resources, release contents, and runtime linkage checks pass. Release Java classes match both complete server runs; the final guidebook resources match the later book-specific verification.

## [0.3.6] - 2026-10-21

### Added

- Collapsed mines in newly generated dry, level chunks of The Slimic: descending 3×3 tunnels and unfinished 16×16 quarries with visible surface entrances, timber, rubble, cobwebs, and fallen slime remains.
- Quarries retain the Mining Slime’s two-wide perimeter stairs, reinforce their treads against falling terrain, and persist their protection for later workers. Ancient remains use empty Broken Slime Blocks and cannot be revived.
- Bilingual mine documentation and a four-page guidebook entry, bringing the guide to 55 entries and 177 pages per language.

### Fixed

- Allow working companions to follow the longer detours needed by deep perimeter stairs. Height differences now contribute to the loaded-terrain path distance, while the existing 4,096-node budget and chunk loading restrictions remain unchanged.

### Validation

- More than two hours of independent review cover terrain, companion cooperation, documentation, rendering, and save compatibility. Complete and focused native server runs verify all 644 regression scenarios with JEI and Patchouli present and absent.
- Mine cases cover four tunnel orientations, all five surface ruin variants, stable stair treads, persistent protection, empty remains, collision rejection, and dimension boundaries. A real cargo round trip fails before the vertical-route correction and passes afterward; a saved-mine replay delivers exactly 192 items to the original chest.
- Actual generation samples 4,096 chunks and finds both mine shapes. Separate server restarts preserve both sites, all five empty remains, and stair protection; revival attempts and block-item replacement cannot create companions.
- Separate 30-minute native runs verify two miners and a carrier at a generated quarry, and tunnel workers resuming all four rotated 3×3 faces. The quarry run preserves every original tread through 1,795 checks while delivering cargo.
- A separate 110-minute preservation run completes 30,268 transfers across all twenty-three professions and 109 core restorations, retaining identities, ownership, vitals, equipment, cargo, storage, and duplicate-redemption protection.
- Native clients verify generated surface entrances and interiors. Both fonts render all 55 guidebook entries and 177 pages in English, Korean, and English fallback. A 0.3.5 save upgrade preserves companion identities, ownership, vitals, equipment, cargo, and core redemption data.
- Native AI fixtures wait for entity ticks and isolate ambient spawning, weather, incidental item drops, and exterior fluid sources. Google Java Format, the Java 17 build, bilingual documentation, resources, release contents, and runtime linkage checks pass. Release code and resources match the tested server snapshots.

## [0.3.5] - 2026-10-20

### Added

- Guard, Transport, and Commander variants of the Damaged Ancient Slime Core in the main Slimic Creative tab, beside the seal and restoration station. Each newly taken variant can be restored independently; naturally obtained cores retain their one-time identity protection.

### Validation

- All 45 focused native server cases pass with JEI and Patchouli present and absent, including repeat restoration of all three Creative variants, one-time natural cores, exact material costs, denied interactions, and nested restoration callbacks.
- Native clients verify the three Creative core entries, role tooltips, existing models, and restoration information. Google Java Format, the Java 17 build, bilingual documents, resources, and runtime linkage checks pass. The release matches both focused server snapshots.

## [0.3.4] - 2026-10-20

### Changed

- Give the Ancient Command Seal a dedicated gold-and-purple item texture.
- Raise Ancient Guard base health/attack/armor to 64/8/8, Forgotten Transport to 48/6/6, and Ancient Commander to 96/12/12. Earlier default remnants migrate once while retaining health proportion and saved traits; custom attributes remain intact.
- Match the Royal Guard's base maximum health to the Guardian Angel at 160. Its permanent profession bonus preserves existing health training and current health without stacking on crafting or reload. Stored roster maximum health includes attribute modifiers.
- Rewrite recovered records as slime-authored accounts with the missing figure obscured as XXX. Keep restoration clues in their experiences and add bilingual royal-ward exploration guidance.
- Make chest seals optional: a 20% chance in workshops and depots, 35% in archives, and 50% in royal wards, with at most one seal per chest. Defeated remnant drops are unchanged.
- Name the functional pedestal Slime Restoration Station, move it from decorations to beside seals in the main Creative tab, and add item, interaction, JEI, and guidebook restoration instructions. Existing block identities remain compatible.

### Added

- Native spawn eggs for all three ancient hostile roles, available in the Slimic Creative tab.

### Validation

- All 633 native server regressions pass with JEI and Patchouli present and absent. Focused cases cover restoration through either hand, exact ingredient consumption, permission vetoes, 768 seeded chest rolls, stronger ancient defaults, legacy migration, royal health training, stored vitals, and native spawn eggs.
- Native clients verify all 2,892 world block states in English and Korean, exclusive station placement in the main Creative tab, station tooltips and JEI information, the dedicated seal and hostile eggs, and synchronized Royal Guard management at 160/160 health.
- Both fonts render all 54 guidebook entries and 173 pages in English, Korean, and English fallback. All 42 recovered-record page renders also pass, with unchanged record text and book data verified against the release.
- Google Java Format, the Java 17 build, bilingual documentation, resource resolution, release contents, and runtime linkage checks pass. Release code and resources match both complete server runs.

## [0.3.3] - 2026-10-20

### Added

- Forgotten Orders: Ancient Guards, Forgotten Transports, and Ancient Commanders remain bound to newly generated ruins in The Slimic. Guards patrol and defend their home; carriers use their own linked depots; commanders alert nearby guards of the same ruin.
- Damaged Ancient Slime Cores and Ancient Command Seals. Defeat remnants, then use a core on an intact Command Pedestal with one seal and one slimeball to restore a newly owned companion at one health. Redeemed ancient identities persist across the save to prevent duplicate restoration.
- Royal Guard Slimes defend an assigned post and, with a shield, intercept part of nearby allies' combat damage at a stamina cost. They inherit normal combat filters, equipment, logistics, growth, ownership, recovery, and travel rules.
- Two acquisition paths: restore ancient remnants with their old names, insignia, and some endurance training, or specialize an existing Combat Slime with a seal while retaining its identity, growth, equipment, and cargo.
- Occupied supply depots, archives, and old royal wards with guaranteed seals and translated records about the missing traveler and the last watch. Four new bilingual guidebook entries bring the guide to 54 entries and 171 pages per language.
- A dedicated gold-and-purple royal accessory atlas and a royal crest on the common summon-item base.

### Validation

- Over two hours of independent review cover companion interactions, restoration, natural ruins, documentation, guidebook rendering, appearance, and previous-save compatibility.
- All 624 native server regressions pass with JEI and Patchouli present and absent. New cases cover real patrol and water routes, raised-shield interception, native interaction vetoes, exact restoration costs, duplicate redemption, royal protection, occupied ruin generation, and existing companion contracts.
- Native clients verify all twenty-three companion professions, eight armor variants, royal and ancient appearances, resource-pack replacement, 552 tooltips, and JEI information. Both guidebook fonts render all 54 entries and 171 pages in English, Korean, and English fallback; all 42 rendered record pages across those languages and both fonts also fit.
- A real 0.3.2 world upgrade preserves all twenty-two previous professions and their stored items. Separate cold restarts retain restored and specialized companions, unused cores, redeemed identities, ancient parcels, named storage, and depot links.
- A separate 90-minute real-time portal, restoration, and storage run completes 24,771 transfers across all twenty-three professions and 89 core restorations, preserving exact identities, ownership, vitals, equipment, cargo, waiting arrivals, named storage, and duplicate-redemption checks.
- Actual generation checks sample 1,024 chunks. A separate 20-minute real-time run completes 1,463 pickup/delivery transitions across three natural ruin types while preserving exact stock and the original forced-chunk set.
- Google Java Format, the Java 17 build, bilingual documents, resources, release contents, and runtime linkage checks pass. The release matches the code and resources used in both complete server runs.

## [0.3.2] - 2026-10-20

### Fixed

- Preserve a Slime Chest's custom name when it is broken and placed again. Its inventory still drops separately, exactly once.
- Reject companion summons removed or reassigned by entity-admission callbacks. Preserve the summon item and previous roster record, remove provisional entities, and prevent duplicate recovery data before a summon succeeds.
- Recheck pinned Ender Slime capacity after entity-admission callbacks so nested restorations cannot exceed the owner's configured limit.

### Validation

- Full and focused native server runs verify 589 regression scenarios with JEI and Patchouli present and absent. The tool-replacement fixture excludes exterior fluids and verifies that mining resumes with the replacement tool within its original deadline; all 20 transport checks pass in both environments.
- Native clients verify all 2,892 world block states, chest articulation, portal animation, and 100 resource-pack replacement/restoration cycles. Both guidebook fonts render all 50 entries and 157 pages in English, Korean, and English fallback without layout errors.
- Real-time portal and storage checks cover all twenty-two professions over separate 45-minute and 20-minute runs, preserving identities, vitals, equipment, cargo, and committed destination roster records. Google Java Format, the Java 17 build, documentation, resources, and release runtime references pass verification.

## [0.3.1] - 2026-10-20

### Changed

- Rename the world tabs to Slimic Blocks and Slimic Decorations and remove repeated decoration tooltips and generic decoration JEI information.
- Craft the Slime Scanner with a centered Slime Tuner and four glass blocks in a cross. Update English and Korean gameplay, JEI, and guidebook instructions.
- Animate the slime portal with sixteen interpolated frames. Use native single-chest proportions and UV layout for the Slime Chest, including a lid-mounted latch and matching item model.
- Give Damp Grass a full soil face with the native irregular grass fringe instead of a straight horizontal cap.

### Validation

- All 71 focused native server checks pass with JEI and Patchouli present and absent, covering the shaped scanner recipe and invalid layouts, work-area interactions, storage, transport, portals, and guidebook resources.
- English and Korean native clients verify short tab names, all 32 decoration tooltips, 86 world models, the chest body/lid/latch and four facings, grass fringes, and sixteen portal frames with changing GPU pixels. The final scene is also visually inspected.
- Both guidebook fonts render all 50 entries and 157 pages in English, Korean, and English fallback, including the new cross recipe, item lookups, links, and localized searches. Google Java Format, the Java 17 build, documentation, resources, and runtime references pass verification.

## [0.3.0] - 2026-10-20

### Added

- Explore The Slimic, a separate dimension with the Damp Plains biome, gel-covered Overworld terrain, native wildlife, abandoned workshops, supply depots, and broken roads. Ruin chests contain supplies and a translated written ledger.
- Activate a vertical vanilla slime-block frame with a slimeball. Nether portal dimensions and optional corners apply. Portals connect the Overworld and The Slimic at a 1:1 coordinate scale, reuse saved exits, and create safe return platforms when needed.
- Add 32 independent base materials, eight stairs, eight slabs, six walls, and 32 decorations. Building blocks and decorations each have their own Creative tab, recipes, models, loot, and bilingual names. Nine dedicated textures establish the world palette.
- Add six bilingual guidebook entries and detailed dimension guides. The guide now contains seven categories, 50 entries, and 157 pages per language. JEI provides world-material guidance alongside recipes.

### Changed

- Companions crossing a slime portal retain their identity, owner, vitals, upgrades, equipment, and cargo and arrive in Wait. Their work areas follow the existing relocation rules.

### Fixed

- Cross-dimension player travel rechecks permissions and landing safety before removal from the source dimension. Canceled Ender travel leaves the player registered in the original world.

### Validation

- All 586 native server regressions pass with JEI and Patchouli present and absent. Bucket-supply fixtures isolate exterior fluids so unrelated quarry wall-lining jobs do not interfere with supply checks.
- Native clients verify portal activation and round trips, all 86 material and decoration models, separate Creative tabs, generated workshops, and translated ruin records. Both guidebook fonts render all 50 entries and 157 pages in English, Korean, and English fallback, including item lookups, links, and localized searches.
- Actual server restarts preserve all twenty-two companion identities, saved traits, and both portal indexes. Native terrain checks cover 64 generated chunks, ores, wildlife spawning, ruin storage, and written records.
- Over two hours of independent review include a 100-minute native stress run: 26,334 companion transfers preserve unique identities, vitals, equipment, cargo, and waiting arrivals. Nineteen additional chunks retain the dimension's biome.
- Google Java Format, the Java 17 build, bilingual documentation, resource references, release contents, and runtime linkage checks pass.

## [0.2.4] - 2026-10-19

### Documentation

- Distinguish preserved traits and applicable work plans from commands: item release and revival start in Wait, while ordinary world reloads retain saved commands. Revival restores one health.
- Clarify normal tool interaction reach, management distance limits, and Creative tuner base values before profession multipliers.
- Scope Commander rosters and Gather to the primary owner's companions, and include the empty-hand and inventory-space requirements for Ender linking.
- Explain active fishing time, Attention roaming growth, scanner priority over manual herald signals, and shared tunnel block-breaking timing. Align Korean ingredient names with Minecraft and correct grammatical errors.

### Validation

- All 558 native server regressions pass with JEI and Patchouli. All 24 focused control, following, and aggression checks also pass without both optional mods. The obsolete tuner preview expectation now checks Shift recovery; following and aggression fixtures avoid cross-test interference.
- Both guidebook fonts render all 44 entries and 139 pages in English, Korean, and English fallback, including internal links, item lookups, localized search, and page bounds.
- Native clients verify 528 fresh/stored tooltips across languages and fonts, plus profession-specific JEI information. The Java 17 build, Google Java Format, bilingual documentation, release resources, and runtime reference checks pass.

## [0.2.3] - 2026-10-19

### Added

- Craft one Slime Tuner and four glass blocks without a fixed arrangement to make a Slime Scanner. Its dedicated lens texture distinguishes the tool, which appears beside the tuners in the Creative tab and has JEI guidance.

### Changed

- Move work-area previews to ordinary right-click with the Slime Scanner. Hold it in either hand to keep the fifteen-second preview visible. Ownership, range, and read-only area rules remain in effect; Shift-right-click uses normal companion recovery with any held tool.
- Rename the Korean Slime Tuner and Creative Slime Tuner to 슬라임 조작기 and 크리에이티브 슬라임 조작기. The scanner is named 슬라임 탐색기.

### Validation

- All 51 focused native server regressions pass with JEI and Patchouli present and absent, covering the exact shapeless recipe, ordinary scanner clicks on all twenty-two professions, shared permissions and revocation, range limits, tool-independent Shift recovery, and existing acquisition and guidebook behavior.
- Native English/Korean clients verify fourteen scanner previews, dedicated 16×16 artwork, localized tool names, tool-change clearing, expiry, and named herald chat. Both guidebook fonts render all 44 entries and 139 pages in English, Korean, and English fallback.
- Bilingual documentation and resource checks, release contents, runtime references, Google Java Format, and the Java 17 build pass.

## [0.2.2] - 2026-10-19

### Added

- Feed a golden apple to a tiny wild Slime to turn it into one owned Friendly Slime in Wait. The companion retains its custom name and supports normal Shift-right-click recovery. Survival consumes one apple; Creative preserves it. Larger Slimes and Magma Cubes are unaffected.

### Validation

- All 34 focused native server regressions pass with JEI and Patchouli present and absent. Eight new cases cover feeding, both hands, Creative use, full inventories, excluded targets and items, canceled interactions and spawns, owner-only recovery, and preserved traits on release.
- English/Korean gameplay, development, guidebook, and JEI guidance describe the new acquisition method. Google Java Format, bilingual resource checks, and the Java 17 build pass.

## [0.2.1] - 2026-10-19

### Fixed

- Planned quarry previews use the same starting-floor calculation as actual quarry initialization, including relocation at the world's minimum build height.
- Rebased farming and logging previews remain centered on the registered work area when the worker moves.
- Missing-storage herald messages cover both Slime Chests and Warehouse Slimes.
- Fishing alerts distinguish a missing suitable fishing spot from an unreachable route.
- Bilingual version guidance uses the 0.2.x series. Meow Slime guidance distinguishes retained activation from movement orders, which return to Wait on item release.

### Validation

- All 547 native server regressions pass in each of the four JEI/Patchouli combinations. Previous-version world and recovered-item data retain their companion traits, and all twenty-two types pass repeated recovery and release checks.
- Focused server regressions reproduce and verify corrected quarry heights, rebased farming/logging origins, missing fishing spots, shared-access revocation, cross-dimension warning delivery, and read-only previews without chunk loading or survey advancement.
- A native client completes sixty English/Korean resource-reload cycles and 600 Shift previews. Latest-client checks cover rebased fields, GUI scales 2–4, armored Combat/Dragon previews, existing management and equipment, 528 tooltips, and 26 synchronized inventory screens.
- Both guidebook fonts render all 44 entries and 137 pages in English, Korean, and English fallback, including updated recovery guidance.
- Seventy minutes of real server time complete 170 obstacle-route, delivery, treatment, and collection cycles while preserving exact cargo, supply costs, and mount equipment.

## [0.2.0] - 2026-10-19

### Added

- Shift-right-click a companion with a Slime Tuner to preview its current or planned work area in the world for fifteen seconds. Quarry bounds, the next tunnel section, field areas, and search ranges use compact outlines visible only to the requesting owner.
- Herald firework alerts also send authorized online owners a localized message identifying the blocked companion, its custom name, and the specific cause, including missing tools and supplies, full bags, storage issues, exhaustion, and unreachable work.

### Changed

- All item-based releases and revivals start in Wait, including recovered companions. Identity, growth, equipment, cargo, and applicable work plans remain intact. Recovery retains health; revival still restores one health. Work resumes through an explicit command.
- Tuner previews take precedence over Shift-right-click recovery. Recovery remains available without a held tuner. Network protocol 16 carries bounded server-provided area snapshots.

### Validation

- Server audits cover 540 regression scenarios in both optional-integration environments. Focused rechecks pass for the updated Wait and preview rules, all twenty-two releases and revivals, bounded read-only areas, packet validation, named warning delivery to primary/shared owners, and isolated logistics action intervals.
- Native clients with and without JEI and Patchouli verify English/Korean Survival interactions, five work-area displays, tool-change clearing, fifteen-second expiry, and localized herald chat. Guidebook rendering passes all 44 entries and 137 pages in English, Korean, and English fallback.
- Bilingual documentation, release contents, runtime references, Google Java Format, and the Java 17 build pass verification.

## [0.1.40] - 2026-10-19

### Fixed

- Vanilla Slimes no longer acquire nearby companions first; their added companion-target goal only selects a companion that attacked them. Magma Cubes and other hostile mobs retain their existing targeting.
- Combat and Dragon Slimes still initiate attacks on vanilla Slimes under their Hostile target filter.

### Validation

- Seven native server regressions pass with optional integrations present and absent, covering all twenty-two companion types, attacker-only targeting, Magma Cube and Husk aggression, Combat and Dragon first strikes, and existing Enderman behavior. The previous build reproduces both companion-targeting failures.
- Bilingual documentation and guidebook resource checks, Google Java Format, and the Java 17 build pass.

## [0.1.39] - 2026-10-19

### Fixed

- Remove the unbound Slime Teleporter from the Creative tab so all twenty-two companion items appear together. Bound teleporters remain available by linking an Ender Slime.
- Clarify teleporter availability in the English and Korean gameplay guides.

### Validation

- Native clients verify Creative tab contents with JEI and Patchouli present and absent. Existing server regressions confirm Ender linking, authorized teleportation, and destination identity after recovery in both environments. Bilingual documentation checks, Google Java Format, and the Java 17 build pass.

## [0.1.38] - 2026-10-19

### Changed

- Give profession accessories dedicated texture atlases with individual UV regions for hats, packs, overalls, lyres, carts, wings, horns, harnesses, and other permanent details. Surface artwork can be replaced through resource packs while retaining existing geometry, animation, and dye behavior.
- Use matching armor atlases for all six materials, including leather dye and enchantment rendering, and move management, creative tuner, roster, and slot backgrounds into PNG resources.
- Document the accessory atlas layout and texture editing workflow in English and Korean.

### Fixed

- Show each of the twenty-two summoning items' own profession help instead of leaving several specializations with only the common companion hints. Fresh and recovered items show the appropriate release instructions, and profession descriptions appear only once.
- Keep long-form recipe and behavior details in JEI and the guidebook while item tooltips summarize each companion's controls and purpose.

### Validation

- All 531 native server regressions pass with JEI and Patchouli present and absent. Focused tooltip checks also verify each recovered profession item, exact saved data, and the original mining-tool hint.
- Native clients verify all twenty-two profession models, six armor materials and dyed variants, 564 accessory faces, 26 synchronized English/Korean management layouts, equipment transfers, roster controls, tunneling templates, and Survival riding. Fresh/stored tooltips pass 528 normal/advanced, language, and font combinations in each optional-integration environment.
- The guidebook renders all 44 entries and 137 pages per language in English, Korean, and English fallback with default and Unicode fonts. Two 100-cycle resource-pack checks with optional integrations present and absent restore the original GPU texture. Bilingual documentation, release contents, runtime references, Google Java Format, and the Java 17 build pass verification.

## [0.1.37] - 2026-10-19

### Changed

- Align companion bags, equipment, player inventory, and hotbar slots on one nine-column grid. Bag pages reserve three rows, distinguish locked cells, and hide page controls when only one page is available.
- Render the Friendly Slime's flat badge with the complete gold-block texture rather than stretching one gold-colored texel.

### Validation

- Native clients verify 26 synchronized English/Korean screens across ordinary, expanded, and partial bag pages, equipment, combat filters, locked cells, and widget bounds. The baked Friendly badge uses two planar faces spanning the gold-block sprite.
- Existing capacity, paging, hidden-slot access, Shift-click, recovery, and armor-transfer regressions pass on servers with optional integrations present and absent. Bilingual documentation, release contents, runtime references, Google Java Format, and the Java 17 build pass verification.

## [0.1.36] - 2026-10-19

### Changed

- Lower Fishing Slime overalls, straps, and buttons below the eyes, and move the Abundance Slime's lyre and strings together beside its body.
- Accept Attention Slimes as an alternative to Friendly Slimes in sixteen basic specialization recipes, including their JEI previews. Advanced professions retain their existing prerequisites.
- Specializing an Attention Slime retains common companion data and invested stats while removing its roaming state, profession activity, and permanent movement-speed bonus. The resulting profession initially waits.

### Validation

- All 39 native regression tests pass with optional integrations present and absent, including every basic specialization and source-slot arrangement, real inventory crafting, exact tool retention, and removal of Attention-specific state.
- Native client portraits verify Fishing and Abundance appearance; JEI exposes both bases for all sixteen specializations. English, Korean, and fallback guidebook pages render successfully. Google Java Format and the Java 17 build pass.

## [0.1.35] - 2026-10-19

### Changed

- Limit released Commander Slimes to one per connected shared-owner group across all dimensions, including unloaded chunks. Direct, indirect, and one-way shared-access links define the population group without expanding management or cooperation permissions.
- Apply the limit to fresh summons, stored releases, and revivals while preserving items, companion data, broken blocks, and revival materials on failure. Stored and broken commanders do not occupy a world slot.
- Reject shared-owner changes that would join two groups with released commanders. Existing extra commanders from older saves remain intact and prevent further releases.

### Fixed

- Gathered commanders cancel their own pending Gather commands and release temporary source chunks. Every gathered profession arrives in Wait; rejected teleports retain the original command.
- Reserve commander capacity through spawn callbacks so recursive releases and relationship changes cannot bypass the limit.

### Validation

- Native servers verify waiting arrivals for all twenty-two professions, rally cancellation and source-ticket release, connected-owner population limits, unloaded and cross-dimension records, stored restoration, revival, and cancelled spawn callbacks with optional integrations present and absent.
- English, Korean, and English-fallback guidebook rendering passes with default and Unicode fonts. Bilingual documentation, release metadata, runtime references, the Java 17 build, and Google Java Format pass verification.

## [0.1.34] - 2026-10-18

### Documentation

- Align English and Korean gameplay guides, development notes, guidebook pages, and JEI descriptions with all twenty-two companion types, shared ownership, growth rewards, and current equipment support.
- Clarify right-click priorities, Wait → Farm plot registration, supported staircase materials, request-based cargo collection, exact supply limits, flight recovery, and Ender linking and travel.
- Distinguish common stat values from profession multipliers and document Guardian beam range, assigned-enemy target filters, bonemeal chance and costs, and teleport dismounting.
- Standardize Korean crafting diagrams and status wording, and shorten dense guidebook paragraphs while keeping 44 entries and 137 pages per language.

### Fixed

- Commander rosters display Nurture for Abundance Slimes and missing saplings for Lumberjack Slimes without changing saved order or reason identifiers. Korean roster labels distinguish loaded and unloaded companions.
- Cargo-collection messages describe actual support requests, including combat cargo before a bag fills. Development test-mod metadata matches the release.

### Validation

- Documentation and context review continued for over two hours, comparing implementation, bilingual wording, and native rendering.

- All 822 rendered page bodies match the current source text across both font profiles and all three language settings. Native guidebook checks cover English, Korean, English fallback, default and Unicode fonts, every page and diagram, links, item lookups, and localized search.
- Independent servers verify profession-specific roster text through saved records, native menu snapshots, real packet encoding, and both locales with optional integrations present and absent.
- Native clients verify all twenty-two models and minimum-size English/Korean management and roster screens. Google Java Format and the Java 17 build pass.

## [0.1.33] - 2026-10-18

### Changed

- All six Slime Armor variants share one silhouette and an untinted slime badge. Material colors and leather dye remain distinct.
- Friendly Slime summon items display a flat, unmarked gold plate.

### Validation

- Native client renders have identical silhouettes for all six armor materials and two dyed leather examples. The exposed slime badges retain the same colors, and the Friendly Slime plate renders as one flat color.
- Google Java Format, the Java 17 build, English and Korean client checks, bilingual documentation, release contents, and runtime references pass verification.

## [0.1.32] - 2026-10-18

### Added

- Glow Slimes with independent torch-strength lighting and stamina-powered temporary fireflies.
- Commander Slimes with persistent companion records, tags, player relationships, and stamina-powered gatherings.
- Ender Slimes with fixed, ticking destination chunks and identity-bound Slime Teleporters.
- Attention Slimes with faster movement and an optional playful roaming order.

### Changed

- Shared owners can manage companions, ride, and receive protection. Automatic cooperation across primary owners requires mutual sharing. Primary owners control shared ownership and enemy relationships.
- All twenty-two companion types initially wait. Network protocol is 15; companion payload schema remains 1.

### Fixed

- Gathered workers arrive in Wait even when relocation changes their working chunk.
- Ender chunk tickets release correctly when an endpoint is unpinned or removed before its first tick after a restart. Restoring a stored pinned endpoint respects the owner’s anchor limit without changing or consuming its saved traits.
- Attention roaming reports the correct management mode and uses local ground when its owner is airborne or far away, with bounded route retries. Commander menus tolerate entity removal before their opening packet arrives.
- Primary-owner team alliances survive delegated Follow commands and unavailable Follow targets. Commander rosters fit the minimum 320×240 GUI size.

### Validation

- Independent review continued for over two hours after the four professions were implemented.
- All 516 native regressions pass in each of the four independent Patchouli/JEI configurations. Native hostile acquisition covers all twenty-two companion types with deterministic actors and exact target observations.
- Independent checks gather every profession from actually unloaded terrain, restore two-dimensional Ender anchors and original tokens across cold restarts, and verify real shared healing, monitoring, totem rescue, and permission revocation.
- A 0.1.31 world preserves eighteen professions and their stored summon items, including identity, ownership, health, equipment, cargo, and growth. A 10,000-record roster retains bounded six-row packets and save/load fidelity.
- Native clients verify the shared summon crests, profession models, real roster/tag/relation packets, minimum-size English and Korean screens, light controls, fireflies, teleporters, and roaming. The guidebook covers 44 entries and 137 pages per language, including English fallback.
- Google Java Format, the Java 17 build, bilingual links, release contents, source archives, and runtime member references pass verification.

## [0.1.31] - 2026-10-18

### Fixed

- All eighteen companion types, including Friendly and Saddle Slimes, initially select Wait. Fresh summon items and entity data without an order use the same default.
- First Saddle Slime specialization pauses a stored Follow order while retaining identity, resources, and the original saddle. Ordinary recovery retains saved commands.
- English and Korean gameplay, development, and guidebook instructions consistently describe the shared initial Wait state.

### Validation

- Six initial-order regressions cover all eighteen companion types, commandless entity loading, native summon items, initial specialization, command-preserving recovery, and waiting near an online owner. Twenty-five command and recovery checks pass with both optional integrations present and with both absent.
- Thirteen movement and lifecycle checks pass with the hop-frequency probe explicitly selecting Follow before measuring movement.
- Google Java Format, the Java 17 build, bilingual document links, release contents, and runtime member references pass verification. The native guidebook checks cover all 116 pages per language, English fallback, links, search, and item lookups.

## [0.1.30] - 2026-10-18

### Changed

- Meow Slime uses only its current entity, item, recipe, and saved-state names. Previous-name registry remapping and state conversion have been removed along with their migration tests.

### Validation

- Seventeen native profession and initial-order regressions pass. Java formatting, the Java 17 build, bilingual documentation, and runtime member references pass verification.
- Current sources, resources, documentation, compiled classes, release JAR, and source archive contain no previous companion name.

## [0.1.29] - 2026-10-18

### Changed

- The cat companion is named Meow Slime in English and 냥냥 슬라임 in Korean. Entity classes, registry IDs, recipe serializers, resources, translation keys, tests, documentation, and guidebook entries use the same name.
- Existing world registries and companion state migrate to the new names while preserving identity, ownership, health, growth, activation, and movement orders. Companion schema 1 and network protocol 14 remain unchanged.

### Validation

- Forty focused profession, initial-order, and migration regressions pass with both optional integrations enabled and with both absent.
- Loading the previous release's saved registry remaps entity and item IDs; native entity loading and stored-item release retain companion identity, ownership, health, activation, and roaming. Migration preserves current fields, remains idempotent, and leaves future schemas intact.
- Native clients verify the renamed models and controls, and all guidebook pages pass English, Korean, and English-fallback checks. Formatting, the Java 17 build, document links, release resources, and runtime member references pass verification.

## [0.1.28] - 2026-10-18

### Added

- Fishing Slimes with shore selection, five-minute relocation, native fishing loot and rod enchantments, cargo storage, and replacement-rod requests.
- Abundance Slimes with healing and revival plus stamina-powered bonemeal effects, a lyre, and a laurel.
- Tunnel Slimes with horizontal 3×3 excavation, a nonconsumable lining template, direction selection, perimeter reinforcement, and carrier material requests.
- Dragon Slimes with flying combat, bat-shaped wings, and periodic targeted flame breath.
- Meow Slimes with a cat head and independently controlled creeper deterrence and roaming.
- English and Korean gameplay, development, and guidebook coverage for all eighteen companions, including 39 guide entries and 116 content pages per language.

### Fixed

- Combat companions collect their kill drops and request unloading without stopping combat when their bags fill.
- Healing Slime detection stays twice the common value across upgrades and tuning.
- Healing tiara cross faces no longer overlap and flicker.
- Work-earned mining and fishing experience applies Mending to the working tool before remaining orbs spawn.
- Specialized recovery uses each entity's own summon item, preserving subclass identity.
- Tunnel swimming maintains its working height and progresses through returning water without repeatedly spending stamina on the same flow. Blocked approaches report their state to heralds.
- Tunnel lining templates cannot be extracted through native inventory shortcuts.
- Waiting flying companions remain stationary, and Meow movement controls stay synchronized on clients.

### Changed

- Network protocol is 14; companion save-data schema remains 1.

### Validation

- All 479 native regressions pass in each of the four independent Patchouli/JEI configurations.
- Profession and boundary checks cover crafting and recovery, original equipment, fishing loot and Mending, growth protection, tunnel lining and flooded travel, supply transactions, dragon combat, creeper avoidance, and full combat bags.
- Native clients verify profession models and synchronized controls with and without JEI. Guidebook checks cover all 116 pages per language, English fallback, item lookups, links, search, default and uniform fonts, and multiple UI scales.
- Google Java Format, the Java 17 build, bilingual document links, packaged resources, source archives, and runtime member references pass verification. Mining protection fixtures require an actual denied native event while preserving exact terrain, equipment, and activity assertions.

## [0.1.27] - 2026-10-18

### Added

- An optional Patchouli companion guide in English and Korean, crafted shapelessly from one book and one slimeball.
- In-game instructions for companionship, growth, all thirteen companion types, equipment, quarry work, logistics, and recovery.

### Changed

- English and Korean guides align job commands, storage priorities, stat usage, and terminology with current companion behavior.

### Validation

- All 446 native regressions pass with both Patchouli and JEI enabled and with both absent. Focused guide checks cover their independent combinations; growth checks include all thirteen companion types.
- Native book checks cover Survival crafting, recipe unlock and synchronization, Creative access, all 99 content pages per language, English fallback, 25 item lookups, internal links, and localized search. Default and uniform fonts and multiple UI scales retain readable layouts.
- The Java 17 build, bilingual document links, conditional resources, release contents, and runtime member references pass verification.
- Terrain preparation clears vegetation drops before test actors spawn. Placement fixtures isolate their registered plot and native planting conditions, while collapse checks retain exact gear and cargo assertions inside their own arena.

## [0.1.26] - 2026-10-17

### Fixed

- Mining Slimes share a persistent staircase layout per quarry chunk, including companions starting at different heights or owned by different players. Recovered or unloaded builders leave their staircase protection intact.
- Previously saved staircase layouts remain protected together. Repairs stop before filling another staircase's headroom, and queued mining rechecks shared floor protection after break callbacks.
- Releasing miners in another dimension keeps staircase protection scoped to each dimension, including the entity join callback before the work anchor resets.

### Validation

- Fourteen cooperative-quarry regressions cover different starting heights, multiple owners, legacy layouts, exact loot and repair costs, callback protection, and dimension changes. Three live miners complete a five-layer quarry while preserving its shared stairs.
- A native world restart retains a repaired step and its protection after the builder is recovered, without loading the quarry for metadata lookup. The same staircase-breaking probe fails against 0.1.25 and passes after the fix.
- Work-area fixtures finish native chunk preparation before actors spawn, retaining the existing action deadlines. Thirty-one quarry, wall, and combat regressions pass with this preparation.
- All 438 native game regressions pass with and without JEI. The Java 17 build, release metadata, source archive, and runtime member linkage checks pass.

## [0.1.25] - 2026-10-17

### Changed

- Saddle flight uses diminishing Movement Speed gains and explicit cruise and boost limits. Ctrl retains its faster movement and higher stamina cost; exhaustion retains controlled descent.
- Saddle Slimes accept Slime Armor alongside the saddle and elytra. Armor supplies native defense, enchantments and durability; legacy chest-slot elytra move intact to the independent wing slot.
- Cleaning Slimes wear blue work caps, Combat Slimes wear metal bands with red crests, Saddle Slimes have bridles, and Soggy Slimes carry slime droplets even without optional equipment.
- Friendly summon items show a plain gold plate; Soggy summon crests use Slime Slag. Every profession retains the shared summon base.
- Network protocol is 13; saved-data schema remains 1 with an explicit saddle equipment layout marker.

### Validation

- All 424 native regressions pass both with and without JEI, including bounded flight, armor damage, equipment migration, exact slot transfers and companion cooperation.
- Korean with JEI and English without JEI pass all seven client stages, including actual Survival riding and simultaneous armor, saddle and wing rendering.
- A world saved by 0.1.24 loads with all thirteen professions retaining identities, owners, health, all nine stats, orders, names, colors, cargo, reserves and equipment.
- Twenty-nine independent interaction scenarios are repeatedly validated for over two real hours. A separate continuous movement run completes 288 logistics and treatment cycles in 7,212 seconds, preserving cargo and all three mount equipment items through stamina recovery and partial unloading.

## [0.1.24] - 2026-10-17

### Fixed

- Work goals recheck the current order before every action, preventing an already-running goal from collecting cargo, healing, changing terrain, or signaling after Follow or Wait.
- Work navigation and sight checks stay within loaded terrain while allowing routes around obstacles. Farming and logging use the same boundary checks; ordinary following retains native navigation. Route caches avoid repeated temporary coordinate objects.
- Adventure-mode revival retains the actual held slimeball's permissions, interaction hand, and clicked face.
- Stored identities are reserved during restoration, preventing spawn callbacks from releasing the same companion in another dimension before the original spawn finishes. Cancellation releases the reservation for a safe retry.
- Healing supply accepts at most one stack and clears fulfilled requests immediately. Full bags show a distinct unloading status.
- Warehouses skip supplies that cannot fit, allowing compatible cargo collection to continue.
- Cancelled treatment, protected revival, and repeated failed approaches retain continuous blocked time for herald monitoring. Successful treatment or absence of eligible patients clears the obstruction.
- Command changes inside healing and protection events no longer invalidate an in-progress treatment target. Completed healing is charged once; interrupted revival preserves the block and its supplies.
- Mining, farming, logging and collection revalidate the current command and owner after protection or pickup callbacks. Interrupted placements restore the original block without consuming materials.
- Herald scans stop additional signals when a signal callback changes the command. Soggy trails roll back when their placement callback switches to Wait, in both Follow and Roam.
- Guardian Angel flight routes, direct-flight checks and beam sight treat unavailable chunks as blocked terrain without loading them.
- Guardian totem protection revalidates ownership, life, distance and the exact reserve after the totem event. Interrupted or recursive protection cannot consume unrelated totems or provide an uncharged rescue.
- Tuned Saddle Slime flight supplies the native dedicated-server movement validator with the expected velocity, preventing valid fast flight from being rolled back. Prediction is cleared before physics, dismounting and wing removal, and is never saved as momentum.
- A displaced miner clears its retained route when its original quarry chunk unloads, so old path nodes cannot load that chunk again. Completed quarry progress, equipment, cargo and the work order remain intact.
- Accepted placement events can adjust crops, soil or sapling growth without bypassing seed, reserved-sapling or tool costs. Converted quarry barriers consume cobblestone when they seal the inlet; ineffective conversions restore the original block without costs.
- Mangrove logging recognizes raised trunks, central root columns and natural gaps, preserves outer roots, and restores propagules with native age and source-water waterlogging. Saved work distinguishes planned root blocks from logs.
- Surface workers use actual collision-floor heights, allowing ordinary logging and mangrove work on mud. Stable stat persistence keys are initialized once to reduce repeated string allocation.

### Validation

- Independent native cases cover unloaded destinations and gaps, wide wall detours, revival permissions, cancelled treatment, concurrent healing, final-slot warehouse recovery, and competing logistics.
- Forty companions preserve all 4,608 cargo items and their source tags; twelve carriers share one supply request without duplicate transfers.
- All 402 native regressions pass. Separate 90-minute runs complete 1,038 logistics cycles with live save/reload and 480 real-movement logistics cycles; Korean with JEI and English without JEI pass the client checks.
- Native sapling generation covers all eight supported species, including two-by-two spruce and jungle trees; low-canopy spruce resumes after a Reach increase.
- Network protocol is 12; saved-data schema remains 1.

## [0.1.23] - 2026-10-16

### Added

- Healing Slimes craft from a Friendly Slime and golden carrot. Explicit Search heals same-owner companions and revives Broken Slime Blocks using bag slimeballs and stamina; transport can replenish their supplies.
- Warehouse Slimes craft from a Transport Slime and eight chests, preserving companion data and cargo. Their capacity is always four times the equivalent transport capacity; Store collects cargo without automatically emptying into chests.
- Shared-base summon crests, visible healing bands and larger warehouse packs, bilingual management messages and guides, and JEI recipes for both specializations.

### Changed

- Transport prefers same-owner warehouses over Slime Chests for deposits and supplies, skips full storage, and retries alternative destinations. Full warehouses pause collection without requesting their own unloading.
- New specializations initially wait. Saved orders and the one-health revival rule remain consistent. Network protocol is 11; saved-data schema remains 1.

### Validation

- Native regressions cover treatment costs, healing cancellation, protected and obstructed revival, duplicate identities, initial orders, capacity scaling, paged recovery, storage ownership changes, partial deposits, alternative destinations, and warehouse supply delivery.
- Korean and English client checks cover both optional-JEI configurations, shared profession crests, visible equipment, synchronized orders and the final warehouse bag page.

## [0.1.22] - 2026-10-16

### Fixed

- Ineffective mining tools no longer receive the Work Speed breaking bonus. Missing or tier-inadequate tools cannot bypass equipment requirements.
- Mining and surface workers share native block-breaking progress. Tool changes, enchantment changes, interruptions, and block replacement reset partial damage and crack overlays.
- Logging respects log hardness and the equipped axe's speed and enchantments before its action cooldown. Farming retains native instant crop harvesting and requires its hoe.

### Validation

- Native regressions measure break-attempt ticks for effective and ineffective tools, maximum Work Speed, tool downgrades, enchantment removal, missing equipment, and logging/farming resumption.

## [0.1.21] - 2026-10-16

### Added

- Mining Slimes reinforce exposed side fluid inlets and large underground openings with their own cobblestone, preserving the quarry staircase and unfinished wall jobs across recovery.
- Missing wall material pauses mining and requests transport delivery of up to one stack; partial stock and available bag space bound the transfer.
- Soggy Slimes support an explicit Roam order, independent of the owner's presence, with a saved local origin, loaded-terrain navigation, and normal trail production and stamina recovery.

### Changed

- Revival from a Broken Slime Block starts every specialization at 1 health while preserving stored traits, equipment, cargo, and stamina.
- Endermen retain native targeting. Combat Slimes classify an Enderman targeting a living companion as Hostile and return it to Neutral when it disengages.
- Updated bilingual management messages, JEI guidance, and gameplay/development guides for wall reinforcement and free roaming. Network protocol is 10; saved-data schema remains 1.

### Validation

- Native server regressions cover wall geometry, protection and costs, recovery, material delivery, roaming, command permissions, stamina recovery, Enderman target transitions, and all eleven specializations revived at one health.

## [0.1.20] - 2026-10-16

### Changed

- Standardized Korean stat names, current stamina, activity, equipment, and work-order terminology across screens and guides; aligned vanilla ingredient names with Minecraft translations.
- Revised English and Korean tooltips, JEI descriptions, status messages, and help text for consistent tone and current behavior. Combat filter states are localized.
- Simplified the bilingual project overviews, corrected misplaced gameplay explanations, and updated development terminology and protocol references.

### Fixed

- Mining Slimes drain exposed source corners in horizontal patches of up to 3×3 cells. Active quarry layers suppress water-source regeneration, preventing repeated removal of the same infinite-water source. Each cell retains range, permission, bucket, and stamina checks.

- Mining paths accept submerged waypoints at the water surface, and swimming control can change heading and move against currents.

### Validation

- Native fluid regressions cover renewable pools, batch limits and costs, protected cells, recovery, and neighboring chunks. Client checks cover localized screens, help panels, and optional JEI integration.

## [0.1.19] - 2026-10-16

### Added

- All work specializations start in Wait, including Creative defaults and new specializations; saved explicit orders survive recovery.
- Soggy Slimes specialize from a Friendly Slime and slime ball, spend stamina on thin slime trails, and retain their traits through recovery. Trails break faster with shovels and drop one Slime Slag; four slag craft into a slime ball.
- Mining Slimes remove water and lava while an empty bucket is present in their own bag. Buckets remain unchanged. Missing buckets pause mining and request transport delivery; full bags can request unloading before supply.

### Fixed

- Guardian Angel Slimes retaliate after taking damage themselves, including absorbed damage and when their owner is unavailable. Untamed guardians safely handle owner-dependent target checks. Environmental lethal damage follows the same nearby guardian-totem protection as attacks.
- Ground slimes retain bounded hop propulsion throughout the native jumping state, preventing high Movement Speed from launching them across one-block steps.

### Validation

- Native checks cover slime trails, slag drops and recipes, stored identity, ownership and world-edit cancellation, guardian self-defense and environmental totems, jumping-state propulsion, fluid removal, retained buckets, and transport resupply.
- Client checks cover the new summoning crest, slag sprite, trail model, management text, JEI, equipment and riding.

## [0.1.18] - 2026-10-16

### Fixed

- Canceled or zero-value healing keeps the slime ball and suppresses healing effects.
- Switching to Spectator invalidates an already open companion management screen.
- Lumberjacks skip temporarily unreachable or protected dropped materials so reachable tree work can continue, and clear obsolete sapling requests when the tree disappears or cargo changes.
- Guardian beams remain visible when the beam crosses the view but the slime body is outside it.

### Validation

- Twenty independent boundary checks cover saved data, summoning, recipes, interactions, permissions, tuning, inventories, growth, mining, search limits, farming, forestry, logistics, cleaning, heralds, combat, guardians, riding, and storage.
- Full native regression and client checks cover profession behavior, equipment, animated storage, optional JEI, resource consistency, and release packaging.

## [0.1.17] - 2026-10-16

### Added

- Guardian Angel Slimes fly beside their owners, retaliate with beams, store up to 64 totems and protect against lethal damage. Maximum health stays ten times the common value.
- A craftable Survival Slime Tuner opens companion management. The former editor is now the Creative Slime Tuner throughout code, registries and UI.
- Slime balls heal four health; water buckets remove dye. Heralds support manual signals and direct rocket-design replacement.
- Slime Chests animate their lids with native opening and closing sounds.

### Fixed

- Lumberjacks refresh tree searches when supplied saplings change, skip temporarily unsupplied species, and show the exact sapling type and stump count. Farming and lumberjack status messages are distinct.
- Surface workers choose reachable standing positions and follow progressive routes across their doubled work range. Miners avoid harvesting wood and revalidate close approach paths. Escape movement retains valid routes through hops and only replaces them with reachable grounded paths.
- Saddle Slimes accept ordinary mounting interactions while items are held; management uses the Survival tuner.

### Validation

- Native regression coverage includes companion care, management permissions, stored firework designs, guardian crafting and reserve transfers, health scaling, flight, retaliation, lethal protection, sapling resupply, mixed tree species, mining around wood, and chest lids.
- Client checks cover the guardian halo and wings, reserve synchronization and clicks, animated chests, worker menus and equipment, profession crests, and optional JEI.

## [0.1.16] - 2026-10-16

### Added

- Farming Slimes equip the exact crafting hoe, wear straw hats, register farmland on the Farm order, harvest and replant crops, restore registered soil, and avoid trampling farmland.
- Lumberjack Slimes equip the exact crafting axe, wear leather-style caps, fell natural trees around their release origin, and reserve matching saplings to replant the original stumps.
- Both workers have twice the common Detection Range, ordinary cargo bags, work growth, stamina costs, transport support requests, distinct summoning crests, and optional JEI recipes.

### Validation

- Native tests cover crop and tree work, exact equipment, plot snapshots, replanting, soil exclusion, protections, full bags, tool requests, saved state, and all-nine companion revival.
- Client validation covers the new hats, held equipment, management screens, recipe families and profession crests with and without JEI.

## [0.1.15] - 2026-10-16

### Changed

- Herald signals repeat every ten seconds while a worker remains blocked.
- Activity growth includes defense, escape movement, and ridden travel.
- Ground movement speed changes hop frequency while hop propulsion stays fixed.
- Companion summoning items share a Friendly Slime base with distinct framed profession crests.
- Winged mounts have paired animated side wings and stamina-powered Ctrl ground and flight sprint. Normal flight also consumes stamina; exhaustion produces a slow controlled landing before grounded recovery.

### Added

- Hostile mobs can target companions; ordinary companions flee after attacks. Creepers receive no additional targeting behavior.
- Fallen companions become Broken Slime Blocks, preserving their identity, stats, equipment, cargo, and orders. Their owner can revive them with a slimeball.

### Fixed

- Combat slimes face threats while shielding and maintain a usable blocking interval between attacks.

### Validation

- Added native regressions for all-specialization revival, payload-preserving mining and placement, blocked/foreign revival, Creative recovery, hostile targeting, escape, shield timing and cancellation, repeated signals, hop distance, and stamina costs.
- Client coverage includes shared summoning bases and distinct profession crests, the recovery block model, paired wings, ground and air sprint, and controlled descent after stamina depletion.

## [0.1.14] - 2026-10-16

### Changed

- All companions require 32 activity for the first stat point, with the existing 64-unit increase and 4,096-unit cap. Existing saved activity grants earned points and retains its remainder without repeated awards.
- The Creative tab groups companion items, storage, armor by material, and the tuner.

### Fixed

- Transport packs and straps clear both slime body surfaces, eliminating overlapping faces and flicker.
- Starting mining after following into another chunk establishes a quarry in the current chunk while same-chunk commands retain progress.
- Slowly advancing mining approaches no longer fail the travel timeout because of the previous half-block movement threshold.
- Changing the target block state during mining clears previous damage; the current block takes its own work interval and yields its own drops.

### Validation

- Added all-specialization growth and saved-activity coverage plus native regressions for relocated work commands, low-speed approaches, and block replacement. Client validation includes multiple pack views and the actual Creative ordering.

## [0.1.13] - 2026-10-16

### Added

- Saddle Slimes crafted from a Friendly Slime and saddle, preserving companion traits and the exact saddle.
- Owner-only riding, view-relative ground steering and jumping, and saddle/elytra equipment slots with direct application and Shift transfers.
- Free flight with usable elytra: view-directed steering, ascent, configurable descent, hovering, and native dismounting for Survival riders without rockets.
- Visible leather saddle, straps, stirrups, and vanilla-textured elytra wings with a mounted pose; dedicated 16px summon artwork, translations, Creative entry, and optional JEI guidance.

### Compatibility

- Network protocol 6 adds riding controls. Use compatible client and server builds. Existing companion save fields remain compatible; equipment and current vitals survive recovery without retaining transient flight gravity.

### Validation

- Added server coverage for specialization and release, ownership, equipment transfers, flight prerequisites, and mounted recovery, plus native client riding/flight checks with and without JEI.

## [0.1.12] - 2026-10-16

### Fixed

- Slow combat pursuit and cleaning returns no longer report unreachable routes solely because 400 travel ticks have elapsed. Stationary companions still report blocked work, and resumed movement clears the state before arrival.
- Combat pursuit and cleaning returns preserve valid paths during hops. Pursuit refreshes its route when the target moves, and each cleaning return starts with a fresh stall timer.
- Transport slimes recheck the current work radius before deposits and tool pickups, cancelling selected storage that becomes out of range after a Detection Range change.

### Validation

- Added server regressions for advancing and stationary combat/cleaning slimes, recovery of movement, moving combat targets, exact cleaning returns, and range changes during both storage operations.

## [0.1.11] - 2026-10-15

### Fixed

- Herald warning history retains every recorded worker episode across recovery. New warnings no longer evict older records and cause repeated signals in large groups.
- Combat slimes respect the server's PVP setting and their owner's player-damage permissions when player targets are enabled.
- Management and tuner screens preserve complete integer values over native container packets, including large attributes and multiplied detection ranges. Initial editor values wait for the complete snapshot.

### Compatibility

- Network protocol 5 requires compatible client and server builds because menu-data layouts have changed. Existing worlds and recovered items keep their save format.

### Validation

- Added tests for 200 saved warning records, warning insertion without eviction, PVP and team rules, native packet round-trips in both menus, and clearing high bits after values shrink.
- Client checks include an attribute value exceeding a single native data packet's range.

## [0.1.10] - 2026-10-15

### Added

- A shared work status separates orders, execution states, blocking reasons, and cargo/tool support requests. Transport and herald behavior consume the common contract.
- Moving with an owner and monitoring active workers provide growth opportunities for companions without collection or combat actions. Delivery growth rewards and following/monitoring intervals are configurable.
- Companion saves include `SlimeDataVersion`; unversioned data remains compatible, and stored companions from unsupported future schemas cannot be released or consumed.
- Management upgrade tooltips identify stats used after specialization.

### Changed

- Newly crafted and Creative mining items summon waiting companions. The owner explicitly starts the quarry with Mine. Recovered miners retain same-area orders; relocation to another chunk or dimension still resumes a new quarry.
- Work orders can start after waiting even when the owner is not loaded; the native sitting goal no longer blocks the assigned task.
- Stamina recovery scales at 2% of maximum per second, keeping the recovery period to 25% similar across upgrade levels.
- Completed cargo deliveries and supplied tools award four activity units by default to account for transport travel.
- Transport slimes share loaded-chunk candidate snapshots for up to 20 ticks and stagger idle searches. Requests, ownership, distance, locks, and transfer capacity are rechecked before use.

### Validation

- Added coverage for initial mining orders, common requests, proportional recovery, growth and paused timers, legacy/future data, cache expiration without chunk loading, and 32 concurrent carriers sharing storage.

## [0.1.9] - 2026-10-15

### Fixed

- Partial Shift-click unloading wakes full miners even when the original bag stack remains occupied.
- Combat target settings reject Shift-click merges into hidden bag slots.
- Open transport menus return to an existing page after Capacity is reduced.
- Slime menus retain their slot layout and close safely if the companion disappears during opening. Network protocol 4 requires matching client and server versions.

### Validation

- Added regression tests for partial unloading, hidden-slot transfers, capacity changes, missing entities, and stale menu actions.

## [0.1.8] - 2026-10-15

### Fixed

- Mining in another dimension establishes a new quarry even when its X/Z chunk coordinates match the previous world.
- Fixed work areas move to the arrival point after portal transfers and dimension-restored entity copies, while ordinary same-world reloads retain their saved origins.
- Transport travel timeouts measure stalled movement, allowing slow deliveries to continue beyond 400 elapsed ticks.
- Carriers preserve valid paths during hops and refresh paths when destinations move or a route ends.
- Transport action cooldowns survive world saves and recovery, preserving Work Speed intervals.

### Validation

- Added regression coverage for dimension changes, legacy saves, cancelled transfers, work-area restoration, slow and stalled deliveries, and persisted transfer cooldowns.
- Isolated cargo-transfer integration tests in a fully prepared work chunk, independent of surrounding natural terrain.

## [0.1.7] - 2026-10-15

### Changed

- Coarser 16×16 summon and tuner icons, with dedicated textures for all six Slime Armor materials.
- Dedicated Slime Chest face textures shared by the placed block and its block-model inventory item.
- A striped conical hat for herald slimes, with the nameplate raised above it.

### Fixed

- Quarry routes now cover long perimeter detours at default stats while restricting navigation reads and the slime footprint to the work chunk.
- Valid quarry paths survive hops, and tighter waypoint arrival prevents wide slimes from cutting into staircase corners.
- Mining approaches search valid visible standing positions together rather than repeatedly pathfinding toward individual positions or solid targets.
- Support repairs check sight and reach, report stalled routes, and retry after obstructions are cleared.
- Sealed bedrock floors complete correctly when their only breakable blocks are preserved staircase supports.
- Travel timeouts measure lack of movement so long routes can finish without being abandoned solely for taking time.

## [0.1.6] - 2026-10-15

### Added

- Combat slimes specialized with a sword, with sword, shield, and Slime Armor equipment slots, native melee damage and defenses, stamina, and growth.
- Independent hostile, neutral, passive, player, tamed-mob, and creeper target switches; defaults enable only hostile mobs other than creepers.
- Slime Armor crafted from a chestplate and a slime ball, preserving the original material, durability, enchantments, color, name, and attributes, with six standard material variants.
- Cleaning slimes specialized with rotten flesh, collecting dropped items within a fixed area with five times the shared Detection Range and returning after each pickup.
- Three-minute pickup retries that prefer another available item when the current target remains inaccessible.
- Persistent combat filters, equipment, cleaning origins and retry state; transport support for combat swords and full cleaning bags.
- Visible combat armor, swords, and shields, simultaneous pickaxe/shovel display, and shared held-tool poses with upright tool heads and inward grips.
- New summon icons, armor material models, target controls, JEI previews, and bilingual guides.

## [0.1.5] - 2026-10-15

### Added

- A Creative-only Slime Tuner with a separate screen for base stats, current health, stamina, and unused points.
- Herald slimes crafted with a Friendly Slime and any firework rocket, preserving its design and signaling stalled same-owner workers without consuming more rockets or dealing damage.
- Configurable herald delays, persistent warning state, and a white small-ball firework for Creative defaults.
- A dedicated Slimic creative tab, six distinct item textures, and dye colors for every companion type.
- Whole-chunk excavation with a persistent, two-block-wide perimeter staircase and repair using common building blocks from the bag.
- Separate pickaxe and shovel equipment slots, automatic bag-spare replacement, and native held-tool orientation.

### Changed

- Friendly Slime crafting now uses eight slime balls around soul sand or soul soil.
- Releasing a miner in another chunk starts a fresh quarry automatically; releasing in the same chunk preserves its progress and order.
- Mining recipe results equip the exact input pickaxe; Creative default items have empty equipment.
- Recovery preserves dye, tuning, firework designs, alert settings, blocked-work timers, tools, cargo, and quarry progress.

## [0.1.4] - 2026-10-15

### Added

- Transport slimes crafted from a Friendly Slime and a regular chest, preserving saved companion traits.
- A transport bag with exactly twice the capacity at every shared Capacity level, supporting 18–54 slots without an equipment slot.
- Slime Chests crafted from eight wooden planks and a slime ball, with 27 persistent slots, standard inventory access, and automation support.
- Automatic unloading of same-owner workers stopped by full bags and delivery to nearby Slime Chests.
- Replacement-tool delivery from Slime Chests to workers stopped by missing or broken tools.
- Cargo retention when destinations are full or unavailable, stamina-based work, and activity earned on completed deliveries.
- A transport backpack, paged bag controls, and JEI crafting and information support.

### Changed

- Shared bag persistence and recovery across worker specializations while preserving existing mining-slime saves.

## [0.1.3] - 2026-10-15

### Added

- Shared Health, Attack, Defense, Movement Speed, Work Speed, Capacity, Stamina, Reach, and Detection Range stats with activity-earned upgrades.
- An owner management screen for naming, visible nameplates, stat upgrades, orders, pickaxe equipment, and bag access.
- Mining slimes crafted from a Friendly Slime and any pickaxe, preserving both companion traits and tool data.
- Chunk-bound mining with tool-tier checks, break progress, enchantment-aware drops, durability, stamina, and bag-full pausing.
- A mining helmet, lamp, and equipped-pickaxe rendering.
- Optional JEI recipes and information pages.
- Persistent stats, progression, equipment, and bag contents across saves and recovery.

## [0.1.2] - 2026-10-15

### Added

- Owners can recover a friendly slime into an item with Shift-right-click, including while holding another item.
- Recovery preserves the slime's saved state, including health, attributes, effects, name, owner, waiting order, identity, and custom data.
- Recovered slimes remain separate items and are consumed when released, including in Creative mode.
- Full inventories leave the slime in the world, and failed releases preserve the stored item.

### Changed

- Standardized Java source formatting with Google Java Format.

### Documentation

- Kept the bilingual READMEs focused on the project overview and moved gameplay and development details into bilingual documents under `docs`.

## [0.1.1] - 2026-10-15

### Added

- A Friendly Slime item crafted with eight slime blocks surrounding either soul sand or soul soil.
- Using the item on a block or water summons a persistent slime already tamed to the player.
- Friendly slimes hop after their owner, remain in Peaceful difficulty, and never attack or split on death.
- Owners can use an empty hand to toggle between following and waiting.
- Minecraft slime and slime-ball artwork, with English and Korean names and tooltips.

## [0.1.0] - 2026-10-15

### Added

- An empty Minecraft Forge 1.20.1 mod template with a minimal Slimic entry point.
- Gradle wrapper, development configurations, and a GitHub Actions build workflow based on Siliconic.
- English and Korean project documentation, the MIT license, and Forge credits.
