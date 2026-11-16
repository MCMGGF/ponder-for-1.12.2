"""Generate vanilla Minecraft 1.12.2 schematics for Ponder UI tests.

Requires the development-only ``nbtlib`` Python package.
"""

from pathlib import Path

import nbtlib


ROOT = Path(__file__).resolve().parents[1]
PONDER_ROOT = ROOT / "src/main/resources/assets/ponder_legacy/ponder"


def state(name, **properties):
    return name, tuple(sorted((key, str(value)) for key, value in properties.items()))


CONCRETE_WHITE = state("minecraft:concrete", color="white")
CONCRETE_GRAY = state("minecraft:concrete", color="gray")
FORBIDDEN_FLATTENED_IDS = {
    "minecraft:white_concrete",
    "minecraft:red_stained_glass",
    "minecraft:blue_stained_glass",
    "minecraft:lime_stained_glass",
    "minecraft:white_stained_glass",
    "minecraft:stone_bricks",
    "minecraft:oak_log",
    "minecraft:oak_planks",
    "minecraft:polished_andesite",
}


class Schematic:
    def __init__(self, width, height, depth):
        self.size = (width, height, depth)
        self._blocks = {}

    def add(self, x, y, z, block_state):
        self._blocks[(x, y, z)] = block_state

    def fill(self, x1, y1, z1, x2, y2, z2, block_state):
        for x in range(min(x1, x2), max(x1, x2) + 1):
            for y in range(min(y1, y2), max(y1, y2) + 1):
                for z in range(min(z1, z2), max(z1, z2) + 1):
                    self.add(x, y, z, block_state)

    def base(self):
        width, _, depth = self.size
        for x in range(width):
            for z in range(depth):
                self.add(x, 0, z, CONCRETE_WHITE if (x + z) % 2 == 0 else CONCRETE_GRAY)

    def save(self, path):
        palette = []
        palette_indexes = {}
        block_tags = []

        for position, block_state in sorted(self._blocks.items(), key=lambda entry: (entry[0][1], entry[0][2], entry[0][0])):
            if block_state not in palette_indexes:
                palette_indexes[block_state] = len(palette)
                name, properties = block_state
                palette_entry = nbtlib.Compound({"Name": nbtlib.String(name)})
                if properties:
                    palette_entry["Properties"] = nbtlib.Compound(
                        {key: nbtlib.String(value) for key, value in properties}
                    )
                palette.append(palette_entry)

            block_tags.append(
                nbtlib.Compound(
                    {
                        "pos": nbtlib.List[nbtlib.Int](map(nbtlib.Int, position)),
                        "state": nbtlib.Int(palette_indexes[block_state]),
                    }
                )
            )

        data = nbtlib.File(
            {
                "size": nbtlib.List[nbtlib.Int](map(nbtlib.Int, self.size)),
                "entities": nbtlib.List[nbtlib.Compound](),
                "blocks": nbtlib.List[nbtlib.Compound](block_tags),
                "palette": nbtlib.List[nbtlib.Compound](palette),
                "DataVersion": nbtlib.Int(1343),
            }
        )
        path.parent.mkdir(parents=True, exist_ok=True)
        data.save(path, gzipped=True)


def normalize_legacy_state(entry):
    name = str(entry.get("Name", ""))
    properties = {key: str(value) for key, value in entry.get("Properties", {}).items()}

    flattened_colors = {
        "minecraft:white_concrete": state("minecraft:concrete", color="white"),
        "minecraft:red_stained_glass": state("minecraft:stained_glass", color="red"),
        "minecraft:blue_stained_glass": state("minecraft:stained_glass", color="blue"),
        "minecraft:lime_stained_glass": state("minecraft:stained_glass", color="lime"),
        "minecraft:white_stained_glass": state("minecraft:stained_glass", color="white"),
        "minecraft:stone_bricks": state("minecraft:stonebrick", variant="stonebrick"),
        "minecraft:oak_planks": state("minecraft:planks", variant="oak"),
        "minecraft:polished_andesite": state("minecraft:stone", variant="smooth_andesite"),
    }
    if name in flattened_colors:
        return flattened_colors[name]
    if name == "minecraft:oak_log":
        axis = properties.get("axis", "y")
        return state("minecraft:log", variant="oak", axis=axis if axis in {"x", "y", "z", "none"} else "y")

    clean_states = {
        "minecraft:stone": state("minecraft:stone", variant="stone"),
        "minecraft:hay_block": state("minecraft:hay_block", axis="y"),
        "minecraft:water": state("minecraft:water", level=properties.get("level", "0")),
        "minecraft:observer": state("minecraft:observer", facing="south", powered="false"),
        "minecraft:hopper": state("minecraft:hopper", facing="down", enabled="true"),
        "minecraft:chest": state("minecraft:chest", facing="north"),
        "minecraft:slime": state("minecraft:slime"),
        "minecraft:glass": state("minecraft:glass"),
        "minecraft:snow_block": state("minecraft:snow"),
        "minecraft:iron_block": state("minecraft:iron_block"),
        "minecraft:gold_block": state("minecraft:gold_block"),
        "minecraft:barrier": state("minecraft:barrier"),
    }
    return clean_states.get(name)


