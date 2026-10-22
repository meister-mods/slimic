# Royal synthesis

English | [한국어](SYNTHESIS.kr.md) · [Fallen Sanctum](FALLEN_SANCTUM.md) · [Gameplay](GAMEPLAY.md)

Royal synthesis consumes two recovered companions to create one new companion. In 0.4.x the supported families are **ordinary Mining Slime + Ancient Mining Slime → Next-Generation Mining Slime** and **ordinary Tunnel Slime + Ancient Tunnel Slime → Next-Generation Tunnel Slime**. Guard synthesis is not implemented. A next-generation companion cannot be a parent for another synthesis.

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
| Precision Prospecting | Dig a 2×2 shaft and supported branches toward selected ores behind walls, bypassing unsafe terrain |

**Precision Prospecting** replaces Selective Ore. It descends through a **two-by-two vertical shaft**, one supported level at a time. Its physical width and height are 75% of an ordinary miner, about **0.78 blocks**. Branch passages are **two blocks wide and two high**. Issue a work order separately through the Tuner.

Loaded terrain is surveyed for enabled Forge ore tags, including deposits behind walls. Supported branches lead toward selected deposits; rising or descending branches can advance two blocks and change height by one. Actual breaking proceeds through visible blocks within tool reach and prioritizes exposed selected ores. Rock, dirt, and unselected ores obstructing a physical passage also enter its bag. Drop amounts and required tool tiers remain unchanged. Filters cover coal, copper, iron, gold, redstone, lapis, diamond, emerald, quartz, and ancient debris.

Each next passage checks all four floor blocks, its body volume, adjacent fluids, and falling-block roof. Magma blocks are avoided, and sand or gravel floors must have solid support beneath their falling column. Unsupported cavities and routes opening into water or lava are avoided. A blocked shaft prompts a lateral search for another safe descending column. If none is safe, it pauses as unreachable. It never forcibly removes fluids or jumps into unsupported holes. Protected stairs, block entities, unbreakable blocks, and protection callbacks remain respected. Completed branches retain a return trail to the shaft, and every physical step is checked again before excavation.

Prospecting never acquires new chunks. Ordinary trained detection ranges apply; very large Creative values still use a per-survey window capped at radius 32. No access ladder or surface-return staircase is installed, so transport companions need a separate usable return route for deep work. Changing selected tags restarts target selection while retaining shaft progress.

The Scanner previews the **two-by-two shaft**, detection window, and immediate planned face. Previewing alone neither surveys terrain nor reveals hidden ore positions.

Health, maximum stamina, and capacity use ordinary Mining Slime values; **the ancient miner’s half-stamina-cost passive is inherited in every policy**. Transport and Warehouse Slimes supply tools and unload cargo where they can reach; Signaling Slimes report blocked work. Recovery, revival, portals, and redeployment preserve ordinary companion data and return it to Wait. Old Selective Ore saves retain their policy and traits but migrate to Wait; explicitly resume work before the newly destructive excavation begins.

## Configurable tunnel bores

A **Next-Generation Tunnel Slime** is synthesized from an ordinary Tunnel Slime and an Ancient Tunnel Slime. It uses the same four slimeballs, two Old Command Fragments, parent validation, growth refund, and transaction recovery as mining synthesis. The parents’ equipment and bags must be empty. Both parents are consumed, and the new companion starts in **Wait** with its own identity and a default **3×3** bore.

Open **Slime Tuner → Bore size**, enter a positive integer **n**, and Apply. It excavates a square **n blocks wide and n high**: 1×1, 2×2, 5×5, and larger sizes use the same rules. Size changes and direction changes select Wait; issue Tunnel separately. The maximum configurable size is the dimension’s build height minus the floor and roof margins. The complete face and its lining must fit at the working elevation and inside the world border; otherwise it pauses before changing that face. There is no upgrade cost for selecting a size.

The compact 0.78-block body and narrow cart fit a 1×1 bore. From its entrance it extends work reach across the active face, excavates individual blocks with native tool time and wear, repairs the floor, roof, and sides, and then physically advances. Odd and even widths preserve the same centerline. A larger bore increases total excavation and material needs. It surveys at most 128 cells per tick and performs one completed block action per tick, without loading missing chunks. Lining cells are calculated as needed. If a new block obstructs forward movement after the final scan, it checks the face again and resumes excavation.

The lining template remains a nonconsumable reference; actual building blocks, tools, and an empty bucket come from the bag. Transport and Warehouse Slimes provide the same supply and cargo services, while Signaling Slimes report blocked work. Tier requirements, container protection, shared quarry stairs, Forge permissions, and `mobGriefing` still apply. It inherits half stamina costs and a 1.5× work multiplier on ancient masonry. Size, direction, template, face progress, and drained-fluid cells persist in saves; recovered deployment starts in Wait.

## Interrupted transactions

The shared saved-world transaction ledger records the parent UUIDs, before and after inputs, result identity, recipient, and delivery receipt before the inputs are consumed. A stale station image is reconciled with the committed record. Repeated confirmation, another station, or copied parent items cannot reuse consumed parents. External mods may cancel the pre-commit event.

A committed result belongs to its recipient even if its original station is broken. Use **Recover pending result** at any working Royal Synthesis Station. A new result stays reserved until the player save succeeds; a receipt already present in inventory is checkpointed rather than duplicated. Keep space available and retry after resolving storage errors.

JEI displays a dedicated station synthesis category when installed. Patchouli includes the station recipe, preparation, point inheritance, policies, and sanctum path in English and Korean. Neither optional mod is required for synthesis.
