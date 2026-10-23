# Colony Bonding

[한국어](COLONY_BONDING.kr.md) · [The Aimless Colony](AIMLESS_COLONY.md) · [Royal synthesis](SYNTHESIS.md)

## Make a reusable station

Craft a Colony Bonding Station with **one Colony Heart, two iron ingots, three slimeballs and three stone bricks**. The heart comes from an assembly chamber's personal first-clear reward, its Peaceful alternative, or four Cohesion Shards. A completed station is reusable; binding and unbinding do not consume another heart.

Colony Bonding preserves the original two companions. Royal Synthesis remains a separate, irreversible ordinary-plus-ancient inheritance process with a new UUID. The devices, recipes and transaction records are separate. Existing sanctum rewards and synthesis ingredients do not change.

## Bind two companions

Initially supported pairs are **two ordinary Transport Slimes** or **two ordinary Combat Slimes**. The exact entity types must match. Upper variants, other professions, mixed professions and groups larger than two are unsupported.

Recover both living companions and place their stored items in the station. You must be the primary owner of both. Equipment and cargo may stay inside. Finish temporary effects, absorption, burning, freezing and low-air states first. Unrepresentable combined health or stamina is also rejected while preserving the originals. An already active, reserved, bound, damaged or invalid identity is rejected without consuming the inputs. Unsupported external attachments are rejected to avoid silently discarding data.

The screen shows both names, current health and stamina, unused individual points and occupied bag slots. It previews the colony's maximum health, capacity, attack and stamina. Inspect the result before confirming **Bind**. One stored representative is delivered to your inventory; pending delivery can be recovered at any bonding station.

## Operate the colony

The world contains **one representative entity**, rendered as two joined gel bodies. The original UUIDs remain reserved inside its member records and cannot be deployed separately while bound. The representative has one movement and work order. New deployment and recovered deployment select **Wait**; ordinary world reloads preserve an existing explicit order.

| Property | Behavior |
| --- | --- |
| Health and maximum stamina | Sum of both members, within finite native numeric representation. |
| Current health and stamina | Actual remaining totals; damage, healing and expenditure are reflected in the constituent records. |
| Cargo | Sum of the two real capacities. Transfers address the actual member bags, with no parallel copy. |
| Transport bonus | Already included in each ordinary Transport member's capacity. It is not applied again to the total. |
| Movement and work speed | The lower of the two member values. |
| Attack base | 60% of the combined individual attack values, before the active weapon's native effects. |
| Combat equipment | The first input member supplies the active sword, shield and armor; wear and replacements alter that member's real items. The second member's gear remains stored. |
| Combat target filters | Initial group filters follow the first member. Group filter changes do not overwrite either member's saved individual filters. |
| Growth | Completed activity alternates between members. Their points and investment remain individual, rather than pooled. |
| Training and Creative tuning | Apply to the first input member's point balance. Derived colony stat edits distribute to the members; life, energy and capacity are combined values. |

Both member colors and equipment remain visible. The representative can follow, wait, work, change dimension, receive logistics support, heal, be recovered and be revived through the common companion systems. Its one shared order concentrates capacity or survivability but cannot run two distant jobs or provide two independent attacks.

Recovering or separating a colony does not refill its members. Rename the representative independently if desired; original member names are preserved for unbinding. Dyeing or washing the colony updates member one. Temporary effects, absorption, burning, freezing and air acquired by the shared body transfer once to member one with their remaining amount and duration. Temporary bonus health survives recovery and unbinding. To train the other member directly, unbind and either manage it normally or put it first when binding again.

## Unbind safely

Recover the colony, place it **alone in the first station slot**, inspect both records and confirm **Unbind**. The station needs two nearby loaded landing spaces with solid floors, sufficient collision clearance and no fluids. Blocked space, cancelled admission or failed pre-commit storage keeps the colony available.

On success, the original **two UUIDs** return nearby in **Wait**, retaining their remaining health, stamina, equipment, cargo, ownership, individual growth and profession settings. The representative is retired. Copied pre-binding items and old colony items cannot deploy duplicate identities.

If the colony collapses, the common broken-companion system preserves its member records. A slimeball revival restores the representative with **one total health**, shared across the members. It does not restore two full-health companions. Recover and unbind it after revival when safe.

## Persistence and recovery

`ColonyMembers.Schema=1` stores two native, versioned companion records. `slimic_colony_bonding` tracks membership, release epochs and interrupted transactions independently from `slimic_royal_synthesis`. Admission and stored-item restoration validate current identity authority. Old member and representative epochs cannot be reused after the transition.

Binding checkpoints the membership and recoverable output before clearing the station's inputs. Loading a stale station reconciles already committed inputs. Unbinding reserves a release, admits both originals provisionally, verifies both admissions, and commits their new epochs before retiring the representative. Native world checkpoints and recovery records cover interrupted transitions without acquiring unloaded chunks.

Use **Recover** at any Colony Bonding Station for pending delivery or a recoverable interrupted transition. Keep inventory space for the result. A full inventory does not erase the record. JEI lists the two binding and two unbinding operations; Patchouli provides the process in both languages. Neither optional mod is required to use the station.