def normalize_existing_schematics():
    for folder_name in ("debug", "ui_test"):
        folder = PONDER_ROOT / folder_name
        if not folder.exists():
            continue
        for path in folder.glob("*.nbt"):
            data = nbtlib.load(path)
            changed = False
            for entry in data.get("palette", []):
                normalized = normalize_legacy_state(entry)
                if normalized is None:
                    continue
                name, properties = normalized
                replacement = nbtlib.Compound({"Name": nbtlib.String(name)})
                if properties:
                    replacement["Properties"] = nbtlib.Compound(
                        {key: nbtlib.String(value) for key, value in properties}
                    )
                entry.clear()
                entry.update(replacement)
                changed = True
            if changed:
                data["DataVersion"] = nbtlib.Int(1343)
                data.save(path, gzipped=True)


def validate_schematic(path):
    data = nbtlib.load(path)
    size = tuple(map(int, data["size"]))
    positions = []
    for block in data["blocks"]:
        position = tuple(map(int, block["pos"]))
        positions.append(position)
        if not all(0 <= position[index] < size[index] for index in range(3)):
            raise ValueError(f"{path}: block outside schematic bounds: {position}")
        if not 0 <= int(block["state"]) < len(data["palette"]):
            raise ValueError(f"{path}: palette index out of range at {position}")
    if len(positions) != len(set(positions)):
        raise ValueError(f"{path}: duplicate block positions")
    for entry in data["palette"]:
        block_id = str(entry.get("Name", ""))
        if block_id in FORBIDDEN_FLATTENED_IDS:
            raise ValueError(f"{path}: non-1.12.2 block id: {block_id}")


def factory_line():
    result = Schematic(9, 6, 9)
    result.base()
    result.add(1, 1, 4, state("minecraft:chest", facing="east"))
    result.add(2, 1, 4, state("minecraft:hopper", facing="east", enabled="true"))
    result.add(1, 1, 6, state("minecraft:lever", facing="up_x", powered="false"))
    for x in range(3, 7):
        result.add(x, 1, 4, state("minecraft:rail", shape="east_west"))
    result.add(7, 1, 4, state("minecraft:chest", facing="west"))
    for x in (3, 5):
        result.add(x, 1, 2, state("minecraft:furnace", facing="south"))
        result.add(x, 2, 2, state("minecraft:hopper", facing="down", enabled="true"))
        result.add(x, 3, 2, state("minecraft:chest", facing="south"))
        result.add(x, 4, 2, state("minecraft:redstone_lamp"))
        result.add(x - 1, 1, 2, state("minecraft:iron_block"))
    for x in range(2, 7):
        result.add(x, 2, 3, state("minecraft:stained_glass", color="cyan"))
    result.fill(0, 1, 0, 0, 2, 0, state("minecraft:iron_block"))
    result.fill(8, 1, 8, 8, 2, 8, state("minecraft:iron_block"))
    return result


def redstone_matrix():
    result = Schematic(9, 5, 9)
    result.base()
    colors = ("red", "lime", "blue")
    for z, color in zip((2, 4, 6), colors):
        result.add(1, 1, z, state("minecraft:lever", facing="up_x", powered="false"))
        result.add(2, 1, z, state("minecraft:redstone_wire", power="0"))
        result.add(3, 1, z, state("minecraft:redstone_wire", power="0"))
        result.add(4, 1, z, state("minecraft:unpowered_repeater", facing="east", delay="1", locked="false"))
        result.add(5, 1, z, state("minecraft:redstone_wire", power="0"))
        result.add(6, 1, z, state("minecraft:redstone_wire", power="0"))
        result.add(7, 1, z, state("minecraft:redstone_lamp"))
        result.add(7, 2, z, state("minecraft:stained_glass", color=color))
    result.fill(3, 1, 8, 5, 3, 8, state("minecraft:iron_bars"))
    result.add(4, 4, 8, state("minecraft:redstone_block"))
    return result


