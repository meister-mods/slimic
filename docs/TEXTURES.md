# Texture editing

[한국어](TEXTURES.kr.md) · [Development guide](DEVELOPMENT.md#visual-asset-conventions)

Companion accessories use dedicated PNG atlases under [`assets/slimic/textures/entity/accessories`](../src/main/resources/assets/slimic/textures/entity/accessories). A resource pack can replace each file at the same `assets/slimic/` path. PNG files control the surface artwork; the client models define the shape, placement, animation, and equipment visibility.

## Accessory files

Every accessory atlas is **256×256 pixels**, mapped to a **64×64 logical UV canvas**. One UV unit corresponds to four image pixels. Keep the image square when changing its resolution, preserve the occupied regions, and use nearest-neighbor scaling for pixel art. Transparent space separates the islands; opaque edge padding prevents gaps on thin surfaces.

| File | Visible elements |
| --- | --- |
| `mining.png` | Yellow helmet, brim, and luminous front lamp; shared by Mining and Tunnel Slimes. |
| `transport.png` | Wooden pack and brown straps; shared by Transport and Warehouse Slimes. |
| `farmer.png` | Straw hat, brim, and leather band for Farming Slimes. |
| `lumberjack.png` | Leather cap and brim for Lumberjack Slimes. |
| `healing.png` | Ivory headband and golden cross; shared by Healing and Abundance Slimes. |
| `cleaning.png` | Blue work cap, brim, and metal badge. |
| `combat.png` | Metal headband and red crest; also used by Dragon Slimes. |
| `bridle.png` | Brown bridle and gold buckle for Saddle Slimes. |
| `soggy.png` | Green slime droplets. |
| `fishing.png` | Low blue overalls, straps, and gold buttons. |
| `abundance.png` | Side-held golden lyre, ivory strings, and green laurel. |
| `tunnel.png` | Metal cart body and dark wheels. |
| `dragon.png` | Purple wing membranes and dark horns. |
| `commander.png` | Dark mustache, wooden baton, and gold baton tip. |
| `guardian_halo.png` | Luminous gold halo. |
| `saddle.png` | Leather seat, saddle straps, and metal stirrups. |
| `armor_leather.png` | Neutral leather armor surface, multiplied by the original chestplate's dye color. |
| `armor_chainmail.png` | Chainmail armor surface. |
| `armor_iron.png` | Iron armor surface. |
| `armor_golden.png` | Golden armor surface. |
| `armor_diamond.png` | Diamond armor surface. |
| `armor_netherite.png` | Netherite armor surface. |

All six armor atlases share their UV coordinates and equipped silhouette. The existing `textures/entity/slime_wings.png` and `herald_hat.png` remain the feathered-wing and Herald hat textures. Meow Slimes use Minecraft's native cat head and texture. Held tools retain the original item models.

## UV layout

[`texture-layouts.json`](texture-layouts.json) lists every accessory atlas, its model class, and its cuboid regions. `uv` is the cuboid's top-left logical coordinate; `size` contains width, height, and depth in model units. `cube` numbers the cuboids within a named model part, starting at one. Repeated lyre strings and mirrored dragon wings share their respective UV regions.

For a cuboid with origin `(u, v)` and size `(w, h, d)`, the two horizontal faces occupy `(u+d, v, w, d)` and `(u+d+w, v, w, d)`. The four vertical faces form a row at `v+d`, with widths `d`, `w`, `d`, and `w`, and height `h`. Multiply these coordinates by four to locate the regions in the default PNG. Fractional model dimensions are intentional; retain the opaque padding around their edges.

Surface changes follow a resource reload with **F3+T**. Changing the UV layout, volume, or attachment position also requires updating the corresponding client model. The mining lamp and guardian halo retain their full-bright lighting; leather dye and armor enchantment glint remain dynamic.

## Screen files

Screen backgrounds live under [`assets/slimic/textures/gui`](../src/main/resources/assets/slimic/textures/gui). Their default image dimensions match screen coordinates exactly.

| File | Size | Usage |
| --- | --- | --- |
| `slime.png` | 368×240 | Companion management panels and header. |
| `creative_tuner.png` | 360×230 | Creative tuner panels and header. |
| `commander.png` | 310×235 | Commander roster panels and header. |
| `slots.png` | 36×18 | Active 18×18 slot on the left; locked 18×18 bag cell on the right. |

Slot images include a one-pixel frame around the 16×16 item area. The management screen places them using the menu's slot coordinates, including partial bag pages. Text, controls, selected-row highlights, tooltips, and item contents remain dynamic.

## Native validation

Run against the prepared client smoke world:

```sh
JAVA_TOOL_OPTIONS="-Dslimic.accessoryTexturesOnly=true" ./gradlew runClient -PclientSmoke=true
JAVA_TOOL_OPTIONS="-Dslimic.companionTooltipsOnly=true" ./gradlew runClient -PclientSmoke=true
JAVA_TOOL_OPTIONS="-Dslimic.visualPolishOnly=true" ./gradlew runClient -PclientSmoke=true
```

The client smoke suite accepts `-Dslimic.accessoryTexturesOnly=true`. It checks loaded PNG dimensions and opaque surface coverage using Minecraft's baked UVs, then captures front and rear portraits of all twenty-two professions and eight armor variants, including dyed leather, a dyed body, enchantment glint, equipped tools, and wings. It also checks a temporary 512×512 resource-pack override and restoration of the original 256×256 GPU texture. `-Dslimic.textureReloadCycles=100` repeats that override/restore cycle for a longer reload check. The existing `-Dslimic.inventoryLayoutOnly=true` check verifies synchronized English and Korean management layouts. These options require a prepared world named `slimic-smoke` in the development client’s configured run directory. They modify the test inventory and game mode; use a dedicated fixture.

The visual-polish check covers localized material tabs, minimal decoration tooltips, all world models, the chest atlas and lid motion, native grass fringes, and actual portal animation updates in the GPU atlas.

## World materials

The Slimic uses eight opaque **32×32** material textures and one animated portal strip in [`assets/slimic/textures/block/world`](../src/main/resources/assets/slimic/textures/block/world): stone, bricks, tiles, soil, damp grass, silt, congealed gel, marble, and the portal surface. Pixel-art sources were exported with nearest-neighbor sampling. The portal is a **32×512** strip of sixteen 32×32 frames. Its `.png.mcmeta` advances every three ticks and interpolates frames; keep the strip and metadata together. Damp Grass combines its top texture and local soil with Minecraft’s irregular grass-side overlay.

Block models under `models/block/world` combine these textures with referenced Minecraft surfaces and tint index 0. `SlimicWorldClient` applies each catalog entry's tint to blocks and items. Stairs, slabs, walls, cutout plants, translucent glass, and the portal declare their Forge render type in JSON. Resource packs can replace the surfaces without changing collision shapes or world-generation rules.

## Slime Chest

`textures/block/slime_chest.png` follows the native single-chest **64×64 UV atlas**. Body, lid, and latch share it in both the world and item models. Keep the native transparent interior and unused regions; `slime_chest_particle.png` provides the breaking-particle surface.
