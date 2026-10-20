# Forgotten Orders

English | [한국어](FORGOTTEN_ORDERS.kr.md) · [The Slimic](THE_SLIMIC.md) · [Gameplay](GAMEPLAY.md)

The old kingdom's slimes still follow orders left at their ruins. Forgotten Orders connects exploration of The Slimic to raising companions: defeat a remnant, recover its damaged core, replace its ancient command, and bring a new companion home. Existing trained companions can also learn the royal guard profession without losing their growth.

## Occupied ruins

Newly generated workshops and supply depots contain an Ancient Guard, a Forgotten Transport, linked Slime Chests, an intact Command Pedestal, and an ordinary loot chest. Road fragments remain unoccupied. Archives add bookshelves, tablets, an Ancient Commander, and **The Missing Traveler** record, which links the kingdom’s beginnings to the traveler’s disappearance. Rarer royal wards add royal insignia, another guard, and **The Last Watch** record. These are weathered sections of the old capital, built within individual chunks.

| Remnant | Duty |
| --- | --- |
| Ancient Guard Slime | Patrol its ruin and attack nearby intruders. |
| Forgotten Transport Slime | Carry actual items between its own two depots; resist attackers. |
| Ancient Commander Slime | Defend its ruin and alert guards belonging to the same ruin. |

Remnants are distinct hostile entities, with no player owner or companion recovery block. Guards detect intruders within 16 blocks and end pursuit beyond 24 blocks from home. They return to the ruin instead of following an escaping player indefinitely. Creative and Spectator players and fellow ancient slimes are excluded. They can fight player companions; Combat, Dragon, and Royal Guard Slimes recognize them as hostile mobs under their normal filters.

Ancient carriers never search for arbitrary player storage. Their two depots must retain matching ruin identities. Opening a linked chest removes its ancient link, so it can be claimed for ordinary use. Breaking and replacing a chest also removes that link. Ancient hauling respects `mobGriefing`; it does not force-load chunks. Loot and protection mods can continue to cancel attacks or drops through the normal Forge events. Peaceful difficulty removes hostile remnants under native monster rules.

## Restore a remnant

Defeating a remnant yourself or with a tamed Slimic companion yields one **Damaged Ancient Slime Core** and one **Ancient Command Seal**, subject to ordinary mob-loot rules and drop events. Loot chests also guarantee seals: one in workshops and depots, two in archives, and three in royal wards. Supplies and translated records provide the first restoration clues.

1. Hold the core and keep one seal and one slimeball in the player inventory.
2. Right-click an intact **Command Pedestal**. Cracked pedestals do not work. A pedestal can also be cut from Chiseled Slimic Stone Bricks using a stonecutter.
3. The two materials are consumed once, and the held core becomes a stored companion item. No space elsewhere in the inventory is required. The same material cost applies in Creative.
4. Release the companion in a clear location. It starts in **Wait at one health**. Heal it before assigning work.

| Core's former duty | Restored companion |
| --- | --- |
| Guard | Royal Guard Slime |
| Transport | Transport Slime |
| Commander | Commander Slime |

The restored companion receives a new UUID and the restoring player's ownership. It retains an ancient name, a weathered purple insignia, and one to three Endurance upgrades according to its former training. No hostile entity snapshot, target, old order, or old ownership is imported. Ancient carriers' physical cargo remains ordinary loot rather than being copied into the new companion.

Each ancient identity can be redeemed only once across all dimensions of the saved world, including after restart. Missing materials, invalid or already restored cores, and unavailable pedestals preserve the core and materials. The usual Commander limit still applies when releasing a restored commander: one spawned Commander per connected co-owner group across the world. Restored companions then use the same recovery, revival, growth, sharing, portal, logistics, and command rules as their profession.

## Specialize a trained companion

Combine a **Combat Slime item and one Ancient Command Seal** without a fixed arrangement to make a Royal Guard Slime. This works in a 2×2 inventory grid or at a crafting table. The exact identity, owners, name, current health and stamina, invested stats, growth progress, sword, shield, armor, and cargo are retained. The new specialization starts in Wait. An equipped or spare tool is never recreated separately.

Restoration provides a new veteran with ancient collection traits. Specialization develops the existing companion the player has raised. Both paths lead to the same guard abilities.

## Royal Guard Slime

Royal Guards accept the Combat Slime's sword, shield, armor, bag, and six independent target filters. Select **Fight** to assign the current position as a guard post. Eligible targets must remain within Detection Range of the post. With no target the guard returns to its post. Follow or Wait ends active guarding; selecting Fight again assigns a new post. Recovery and portal travel relocate the post and return the guard to Wait; ordinary saving preserves an explicit work order and post.

While working with a shield, a guard can intercept **35%** of an owner's or cooperating companion's combat damage within **four blocks**. Both ally and attacker must be visible. The attacker's target type must be enabled, with the existing ownership, team, PvP, and Creative protections. Guard-to-guard damage is never redirected, and only one nearby guard intercepts an attack. Environmental damage is unaffected.

Protection costs at least four stamina, or twice the intercepted damage when larger. At least four stamina and more than one health are required. Redirection is capped at the guard's health above one; normal armor and protection still apply to the guard's hit. Blocking the redirected hit with a raised shield still spends stamina. Interception also contributes to growth. With insufficient stamina, missing equipment, no eligible attacker, or a Wait/Follow order, the original hit proceeds normally. It does not grant passive invulnerability.

Transport and Warehouse Slimes can resupply swords and unload combat loot, healers can tend the guard, Heralds report blocked orders, and Commanders gather it into Wait. A Slime Scanner shows the guard-post detection range and its smaller protection radius. Gold crowns identify Royal Guards; restored veterans additionally carry weathered purple insignia.
