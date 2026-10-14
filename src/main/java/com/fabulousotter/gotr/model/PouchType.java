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
package com.fabulousotter.gotr.model;

import javax.annotation.Nullable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;

/**
 * A degraded pouch keeps losing capacity in steps as it is filled, until it crumbles. Each
 * essence filled raises a hidden counter, and the step is set by the counter crossing the
 * thresholds below (from the wiki's degradation table).
 */
@AllArgsConstructor
@Getter
public enum PouchType
{
	SMALL("Small pouch", VarbitID.SMALL_ESSENCE_POUCH, ItemID.RCU_POUCH_SMALL, -1, 1, -1, -1,
		new int[]{3}, new int[0]),
	MEDIUM("Medium pouch", VarbitID.MEDIUM_ESSENCE_POUCH, ItemID.RCU_POUCH_MEDIUM, ItemID.RCU_POUCH_MEDIUM_DEGRADE, 25,
		VarPlayerID.RCU_POUCH_DEGRADATION_MED, -1,
		new int[]{6, 3}, new int[]{400}),
	LARGE("Large pouch", VarbitID.LARGE_ESSENCE_POUCH, ItemID.RCU_POUCH_LARGE, ItemID.RCU_POUCH_LARGE_DEGRADE, 50,
		VarPlayerID.RCU_POUCH_DEGRADATION_LARGE, -1,
		new int[]{9, 7, 5, 3}, new int[]{400, 600, 800}),
	GIANT("Giant pouch", VarbitID.GIANT_ESSENCE_POUCH, ItemID.RCU_POUCH_GIANT, ItemID.RCU_POUCH_GIANT_DEGRADE, 75,
		VarPlayerID.RCU_POUCH_DEGRADATION_GIANT, -1,
		new int[]{12, 9, 8, 7, 6, 5, 3}, new int[]{200, 300, 400, 600, 800, 1000}),
	// Capacities at 85 Runecraft; lower levels scale them down (see capacity).
	COLOSSAL("Colossal pouch", VarbitID.COLOSSAL_ESSENCE_POUCH, ItemID.RCU_POUCH_COLOSSAL, ItemID.RCU_POUCH_COLOSSAL_DEGRADE, 25,
		-1, VarbitID.RCU_POUCH_DEGRADATION_COLOSSAL,
		new int[]{40, 35, 30, 25, 20, 15, 10, 5}, new int[]{320, 565, 745, 870, 950, 995, 1015});

	private final String label;
	private final int varbitId;
	private final int itemId;
	private final int degradedItemId;
	private final int levelRequired;
	// The degradation counter: a varp for the original pouches, a varbit for the colossal.
	private final int degradationVarp;
	private final int degradationVarbit;
	@Getter(AccessLevel.NONE)
	private final int[] stageCapacities;
	@Getter(AccessLevel.NONE)
	private final int[] stageThresholds;

	/** The degradation step for a counter value, 0 being intact. */
	public int stage(int counter)
	{
		int stage = 0;
		while (stage < stageThresholds.length && counter >= stageThresholds[stage])
		{
			stage++;
		}
		return stage;
	}

	/**
	 * Essence the pouch holds at a degradation step. The colossal pouch's capacity grows with
	 * Runecraft level and its steps scale down with it; below 85 the game's exact steps are not
	 * known, so they are rounded down, as holding less than assumed only skips a fill.
	 */
	public int capacity(int runecraftLevel, int stage)
	{
		int at = stageCapacities[Math.max(0, Math.min(stage, stageCapacities.length - 1))];
		if (this == COLOSSAL)
		{
			int full;
			if (runecraftLevel >= 85)
			{
				full = 40;
			}
			else if (runecraftLevel >= 75)
			{
				full = 27;
			}
			else if (runecraftLevel >= 50)
			{
				full = 16;
			}
			else
			{
				full = 8;
			}
			return Math.max(1, at * full / stageCapacities[0]);
		}
		if (runecraftLevel < levelRequired)
		{
			return 0;
		}
		return at;
	}

	@Nullable
	public static PouchType fromItem(int itemId)
	{
		for (PouchType pouch : values())
		{
			if (pouch.itemId == itemId || pouch.degradedItemId == itemId)
			{
				return pouch;
			}
		}
		return null;
	}

	public static boolean isDegradedItem(int itemId)
	{
		for (PouchType pouch : values())
		{
			if (pouch.degradedItemId == itemId)
			{
				return true;
			}
		}
		return false;
	}
}
