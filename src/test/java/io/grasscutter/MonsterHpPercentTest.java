package io.grasscutter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import emu.grasscutter.game.entity.EntityMonster;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The stock Lua for {@code EVENT_SPECIFIC_MONSTER_HP_CHANGE} compares {@code evt.param3} against
 * whole-number percent thresholds -- {@code evt.param3 > 20} next to the comment "血量小于%20时触发"
 * -- and the Stormterror domain (group 220020001) gates quest 35722 击退风魔龙 on exactly that check.
 *
 * <p>For a long time the server passed raw current HP instead, so the value was always orders of
 * magnitude above any threshold, the trigger never fired, and the dragon quest dead-ended every
 * player who entered the domain. This pins the conversion so that regression is not silent.
 */
public final class MonsterHpPercentTest {

    private static int pct(float cur, float max) {
        return EntityMonster.toHpPercent(cur, max);
    }

    @Test
    @DisplayName("full health is 100 percent")
    void fullHealth() {
        assertEquals(100, pct(100_000f, 100_000f));
    }

    @Test
    @DisplayName("the dragon's trigger threshold is actually reachable")
    void belowTwentyPercent() {
        // Stormterror has tens of thousands of HP; 19% of it must land under the Lua `> 20` guard.
        assertEquals(19, pct(19_000f, 100_000f));
        assertEquals(20, pct(20_400f, 100_000f));
    }

    @Test
    @DisplayName("a dead monster reports zero, which every stock threshold counts as below")
    void dead() {
        assertEquals(0, pct(0f, 100_000f));
        assertEquals(0, pct(-5_000f, 100_000f));
    }

    @Test
    @DisplayName("overheal and an unset max hp clamp instead of throwing or going negative")
    void degenerate() {
        assertEquals(100, pct(150_000f, 100_000f));
        assertEquals(0, pct(1f, 0f));
        assertEquals(0, pct(0f, Float.NaN));
    }
}
