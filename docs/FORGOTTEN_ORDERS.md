# Forgotten Orders

English | [한국어](FORGOTTEN_ORDERS.kr.md) · [The Slimic](THE_SLIMIC.md) · [Gameplay](GAMEPLAY.md)

The old kingdom's slimes still follow orders left at their ruins. Forgotten Orders connects exploration of The Slimic to raising companions: defeat a remnant, recover its damaged core, replace its ancient command, and bring a new companion home. Existing trained companions can also learn the royal guard profession without losing their growth.

[Collapsed Mine Recovery](MINE_RECOVERY.md) extends this system with three mine remnants, corpse cores, command fragments, and ancient work companions. Mine cores use fragments rather than seals and must be restored in the Overworld.

## Occupied ruins

Newly generated workshops and supply depots contain an Ancient Guard, a Forgotten Transport, linked Slime Chests, an intact Slime Restoration Station, and an ordinary loot chest. Road fragments remain unoccupied. Archives add bookshelves, tablets, an Ancient Command Slime, and **Waiting for XXX** record, which links the kingdom’s beginnings to XXX’s absence. Rarer royal wards add royal insignia, another guard, and **The Last Watch** record. These are weathered sections of the old capital, built within individual chunks.

| Remnant | Duty |
| --- | --- |
| Ancient Guard Slime | Patrol its ruin and attack nearby intruders. |
| Forgotten Transport Slime | Carry actual items between its own two depots; resist attackers. |
| Ancient Command Slime | Defend its ruin and alert guards belonging to the same ruin. |

Remnants are distinct hostile entities, with no player owner or companion recovery block. Guards detect intruders within 16 blocks and end pursuit beyond 24 blocks from home. They return to the ruin instead of following an escaping player indefinitely. Creative and Spectator players and fellow ancient slimes are excluded. They can fight player companions; Combat, Dragon, and Royal Guard Slimes recognize them as hostile mobs under their normal filters.

Ancient carriers never search for arbitrary player storage. Their two depots must retain matching ruin identities. Opening a linked chest removes its ancient link, so it can be claimed for ordinary use. Breaking and replacing a chest also removes that link. Ancient hauling respects `mobGriefing`; it does not force-load chunks. Loot and protection mods can continue to cancel attacks or drops through the normal Forge events. Peaceful difficulty removes hostile remnants under native monster rules.

## Finding a royal ward

Enter The Slimic through a slime-block portal and explore newly generated surface chunks. Royal wards are rare variants among the ruins, marked by royal crests, old slime banners, a command slime, and extra guards. They have no fixed coordinates, separate portal, or entry requirement. Flat land above sea level can contain ruins; steep terrain and existing containers prevent placement. Ruins are world-generation features, so `/locate structure` does not locate them. Older generated chunks do not gain new ruins automatically.

Base health, attack damage, and armor are 64/8/8 for Ancient Guards, 48/6/6 for Forgotten Transports, and 96/12/12 for Ancient Command Slimes. Unmodified remnants from earlier saves receive the new defaults once while retaining their health proportion, ruin, cargo, and name. Custom attributes remain unchanged. Creative provides one ordinary spawn egg per hostile role. Spawn eggs create unowned remnants; carriers without linked ruin depots have no delivery route.

The recovered books speak in the voices of slimes who kept supplies, pressed records into clay, and stood watch. The name `XXX` obscures a missing figure. Their experiences leave restoration clues without describing player controls or progression systems.

## Restore a remnant

Creative provides Guard, Transport, and Command core variants in the main Slimic tab, beside the seal and restoration station. Their tooltips identify the former role. Each newly taken core can be restored using the same materials and procedure.

The **Slime Restoration Station** is the intact pedestal found in ruins. Find it beside the seal in the main Slimic Creative tab. Right-click without a core for instructions, or look up the station in the guidebook’s **Slime restoration** entry.

Defeating a ruin remnant yourself or with a tamed Slimic companion yields one **Damaged Ancient Slime Core** and one **Ancient Command Seal**, subject to ordinary mob-loot rules and drop events. Chest chances for one seal are 20% in workshops and depots, 35% in archives, and 50% in royal wards. Supplies and translated records provide the first restoration clues.