def assembly_core():
    result = Schematic(9, 7, 9)
    result.base()
    result.add(4, 1, 4, state("minecraft:beacon"))
    result.add(4, 2, 4, state("minecraft:diamond_block"))
    for x, z in ((3, 3), (5, 3), (3, 5), (5, 5)):
        result.fill(x, 1, z, x, 4, z, state("minecraft:quartz_block", variant="default"))
    for x in range(3, 6):
        for z in range(3, 6):
            if (x, z) != (4, 4):
                result.add(x, 3, z, state("minecraft:stained_glass", color="light_blue"))
    for x in (1, 2, 6, 7):
        result.add(x, 2, 4, state("minecraft:iron_block"))
        result.add(x, 3, 4, state("minecraft:stained_glass", color="orange"))
    for z in (1, 2, 6, 7):
        result.add(4, 2, z, state("minecraft:iron_block"))
        result.add(4, 3, z, state("minecraft:stained_glass", color="lime"))
    result.add(1, 1, 4, state("minecraft:piston", facing="east", extended="false"))
    result.add(7, 1, 4, state("minecraft:piston", facing="west", extended="false"))
    result.add(4, 1, 1, state("minecraft:piston", facing="south", extended="false"))
    result.add(4, 1, 7, state("minecraft:piston", facing="north", extended="false"))
    return result


def logistics_hub():
    result = Schematic(9, 6, 9)
    result.base()
    result.add(4, 1, 4, state("minecraft:chest", facing="north"))
    result.add(4, 2, 4, state("minecraft:hopper", facing="down", enabled="true"))
    result.add(4, 3, 4, state("minecraft:dropper", facing="down", triggered="false"))
    outputs = {
        (1, 4): "red",
        (7, 4): "blue",
        (4, 1): "yellow",
        (4, 7): "lime",
    }
    for (x, z), color in outputs.items():
        result.add(x, 1, z, state("minecraft:chest", facing="north"))
        result.add(x, 2, z, state("minecraft:stained_glass", color=color))
    for x in range(2, 7):
        if x != 4:
            result.add(x, 1, 4, state("minecraft:wool", color="red" if x < 4 else "blue"))
            result.add(x, 2, 4, state("minecraft:glass_pane"))
    for z in range(2, 7):
        if z != 4:
            result.add(4, 1, z, state("minecraft:wool", color="yellow" if z < 4 else "lime"))
            result.add(4, 2, z, state("minecraft:glass_pane"))
    for x, z in ((3, 4), (5, 4), (4, 3), (4, 5)):
        result.add(x, 3, z, state("minecraft:hopper", facing="down", enabled="true"))
    return result


def gui_lab():
    result = Schematic(9, 5, 9)
    result.base()
    result.add(2, 1, 2, state("minecraft:furnace", facing="south"))
    result.add(6, 1, 2, state("minecraft:chest", facing="south"))
    result.add(2, 1, 6, state("minecraft:hopper", facing="down", enabled="true"))
    result.add(6, 1, 6, state("minecraft:brewing_stand", has_bottle_0="false", has_bottle_1="false", has_bottle_2="false"))
    result.add(4, 1, 4, state("minecraft:crafting_table"))
    for x, z, color in ((2, 2, "orange"), (6, 2, "brown"), (2, 6, "gray"), (6, 6, "purple")):
        result.add(x, 2, z, state("minecraft:stained_glass", color=color))
        result.add(x, 3, z, state("minecraft:redstone_lamp"))
    result.fill(0, 1, 4, 0, 3, 4, state("minecraft:quartz_block", variant="chiseled"))
    result.fill(8, 1, 4, 8, 3, 4, state("minecraft:quartz_block", variant="chiseled"))
    return result


def main():
    normalize_existing_schematics()
    for path in sorted((PONDER_ROOT / "ui_test").glob("*.nbt")):
        validate_schematic(path)
    output = PONDER_ROOT / "complex"
    schematics = {
        "factory_line.nbt": factory_line(),
        "redstone_matrix.nbt": redstone_matrix(),
        "assembly_core.nbt": assembly_core(),
        "logistics_hub.nbt": logistics_hub(),
        "gui_lab.nbt": gui_lab(),
    }
    for name, schematic in schematics.items():
        path = output / name
        schematic.save(path)
        validate_schematic(path)
        print(path)


if __name__ == "__main__":
    main()
