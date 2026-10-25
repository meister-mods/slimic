# Colony Bonding

[한국어](COLONY_BONDING.kr.md) · [The Aimless Colony](AIMLESS_COLONY.md) · [Royal synthesis](SYNTHESIS.md)

## Make a reusable station

Craft a Colony Bonding Station with **one Colony Heart, two iron ingots, three slimeballs and three stone bricks**. The heart comes from an assembly chamber's personal first-clear reward, its Peaceful alternative, or four Cohesion Shards. A completed station is reusable; binding and unbinding do not consume another heart.

Colony Bonding preserves the original two companions, which can be separated again. Royal Synthesis consumes an ordinary companion and an ancient companion to create a new one; that process cannot be reversed. Each process uses its own station and materials.

## Bind two companions

Supported pairs are **two ordinary Transport Slimes** or **two ordinary Combat Slimes**. The exact entity types must match. Advanced variants, other professions, mixed professions and groups larger than two are unsupported.

Recover both living companions and place their stored items in the station. You must be the primary owner of both. Equipment and cargo may stay inside. Wait until both companions have no status effects, absorption health, fire or freezing, and have recovered their full air supply. If combined health or stamina exceeds the supported numeric range, bonding is refused and both companions are preserved. An already active, reserved, bound, damaged or invalid identity is rejected without consuming the inputs. Unsupported external attachments prevent bonding, preserving the originals.

The screen shows both names, current health and stamina, unused individual points and occupied bag slots. It previews the colony's maximum health, capacity, attack and stamina. Inspect the result before confirming **Bind**. One stored representative is delivered to your inventory; pending delivery can be recovered at any bonding station.

## Operate the colony

The colony acts as **one companion**, shown as two joined gel bodies. It retains both original companions, which cannot be deployed separately while bound. The representative has one movement and work order. New deployment and recovered deployment select **Wait**; ordinary world reloads preserve an existing explicit order.

| Property | Behavior |
| --- | --- |
| Maximum health and stamina | Sum of both members, within the supported numeric range. |
| Current health and stamina | Actual remaining totals; damage, healing and expenditure are reflected in the constituent records. |
| Cargo | Sum of the two real capacities. Transfers address the actual member bags, with no parallel copy. |
| Transport bonus | Already included in each ordinary Transport member's capacity. It is not applied again to the total. |
| Movement and work speed | The lower of the two member values. |
| Defense, Reach and Detection Range | Average of the two member values. |
| Attack base | 60% of the combined individual attack values, before the active weapon's native effects. |
| Combat equipment | The first input member supplies the active sword, shield and armor; wear and replacements alter that member's real items. The second member's gear remains stored. |
| Combat target filters | Initial group filters follow the first member. Group filter changes do not overwrite either member's saved individual filters. |
| Growth | Completed activity alternates between members. Their points and investment remain individual, rather than pooled. |
| Training and Creative tuning | Point upgrades and edits to the unspent balance use the first member. Direct colony stat edits affect both members; health, stamina, and capacity are combined values. |

Each member retains its own body color and held-item appearance. Both combat bodies display the first member's active armor, including its material, dye and glint; the second member's original armor stays stored for unbinding. The representative can follow, wait, work, change dimension, receive logistics support, heal, be recovered and be revived through the common companion systems. Its one shared order concentrates capacity or survivability but cannot run two distant jobs or provide two independent attacks.

Recovering or separating a colony does not restore its members’ health or stamina. Rename the representative independently if desired; original member names are preserved for unbinding. Dyeing or washing the colony updates member one. When unbound, status effects, absorption health, fire, freezing and the shared body’s air supply transfer once to member one, retaining their remaining amount and duration. Temporary bonus health survives recovery and unbinding. To train the other member directly, unbind and either manage it normally or put it first when binding again.

## Unbind safely

Recover the colony, place it **alone in the first station slot**, inspect both records and confirm **Unbind**. The station needs two nearby loaded landing spaces with solid floors, sufficient collision clearance and no fluids. Blocked space, cancelled admission or failed pre-commit storage keeps the colony available.

On success, the original **two companions** return nearby in **Wait**, retaining their remaining health, stamina, equipment, cargo, ownership, individual growth and profession settings. The representative is retired. Copied pre-binding items and old colony items cannot deploy duplicate identities.

If the colony collapses, the common broken-companion system preserves its member records. A slimeball revival restores the representative with **one total health**, shared across the members. It does not restore two full-health companions. Recover and unbind it after revival when safe.

## Persistence and recovery

Binding saves both members and the recoverable colony before clearing the station’s inputs. Unbinding verifies that both original companions can safely return before completing the separation. Saved progress allows interrupted transitions to be recovered and prevents old member or colony items from creating duplicates. Neither operation loads distant chunks.

The [development guide](DEVELOPMENT.md#assembly-and-colony-persistence) describes the saved records and transaction checks.

Use **Recover** at any Colony Bonding Station for pending delivery or a recoverable interrupted transition. Keep inventory space for the result. A full inventory does not erase the record. JEI lists the two binding and two unbinding operations; Patchouli provides the process in both languages. Neither optional mod is required to use the station.
