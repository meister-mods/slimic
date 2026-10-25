# Collapsed Mine Recovery

English | [한국어](MINE_RECOVERY.kr.md) · [The Slimic](THE_SLIMIC.md#collapsed-mines) · [Forgotten Orders](FORGOTTEN_ORDERS.md)

Collapsed mines connect exploration to new work companions. Enter a mine in The Slimic, break its fallen workers to recover cores, collect command fragments, and restore ancient workers in the Overworld.

## Recover a core

Look for timber frames or an open quarry shaft in newly generated dry, fairly level chunks. Mines contain either descending 3×3 passages or an unfinished 16×16 quarry with two-wide perimeter stairs. A terminal alcove in the tunnel gives the remaining workers room to move.

Mineralized Broken Slime Blocks have a purple core visible on their faces. **Break a corpse to receive one damaged core.** Any tool works; Silk Touch does not produce a second corpse. The corpse does not contain an owner or a stored companion, and a slimeball cannot revive it directly. Normal block-drop rules and protection events still apply.

Quarry corpses yield Damaged Mining Slime Cores; tunnel corpses yield Damaged Tunnel Slime Cores. Each newly generated site has a Slime Chest containing one Old Command Fragment and one slimeball per corpse, an iron pickaxe, and 32 cobblestone. This chest is usable by ordinary companion logistics and has no ancient carrier link.

Older, empty remains generated before 0.3.7 have no core. A player's fallen companion continues to preserve its identity and belongings in a normal Broken Slime Block and uses the existing [revival procedure](GAMEPLAY.md#hostile-encounters-and-revival).

## Remaining mine slimes

| Remnant | Base health / attack / armor | Behavior | Core result |
| --- | --- | --- | --- |
| Forgotten Mining Slime | 48 / 6 / 6 | Slowly works one old natural face in a quarry; attacks intruders. | Ancient Mining Slime |
| Forgotten Tunnel Slime | 56 / 6 / 6 | Slowly clears the old 3×3 tunnel face; attacks intruders. | Ancient Tunnel Slime |
| Mine Guardian Slime | 96 / 10 / 12 | Defends the workers and reward area. | Royal Guard Slime |

Mine remnants share the ancient faction's alliance and 24-block home boundary. They ignore Creative and Spectator players. Combat companions recognize them as hostile under their normal filters; Royal Guards can defend cooperating workers. Peaceful removes hostile remnants without removing the buried cores and chest supplies.

Old mining orders affect only a fixed 3×3 face, at most nine blocks per worker. They use effective tools, ordinary breaking progress, loaded terrain, `mobGriefing`, Forge break protection, and persistent quarry-stair protection. They do not mine player containers, fluids, arbitrary buildings, or new distant tunnels. Their work count and face survive saving; spawn eggs have no bound work face.

Defeating a mine remnant yourself or with a tamed Slimic companion yields one role-specific core and one command fragment, subject to mob-loot rules and events. Existing guards, carriers, and command slimes in surface ruins continue to drop a seal with their core.

## Restore in the Overworld

1. Bring the core to the Overworld and place an intact **Slime Restoration Station** (`slimic:command_pedestal`). Find a station in occupied ruins or craft one with a slimeball, gold ingot, two redstone dust, and three Stone Bricks.
2. Hold the core. Keep **one Old Command Fragment and one slimeball** in your inventory.
3. Right-click the station. It consumes both materials once and replaces the core in your hand with the restored companion item. The same cost applies in Creative.
4. Release the companion in a clear place. It has **one health and the Wait order**. Heal it, provide equipment and supplies, and explicitly start work.

Each core's identity can be redeemed once across the entire saved world. A failed attempt preserves the core and materials. Restoration creates a new UUID and makes the restoring player the primary owner. The companion retains inherited Stamina training and a saved name when present. Tamed ancient companions have a **white head insignia**. Hostile remnants retain purple insignia.

The main Slimic Creative tab contains both ancient work companions, the three mine-remnant spawn eggs, command fragments, and three mine-core variants. The cores appear with the other restoration materials. Core variants taken from Creative receive independent identities when used. JEI supplies acquisition instructions; these ancient work professions have no crafting recipe.

With JEI installed, open a core’s uses or a companion’s recipes to view the **Slime Restoration** category. All six core roles show one matching seal or command fragment, one slimeball, and the resulting companion. The station is a reusable catalyst. Mine entries state the Overworld requirement and all entries show the one-health Wait state and invested training.

## Ancient Mining Slime

Uses the ordinary [Mining Slime](GAMEPLAY.md#mining-slimes) equipment, Mine order, quarry plan, stairs, fluid handling, and wall reinforcement. It prioritizes visible Forge-tagged ores **within the active layer** and spends half the normal stamina on work and movement. It does not reveal distant deposits, skip layers, bypass unsuitable tools, or change item drops.

Its summon item uses the shared flat gold-block badge with a mining-core icon and purple border. The companion’s helmet, held tools, and white ancient insignia remain visible when its body is dyed.

## Ancient Tunnel Slime

Uses the ordinary [Tunnel Slime](GAMEPLAY.md#tunnel-slimes) direction, 3×3 bore, bucket, bag, and nonconsumable lining template. It spends half the normal stamina on work and movement. Effective tools break blocks in `slimic:ancient_masonry` 50% faster: Slimic Stone, Slimic Cobblestone, Slimic Stone Bricks, Chiseled Slimic Stone Bricks, Ancient Foundation, and Ancient Foundation Bricks. The bonus does not apply to ineffective tools or insufficient mining tiers.

Lining and reinforcement consume actual materials. This slime cannot pass through solid rubble or ignore webs; clear the approach before work. Its summon item uses the shared flat gold-block badge with a tunnel-core icon and purple border. The companion wears a helmet, minecart shell, and white ancient insignia.

## Cooperation and preservation

Transport and Warehouse Slimes support ancient workers through the same requests as ordinary Mining and Tunnel Slimes. They unload full bags and supply missing tools, buckets, wall cobblestone, or lining materials. Signaling Slimes report their named work stoppages; healing, shared ownership, Command gathering, scanners, growth, recovery, and portals follow ordinary companion rules.

Every new or released companion starts in Wait. Recovery preserves health, stamina, stats, tools, cargo, identity, and work data; ordinary world reload retains a saved work order. Ancient and ordinary miners cooperate on shared stairs, and tunnel lining cannot overwrite protected quarry steps.

Station recipe (workbench):

| Empty | Slimeball | Empty |
| --- | --- | --- |
| Redstone | Gold ingot | Redstone |
| Stone Bricks | Stone Bricks | Stone Bricks |

Restoration distributes 12–28 invested upgrade points among stats used by the profession, using the core’s identity to fix its random roll. This total includes inherited Stamina training and follows ordinary upgrade effects and level caps. Points are already invested and count toward future growth costs. Recovery, release, portal travel, and saving preserve the allocation without rerolling or stacking it. All newly restored ancient roles, including ruin guards, carriers, and command slimes, use this rule; already restored companions and crafting specialization keep their existing training.
