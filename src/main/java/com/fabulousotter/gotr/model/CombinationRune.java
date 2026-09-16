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
import lombok.AllArgsConstructor;
import lombok.Getter;
import net.runelite.api.gameval.ItemID;

@AllArgsConstructor
@Getter
public enum CombinationRune
{
	MIST("Mist", 6, Altar.AIR, Altar.WATER, ItemID.MISTRUNE),
	DUST("Dust", 10, Altar.AIR, Altar.EARTH, ItemID.DUSTRUNE),
	MUD("Mud", 13, Altar.WATER, Altar.EARTH, ItemID.MUDRUNE),
	SMOKE("Smoke", 15, Altar.AIR, Altar.FIRE, ItemID.SMOKERUNE),
	STEAM("Steam", 19, Altar.WATER, Altar.FIRE, ItemID.STEAMRUNE),
	LAVA("Lava", 23, Altar.EARTH, Altar.FIRE, ItemID.LAVARUNE);

	private final String label;
	private final int levelRequired;
	private final Altar first;
	private final Altar second;
	private final int runeItemId;

	@Nullable
	public static CombinationRune of(Altar altar, Altar carriedRune)
	{
		for (CombinationRune rune : values())
		{
			if ((rune.first == altar && rune.second == carriedRune) || (rune.second == altar && rune.first == carriedRune))
			{
				return rune;
			}
		}
		return null;
	}

	public static boolean isCombinationRune(int itemId)
	{
		for (CombinationRune rune : values())
		{
			if (rune.runeItemId == itemId)
			{
				return true;
			}
		}
		return false;
	}
}
