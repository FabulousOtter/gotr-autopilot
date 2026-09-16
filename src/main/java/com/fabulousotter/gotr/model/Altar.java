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
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.Quest;

/**
 * HUD indices are 1-based within each alignment; 0 means no active altar.
 */
@AllArgsConstructor
@Getter
public enum Altar
{
	AIR("Air", Alignment.ELEMENTAL, 1, 1, CellTier.WEAK, ObjectID.GOTR_PORTAL_AIR, ObjectID.AIR_ALTAR, ObjectID.AIRTEMPLE_EXIT_PORTAL, ItemID.GOTR_PORTAL_TALISMAN_AIR, ItemID.AIRRUNE, null, 4),
	WATER("Water", Alignment.ELEMENTAL, 2, 5, CellTier.MEDIUM, ObjectID.GOTR_PORTAL_WATER, ObjectID.WATER_ALTAR, ObjectID.WATERTEMPLE_EXIT_PORTAL, ItemID.GOTR_PORTAL_TALISMAN_WATER, ItemID.WATERRUNE, null, 9),
	EARTH("Earth", Alignment.ELEMENTAL, 3, 9, CellTier.STRONG, ObjectID.GOTR_PORTAL_EARTH, ObjectID.EARTH_ALTAR, ObjectID.EARTHTEMPLE_EXIT_PORTAL, ItemID.GOTR_PORTAL_TALISMAN_EARTH, ItemID.EARTHRUNE, null, 11),
	FIRE("Fire", Alignment.ELEMENTAL, 4, 14, CellTier.OVERCHARGED, ObjectID.GOTR_PORTAL_FIRE, ObjectID.FIRE_ALTAR, ObjectID.FIRETEMPLE_EXIT_PORTAL, ItemID.GOTR_PORTAL_TALISMAN_FIRE, ItemID.FIRERUNE, null, 11),
	MIND("Mind", Alignment.CATALYTIC, 1, 2, CellTier.WEAK, ObjectID.GOTR_PORTAL_MIND, ObjectID.MIND_ALTAR, ObjectID.MINDTEMPLE_EXIT_PORTAL, ItemID.GOTR_PORTAL_TALISMAN_MIND, ItemID.MINDRUNE, null, 12),
	BODY("Body", Alignment.CATALYTIC, 2, 20, CellTier.WEAK, ObjectID.GOTR_PORTAL_BODY, ObjectID.BODY_ALTAR, ObjectID.BODYTEMPLE_EXIT_PORTAL, ItemID.GOTR_PORTAL_TALISMAN_BODY, ItemID.BODYRUNE, null, 5),
	COSMIC("Cosmic", Alignment.CATALYTIC, 3, 27, CellTier.MEDIUM, ObjectID.GOTR_PORTAL_COSMIC, ObjectID.COSMIC_ALTAR, ObjectID.COSMICTEMPLE_EXIT_PORTAL, ItemID.GOTR_PORTAL_TALISMAN_COSMIC, ItemID.COSMICRUNE, Quest.LOST_CITY, 19),
	CHAOS("Chaos", Alignment.CATALYTIC, 4, 35, CellTier.MEDIUM, ObjectID.GOTR_PORTAL_CHAOS, ObjectID.CHAOS_ALTAR, ObjectID.CHAOSTEMPLE_EXIT_PORTAL, ItemID.GOTR_PORTAL_TALISMAN_CHAOS, ItemID.CHAOSRUNE, null, 10),
	NATURE("Nature", Alignment.CATALYTIC, 5, 44, CellTier.STRONG, ObjectID.GOTR_PORTAL_NATURE, ObjectID.NATURE_ALTAR, ObjectID.NATURETEMPLE_EXIT_PORTAL, ItemID.GOTR_PORTAL_TALISMAN_NATURE, ItemID.NATURERUNE, null, 5),
	LAW("Law", Alignment.CATALYTIC, 6, 54, CellTier.STRONG, ObjectID.GOTR_PORTAL_LAW, ObjectID.LAW_ALTAR, ObjectID.LAWTEMPLE_EXIT_PORTAL, ItemID.GOTR_PORTAL_TALISMAN_LAW, ItemID.LAWRUNE, Quest.TROLL_STRONGHOLD, 13),
	DEATH("Death", Alignment.CATALYTIC, 7, 65, CellTier.OVERCHARGED, ObjectID.GOTR_PORTAL_DEATH, ObjectID.DEATH_ALTAR, ObjectID.DEATHTEMPLE_EXIT_PORTAL, ItemID.GOTR_PORTAL_TALISMAN_DEATH, ItemID.DEATHRUNE, Quest.MOURNINGS_END_PART_II, 6),
	BLOOD("Blood", Alignment.CATALYTIC, 8, 77, CellTier.OVERCHARGED, ObjectID.GOTR_PORTAL_BLOOD, ObjectID.BLOOD_ALTAR, ObjectID.BLOODTEMPLE_EXIT_PORTAL, ItemID.GOTR_PORTAL_TALISMAN_BLOOD, ItemID.BLOODRUNE, Quest.SINS_OF_THE_FATHER, 6);