1. Hold the core and keep one seal and one slimeball in the player inventory.
2. Right-click an intact **Slime Restoration Station**. Cracked pedestals do not work. Craft a station with a slimeball, gold ingot, two redstone dust, and three Stone Bricks.
3. The two materials are consumed once, and the held core becomes a stored companion item. No space elsewhere in the inventory is required. The same material cost applies in Creative.
4. Release the companion in a clear location. It starts in **Wait at one health**. Heal it before assigning work.

| Core's former duty | Restored companion |
| --- | --- |
| Guard | Royal Guard Slime |
| Transport | Transport Slime |
| Command | Command Slime |

The restored companion receives a new UUID and the restoring player's ownership. It retains an ancient name, a white insignia, and inherited Endurance training. Restoration also distributes a core-stable total of 12–28 invested points among useful profession stats. No hostile entity snapshot, target, old order, or old ownership is imported. Ancient carriers' physical cargo remains ordinary loot rather than being copied into the new companion.

Each ancient identity can be redeemed only once across all dimensions of the saved world, including after restart. Missing materials, invalid or already restored cores, and unavailable pedestals preserve the core and materials. The usual Command Slime limit still applies when releasing a restored command slime: one spawned Command Slime per connected co-owner group across the world. Restored companions then use the same recovery, revival, growth, sharing, portal, logistics, and command rules as their profession.

JEI’s **Slime Restoration** category shows all six station restoration paths. View a core’s uses, a catalyst’s uses, or a resulting companion’s recipes to see the matching ingredients and result. The intact station is the reusable recipe catalyst; mine cores require the Overworld and use command fragments instead of seals.

## Specialize a trained companion

Combine a **Combat Slime item and one Ancient Command Seal** without a fixed arrangement to make a Royal Guard Slime. This works in a 2×2 inventory grid or at a crafting table. The exact identity, owners, name, current health and stamina, invested stats, growth progress, sword, shield, armor, and cargo are retained. The new specialization starts in Wait. An equipped or spare tool is never recreated separately.

Restoration provides a new veteran with ancient collection traits. Specialization develops the existing companion the player has raised. Both paths lead to the same guard abilities.

## Royal Guard Slime

Maximum health is four times the corresponding common value: initially 64 (32 hearts), with 16 added per normal Health upgrade, matching the Guardian Angel. A permanent profession multiplier preserves the original base and training. Legacy profession bonuses are replaced without stacking; current health is retained up to the new maximum, without healing. Creative base Health has no gameplay upper limit.

Royal Guards accept the Combat Slime's sword, shield, armor, bag, and six independent target filters. Select **Fight** to assign the current position as a guard post. Eligible targets must remain within Detection Range of the post. With no target the guard returns to its post. Follow or Wait ends active guarding; selecting Fight again assigns a new post. Recovery and portal travel relocate the post and return the guard to Wait; ordinary saving preserves an explicit work order and post.

While working with a shield, a guard can intercept **35%** of an owner's or cooperating companion's combat damage within **four blocks**. Both ally and attacker must be visible. The attacker's target type must be enabled, with the existing ownership, team, PvP, and Creative protections. Guard-to-guard damage is never redirected, and only one nearby guard intercepts an attack. Environmental damage is unaffected.

Protection costs at least four stamina, or twice the intercepted damage when larger. At least four stamina and more than one health are required. Redirection is capped at the guard's health above one; normal armor and protection still apply to the guard's hit. Blocking the redirected hit with a raised shield still spends stamina. Interception also contributes to growth. With insufficient stamina, missing equipment, no eligible attacker, or a Wait/Follow order, the original hit proceeds normally. It does not grant passive invulnerability.

Transport and Warehouse Slimes can resupply swords and unload combat loot, healers can tend the guard, Signaling Slimes report blocked orders, and Command Slimes gather it into Wait. A Slime Scanner shows the guard-post detection range and its smaller protection radius. Gold crowns identify Royal Guards; restored veterans additionally carry white insignia.
