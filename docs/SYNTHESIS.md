# Royal synthesis

English | [한국어](SYNTHESIS.kr.md) · [Fallen Sanctum](FALLEN_SANCTUM.md) · [Gameplay](GAMEPLAY.md)

Royal synthesis consumes two recovered companions to create one new companion. In 0.4.x the supported family is **ordinary Mining Slime + Ancient Mining Slime → Next-Generation Mining Slime**. Tunnel and guard families have no synthesis recipes yet. A next-generation companion cannot be a parent for another synthesis.

## Station and materials

Obtain a **Royal Synthesis Core** from a Fallen Sanctum's deep relay. Craft a **Royal Synthesis Station** using this pattern:

```text
Gold ingot | Royal Synthesis Core | Gold ingot
Redstone   | Ancient Command Seal | Redstone
Stone brick| Stone brick          | Stone brick
```

The core is consumed when crafting; the placed station is reusable. It is separate from `slimic:command_pedestal`, the existing Slime Restoration Station. The laboratory apparatus is ruined; craft a working station with the core. The core's origin is carried into the crafted station and its children's ancestry.

Each synthesis consumes **four slimeballs and two Old Command Fragments**. An Ancient Command Seal is a different item used to craft the station; it does not replace the two fragments.

## Preparing and confirming

1. Recover one ordinary Mining Slime and one Ancient Mining Slime. Both must be living stored companions with valid identities.
2. Be the **primary owner of both parents**. Shared management alone does not authorize synthesis. A live parent or conflicting identity record is rejected.
3. Empty every equipment slot and both bags, including spare tools and other stored items. Refusals identify the parent and occupied location. Items remain in their original slots. A crafting pickaxe still stored in a newly specialized mining item also counts as equipment: deploy the parent, remove the tool, and recover it again. Unknown external inventory attachments are refused rather than consumed.
4. Insert the parents and reagents in the station. Read the point preview and status. Preview consumes nothing.
5. Keep one free inventory slot and confirm. The server revalidates ownership, contents, parent identity, materials, and growth history before committing. Both parents are consumed exactly once.

The result has a **new UUID**, primary owner, and companion record. It starts in **Wait**, with no inherited custom name, equipment, cargo, or invested levels. Choose a name using a Slime Tuner. Its item preserves the parents' display names and UUIDs, family, synthesis game time, and known origin sanctum as ancestry; advanced tooltips expose identity details.

## Growth refund

All verified **earned points** from both parents return as unused points. This includes already invested levels and unused balances. It does not sum final attribute values, profession multipliers, ancient passives, or equipment effects. Formal ancient restoration training counts when it is represented by the normal growth record.

| Parent | Invested | Unused | Earned total |
| --- | ---: | ---: | ---: |
| Ordinary miner | 21 | 4 | 25 |
| Ancient miner | 16 | 2 | 18 |
| New child | 0 | **43** | **43** |

The next point costs `min(4096, 32 + earned × 64)` activity units, including inherited growth. At 43 inherited points it costs 2,784. Normal per-stat training limits remain unchanged. Extra points remain unused after those limits are reached; there is no hidden training-cap refund reduction.

Whole balances use nonnegative signed 64-bit storage and exact menu synchronization. An unrepresentable sum is rejected with both original inputs preserved. Creative-edited values do not become inherited points. Older 0.3.x companions qualify when invested levels plus unused points match the saved earned-point history; uncertain histories are preserved and explicitly refused.

## Mining policies

Open the new miner's ordinary **Slime Tuner management screen → Policy**. Its default is Normal, and selecting a policy does not issue a Mine order.

| Policy | Behavior |
| --- | --- |
| Normal | Ordinary current-chunk quarrying with the existing fluid, wall, tool, and shared-stair rules |
| Ore Priority | Prefer visible Forge-tagged ores in the active quarry layer; do not expose distant deposits or skip layers |
| Selective Ore | Mine only enabled Forge ore tags that are visible and accessible within the current chunk and detection range |

Selective Ore offers coal, copper, iron, gold, redstone, lapis, diamond, emerald, quartz, and ancient debris tag filters. It does not clear unselected surrounding stone to reach hidden ore, excavate quarry layers, drain the site, or build quarry walls. An inaccessible deposit reports blocked work. Protection events, tool speed and tier, protected staircases, bags, and stamina still apply.

A Slime Scanner shows the selective search window clipped to the current chunk and detection height. It does not display a full-depth quarry, invent a next excavation layer, or reveal hidden ores.

The miner uses ordinary Mining Slime health, stamina, and capacity. It does not inherit the ancient miner's half-cost passive. Transport and Warehouse Slimes supply tools and unload cargo; Herald Slimes report blocked work. Recovery, revival, portals, and redeployment retain the ordinary preservation rules and return it to Wait.

## Interrupted transactions

The shared saved-world transaction ledger records the parent UUIDs, before and after inputs, result identity, recipient, and delivery receipt before the inputs are consumed. A stale station image is reconciled with the committed record. Repeated confirmation, another station, or copied parent items cannot reuse consumed parents. External mods may cancel the pre-commit event.

A committed result belongs to its recipient even if its original station is broken. Use **Recover pending result** at any working Royal Synthesis Station. A new result stays reserved until the player save succeeds; a receipt already present in inventory is checkpointed rather than duplicated. Keep space available and retry after resolving storage errors.

JEI displays a dedicated station synthesis category when installed. Patchouli includes the station recipe, preparation, point inheritance, policies, and sanctum path in English and Korean. Neither optional mod is required for synthesis.
