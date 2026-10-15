# Gameplay Guide

English | [한국어](GAMEPLAY.kr.md) · [Project overview](../README.md)

Slimic currently provides friendly slime companions. Raising systems and work assignments are planned for future updates.

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
| Owner right-clicks with an empty hand | Toggle following and waiting. |
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
