# Gameplay Guide

English | [한국어](GAMEPLAY.kr.md) · [Project overview](../README.md)

Slimic provides friendly companions with upgradeable stats and a mining specialization.

## Installation

Use Minecraft 1.20.1, Forge 47.x (developed against 47.4.10), and Java 17. Place `slimic-1.20.1-*.jar` from the build or release into the Minecraft `mods` folder, then launch the Forge profile. Do not install the `-sources.jar` development artifact.

## Crafting a Friendly Slime

Place eight slime blocks around one soul sand or soul soil in a crafting table to make one **Friendly Slime** item:

| Slime block | Slime block | Slime block |
| --- | --- | --- |
| Slime block | Soul sand / soul soil | Slime block |
| Slime block | Slime block | Slime block |

The item is also available in the Spawn Eggs creative tab.

## Summoning and controls

Use the item on a block or water source, like a spawn egg. A newly crafted item summons a slime already tamed to the player using it. A successful Survival use consumes one item; a newly crafted item is reusable in Creative mode.

An anvil name on the item becomes the slime's custom name.

| Action | Result |
| --- | --- |
| Owner right-clicks | Open the management screen for stats, upgrades, naming, equipment, inventory, and orders. |
| Owner Shift-right-clicks | Recover the slime into an inventory item, even while holding another item. |
| Use a recovered item on a block or water source | Release the same companion with its saved traits. |

Only the owner can recover the slime or change its follow/wait order. Releasing a recovered item preserves the original owner, even if someone else uses the item.

## Recovery and preserved traits

Recovery saves the slime's complete persistent state. Its current health is preserved rather than refilled. Saved traits include:

- Maximum health, other attributes, and permanent attribute modifiers.
- Active effects and their remaining durations.
- Custom name, owner, and follow/wait order.
- Identity, age, equipment, and other saved entity properties.
- Custom persistent data used for future slime traits.

Recovered slimes occupy separate, unstackable items. Their saved state survives inventory, chest, and world saves. Effect durations and other saved timers stay paused while the slime is an item.

The main inventory needs an empty slot. If it is full, recovery fails and the slime stays in the world with its state intact. A dead or dying slime cannot be recovered.

Releasing a recovered item consumes that item in both Survival and Creative mode. A failed release, such as placement inside solid blocks, leaves the stored item unchanged.

The slime appears at the selected destination. Old movement and fall distance are reset so recovery does not carry a previous fall into the new placement. Leashes are returned on recovery, and riding relationships are detached.

## Companion behavior

Friendly slimes hop after their owner and retain ownership and the wait order across saves. They persist in Peaceful difficulty, never attack, and do not split on death.

The slime texture and item icon use Minecraft's slime and slime-ball artwork. See [CREDITS.txt](../CREDITS.txt).

## Shared stats and growth

Right-click an owned slime to open its management screen. The name field sets a visible name above the slime, like a name tag. Follow and Wait buttons change its order. Mining slimes also have a Mine button, one pickaxe slot, and a bag.

| Stat | Initial value | Per point | Purpose |
| --- | --- | --- | --- |
| Health | 16 | +4 | Maximum health; upgrading does not refill current health. |
| Attack | 2 | +1 | Base attack damage; mining companions do not automatically fight. |
| Defense | 0 | +1 | Minecraft's standard armor attribute and damage calculation. |
| Movement Speed | 0.30 | +0.015 | Minecraft movement-speed attribute. |
| Work Speed | 1.0× | +0.1× | Multiplier for mining progress. |
| Capacity | 9 slots | +3 slots | Usable bag slots, up to 27. |
| Stamina | 100 | +20 | Maximum energy for continuous work. |
| Reach | 3 blocks | +0.25 blocks | Maximum distance for a mining action. |
| Detection Range | 8 blocks | +1 block | Radius for finding mining targets. |

Capacity supports six upgrades; the other stats support twenty. Only the owner can spend points, rename the companion, or use its management inventory. All stats, unused points, activity progress, and current stamina survive world saves and recovery.

The first stat point requires 256 successfully mined blocks. Each earned point increases the next requirement by 64 blocks, capped at 4,096. Standing idle, following, changing orders, and recovering or summoning a slime grant no activity. Failed or cancelled mining actions grant none.

## Mining slimes

Craft a Friendly Slime item with any pickaxe, without a fixed shape, to make a **Mining Slime**. The equipped tool retains its type, damage, name, and enchantments. A recovered friendly slime can also specialize without losing its saved traits.

Mining slimes wear a yellow mining helmet with a lamp and display their equipped pickaxe. Newly crafted miners start mining when summoned and restrict targets to the chunk where they were released. Releasing a recovered miner preserves its saved order and establishes the new destination chunk as its work area.

They search for visible, nearby blocks in the pickaxe-mineable group. Tool tier must meet the block's requirements. Block entities, unbreakable blocks, targets outside the chunk, and targets outside detection range are skipped. The miner approaches distant targets and mines only within its Reach.

The actual pickaxe determines break speed and drops, including Fortune and Silk Touch. Durability is consumed normally, including Unbreaking. Broken or missing pickaxes stop work until a replacement is placed in the equipment slot.

Drops go directly into the bag. If all drops cannot fit, the block stays intact, the tool is not damaged, and the miner pauses. Removing items or upgrading Capacity lets work resume. Bag items can be moved to the player's inventory, including with Shift-click.

Each completed block consumes one stamina. At exhaustion the miner rests, recovering two stamina per second until reaching 25% of maximum. Waiting, following, and other non-mining time also restore stamina. The mobGriefing game rule and Forge block-break permissions are respected.

## JEI

JEI is optional. When installed, it shows the Friendly Slime recipe, Mining Slime combinations for registered pickaxes, and companion information pages. Mining recipe previews show the corresponding equipped tool. Slimic also runs without JEI.
