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

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import javax.annotation.Nullable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.ObjectID;

@AllArgsConstructor
@Getter
public enum CellTier
{
	WEAK(1, "Weak", ItemID.GOTR_CELL_TIER1, ObjectID.GOTR_CELL_TILE_TIER1, NpcID.GOTR_BARRIER_NPC_TIER1, NpcID.GOTR_BARRIER_NPC_TIER1_LARGE, 0, 2, 6),
	MEDIUM(2, "Medium", ItemID.GOTR_CELL_TIER2, ObjectID.GOTR_CELL_TILE_TIER2, NpcID.GOTR_BARRIER_NPC_TIER2, NpcID.GOTR_BARRIER_NPC_TIER2_LARGE, 7, 5, 15),
	STRONG(3, "Strong", ItemID.GOTR_CELL_TIER3, ObjectID.GOTR_CELL_TILE_TIER3, NpcID.GOTR_BARRIER_NPC_TIER3, NpcID.GOTR_BARRIER_NPC_TIER3_LARGE, 13, 9, 27),
	OVERCHARGED(4, "Overcharged", ItemID.GOTR_CELL_TIER4, ObjectID.GOTR_CELL_TILE_TIER4, NpcID.GOTR_BARRIER_NPC_TIER4, NpcID.GOTR_BARRIER_NPC_TIER4_LARGE, 22, 15, 45);

	private static final Map<Integer, CellTier> BY_CELL_ITEM;
	private static final Map<Integer, CellTier> BY_TILE_OBJECT;
	private static final Map<Integer, CellTier> BY_BARRIER_NPC;

	static
	{
		ImmutableMap.Builder<Integer, CellTier> cells = new ImmutableMap.Builder<>();
		ImmutableMap.Builder<Integer, CellTier> tiles = new ImmutableMap.Builder<>();
		ImmutableMap.Builder<Integer, CellTier> barriers = new ImmutableMap.Builder<>();
		for (CellTier tier : values())
		{
			cells.put(tier.cellItemId, tier);
			tiles.put(tier.tileObjectId, tier);
			barriers.put(tier.barrierNpcId, tier);
			barriers.put(tier.barrierNpcLargeId, tier);
		}
		BY_CELL_ITEM = cells.build();
		BY_TILE_OBJECT = tiles.build();
		BY_BARRIER_NPC = barriers.build();
	}

	private final int rank;
	private final String label;
	private final int cellItemId;
	private final int tileObjectId;
	private final int barrierNpcId;
	private final int barrierNpcLargeId;
	private final int strengthenEnergy;
	private final int rechargeEnergy;
	private final int guardianEnergy;

	public boolean isAtLeast(CellTier other)
	{
		return rank >= other.rank;
	}

	@Nullable
	public static CellTier fromCellItem(int itemId)
	{
		return BY_CELL_ITEM.get(itemId);
	}

	@Nullable
	public static CellTier fromTileObject(int objectId)
	{
		return BY_TILE_OBJECT.get(objectId);
	}

	@Nullable
	public static CellTier fromBarrierNpc(int npcId)
	{
		return BY_BARRIER_NPC.get(npcId);
	}
}
