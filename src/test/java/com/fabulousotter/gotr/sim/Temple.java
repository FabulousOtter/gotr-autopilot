/*
 * Copyright (c) 2026, FabulousOtter
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.fabulousotter.gotr.sim;

import com.google.common.collect.ImmutableList;
import com.fabulousotter.gotr.model.Altar;
import com.fabulousotter.gotr.plan.Target;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.coords.WorldPoint;

/**
 * The temple floor plan the simulator walks over: landmark tiles placed from the wiki map of
 * the region (3584..3647 x 9472..9535), with the east rubble treated the way the plugin's
 * pathfinder treats it (a three-tick transport between its two ends).
 *
 * The positions are approximate to a few tiles. What matters for the planner is the ratio
 * between legs (large remains behind the rubble versus parts beside the workbench, portal
 * guardian versus Great Guardian), not the exact coordinates.
 */
final class Temple
{
	static final WorldPoint GREAT_GUARDIAN = new WorldPoint(3615, 9503, 0);
	static final WorldPoint WORKBENCH = new WorldPoint(3609, 9496, 0);
	static final WorldPoint DEPOSIT_POOL = new WorldPoint(3612, 9492, 0);
	static final WorldPoint CELL_TABLE = new WorldPoint(3618, 9492, 0);
	static final WorldPoint WEAK_CELL_TABLE = new WorldPoint(3620, 9492, 0);
	static final WorldPoint PILE_ELEMENTAL = new WorldPoint(3624, 9503, 0);
	static final WorldPoint PILE_CATALYTIC = new WorldPoint(3606, 9503, 0);
	static final WorldPoint RUBBLE_WEST = new WorldPoint(3633, 9505, 0);
	static final WorldPoint RUBBLE_EAST = new WorldPoint(3637, 9505, 0);
	static final WorldPoint LARGE_REMAINS = new WorldPoint(3641, 9500, 0);
	static final List<WorldPoint> PARTS = ImmutableList.of(new WorldPoint(3604, 9490, 0), new WorldPoint(3626, 9490, 0));
	static final List<WorldPoint> PORTAL_SPAWNS = ImmutableList.of(
		new WorldPoint(3615, 9524, 0), new WorldPoint(3632, 9516, 0), new WorldPoint(3598, 9516, 0), new WorldPoint(3600, 9482, 0));
	static final List<WorldPoint> CELL_TILES = ImmutableList.of(
		new WorldPoint(3607, 9511, 0), new WorldPoint(3615, 9513, 0), new WorldPoint(3623, 9511, 0),
		new WorldPoint(3628, 9503, 0), new WorldPoint(3602, 9503, 0), new WorldPoint(3608, 9495, 0),
		new WorldPoint(3622, 9495, 0), new WorldPoint(3615, 9489, 0));
	static final Map<Altar, WorldPoint> GUARDIANS = new EnumMap<>(Altar.class);
	static final int RUBBLE_COST = 3;

	static
	{
		GUARDIANS.put(Altar.AIR, new WorldPoint(3600, 9510, 0));
		GUARDIANS.put(Altar.MIND, new WorldPoint(3603, 9515, 0));
		GUARDIANS.put(Altar.WATER, new WorldPoint(3607, 9519, 0));
		GUARDIANS.put(Altar.EARTH, new WorldPoint(3612, 9521, 0));
		GUARDIANS.put(Altar.FIRE, new WorldPoint(3618, 9521, 0));
		GUARDIANS.put(Altar.BODY, new WorldPoint(3623, 9519, 0));
		GUARDIANS.put(Altar.COSMIC, new WorldPoint(3627, 9515, 0));
		GUARDIANS.put(Altar.CHAOS, new WorldPoint(3630, 9510, 0));
		GUARDIANS.put(Altar.NATURE, new WorldPoint(3631, 9500, 0));
		GUARDIANS.put(Altar.LAW, new WorldPoint(3599, 9500, 0));
		GUARDIANS.put(Altar.DEATH, new WorldPoint(3628, 9494, 0));
		GUARDIANS.put(Altar.BLOOD, new WorldPoint(3602, 9494, 0));
	}

	private Temple()
	{
	}

	static boolean beyondRubble(WorldPoint p)
	{
		return p.getX() >= RUBBLE_EAST.getX();
	}

	private static int cheb(WorldPoint a, WorldPoint b)
	{
		return Math.max(Math.abs(a.getX() - b.getX()), Math.abs(a.getY() - b.getY()));
	}

	/** Walking ticks between two tiles, one tile per tick, crossing the rubble where needed. */
	static int walkTicks(WorldPoint a, WorldPoint b)
	{
		if (beyondRubble(a) == beyondRubble(b))
		{
			return cheb(a, b);
		}
		WorldPoint near = beyondRubble(a) ? RUBBLE_EAST : RUBBLE_WEST;
		WorldPoint far = beyondRubble(a) ? RUBBLE_WEST : RUBBLE_EAST;
		return cheb(a, near) + RUBBLE_COST + cheb(far, b);
	}

	static WorldPoint nearest(List<WorldPoint> points, WorldPoint from)
	{
		WorldPoint best = null;
		for (WorldPoint p : points)
		{
			if (best == null || walkTicks(from, p) < walkTicks(from, best))
			{
				best = p;
			}
		}
		return best;
	}

	static WorldPoint landmark(Target t, WorldPoint from, WorldPoint portal)
	{
		switch (t)
		{
			case LARGE_REMAINS:
				return LARGE_REMAINS;
			case GUARDIAN_REMAINS:
				return nearest(PARTS, from);
			case GUARDIAN_REMAINS_ENTRANCE:
				return PARTS.get(0);
			case WORKBENCH:
				return WORKBENCH;
			case DEPOSIT_POOL:
				return DEPOSIT_POOL;
			case UNCHARGED_CELL_TABLE:
				return CELL_TABLE;
			case WEAK_CELL_TABLE:
				return WEAK_CELL_TABLE;
			case GREAT_GUARDIAN:
				return GREAT_GUARDIAN;
			case ESSENCE_PILE_ELEMENTAL:
				return PILE_ELEMENTAL;
			case ESSENCE_PILE_CATALYTIC:
				return PILE_CATALYTIC;
			case PORTAL:
				return portal;
			default:
				return null;
		}
	}
}