	private static final Map<Integer, Altar> BY_PORTAL_GUARDIAN;
	private static final Map<Integer, Altar> BY_ALTAR_OBJECT;
	private static final Map<Integer, Altar> BY_EXIT_PORTAL;
	private static final Map<Integer, Altar> BY_TALISMAN;
	private static final Map<Integer, Altar> BY_RUNE;

	static
	{
		ImmutableMap.Builder<Integer, Altar> guardians = new ImmutableMap.Builder<>();
		ImmutableMap.Builder<Integer, Altar> altars = new ImmutableMap.Builder<>();
		ImmutableMap.Builder<Integer, Altar> exits = new ImmutableMap.Builder<>();
		ImmutableMap.Builder<Integer, Altar> talismans = new ImmutableMap.Builder<>();
		ImmutableMap.Builder<Integer, Altar> runes = new ImmutableMap.Builder<>();
		for (Altar altar : values())
		{
			guardians.put(altar.portalGuardianObjectId, altar);
			altars.put(altar.altarObjectId, altar);
			exits.put(altar.exitPortalObjectId, altar);
			talismans.put(altar.talismanItemId, altar);
			runes.put(altar.runeItemId, altar);
		}
		BY_PORTAL_GUARDIAN = guardians.build();
		BY_ALTAR_OBJECT = altars.build();
		BY_EXIT_PORTAL = exits.build();
		BY_TALISMAN = talismans.build();
		BY_RUNE = runes.build();
	}

	private final String label;
	private final Alignment alignment;
	private final int hudIndex;
	private final int levelRequired;
	private final CellTier cellTier;
	private final int portalGuardianObjectId;
	private final int altarObjectId;
	private final int exitPortalObjectId;
	private final int talismanItemId;
	private final int runeItemId;
	@Nullable
	private final Quest requiredQuest;
	private final int insideTiles;

	@Nullable
	public static Altar fromHud(Alignment alignment, int hudIndex)
	{
		for (Altar altar : values())
		{
			if (altar.alignment == alignment && altar.hudIndex == hudIndex)
			{
				return altar;
			}
		}
		return null;
	}

	@Nullable
	public static Altar fromPortalGuardian(int objectId)
	{
		return BY_PORTAL_GUARDIAN.get(objectId);
	}

	@Nullable
	public static Altar fromAltarObject(int objectId)
	{
		return BY_ALTAR_OBJECT.get(objectId);
	}

	@Nullable
	public static Altar fromExitPortal(int objectId)
	{
		return BY_EXIT_PORTAL.get(objectId);
	}

	@Nullable
	public static Altar fromTalisman(int itemId)
	{
		return BY_TALISMAN.get(itemId);
	}

	@Nullable
	public static Altar fromRune(int itemId)
	{
		return BY_RUNE.get(itemId);
	}
}
