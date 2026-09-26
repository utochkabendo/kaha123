package dev.csarsenal.ballistics;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.Tags;

/** Surface material of a block: wall penetration modifier (CS surfaceprops) and impact effect. */
public enum Material {
    CONCRETE(0.4f, "concrete"),
    DIRT(0.6f, "dirt"),
    WOOD(1.0f, "wood"),
    METAL(0.25f, "metal"),
    GLASS(1.0f, "glass"),
    SOFT(1.5f, "dirt"),       // leaves, wool, plants, snow
    IMPENETRABLE(0f, "concrete");

    /** CS penetrationmodifier: higher = easier to shoot through */
    public final float penetration;
    public final String impactSound;

    Material(float pen, String snd) {
        this.penetration = pen;
        this.impactSound = snd;
    }

    public static Material of(BlockGetter level, BlockPos pos, BlockState state) {
        if (state.getBlock().getExplosionResistance() >= 600f) return IMPENETRABLE;
        if (state.is(Tags.Blocks.GLASS_BLOCKS) || state.is(Tags.Blocks.GLASS_PANES) || state.is(BlockTags.IMPERMEABLE)) return GLASS;
        if (state.is(BlockTags.LEAVES) || state.is(BlockTags.WOOL) || state.is(BlockTags.WOOL_CARPETS)) return SOFT;
        if (state.is(BlockTags.LOGS) || state.is(BlockTags.PLANKS) || state.is(BlockTags.WOODEN_DOORS) || state.is(BlockTags.WOODEN_SLABS)
                || state.is(BlockTags.WOODEN_STAIRS) || state.is(BlockTags.WOODEN_FENCES) || state.is(BlockTags.WOODEN_TRAPDOORS)) return WOOD;
        SoundType st = level instanceof net.minecraft.world.level.LevelReader lr ? state.getSoundType(lr, pos, null) : state.getSoundType();
        if (st == SoundType.WOOD || st == SoundType.BAMBOO_WOOD || st == SoundType.CHERRY_WOOD || st == SoundType.NETHER_WOOD
                || st == SoundType.BAMBOO || st == SoundType.SCAFFOLDING || st == SoundType.LADDER) return WOOD;
        if (st == SoundType.METAL || st == SoundType.ANVIL || st == SoundType.CHAIN || st == SoundType.COPPER || st == SoundType.NETHERITE_BLOCK
                || st == SoundType.LANTERN || st == SoundType.HEAVY_CORE || st == SoundType.COPPER_GRATE) return METAL;
        if (st == SoundType.GLASS) return GLASS;
        if (st == SoundType.GRAVEL || st == SoundType.SAND || st == SoundType.ROOTED_DIRT || st == SoundType.MUD || st == SoundType.GRASS
                || st == SoundType.SOUL_SAND || st == SoundType.SOUL_SOIL || st == SoundType.MOSS || st == SoundType.PACKED_MUD) return DIRT;
        if (st == SoundType.WOOL || st == SoundType.SNOW || st == SoundType.POWDER_SNOW || st == SoundType.AZALEA_LEAVES
                || st == SoundType.CHERRY_LEAVES || st == SoundType.CROP || st == SoundType.SWEET_BERRY_BUSH || st == SoundType.SLIME_BLOCK
                || st == SoundType.HONEY_BLOCK || st == SoundType.MOSS_CARPET) return SOFT;
        return CONCRETE;
    }
}
