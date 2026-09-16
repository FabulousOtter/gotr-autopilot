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
package com.fabulousotter.gotr.plan;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.fabulousotter.gotr.model.Altar;
import com.fabulousotter.gotr.model.CellTier;
import com.fabulousotter.gotr.model.PouchType;
import com.fabulousotter.gotr.state.BarrierState;
import com.fabulousotter.gotr.state.GamePhase;
import com.fabulousotter.gotr.state.Location;
import com.fabulousotter.gotr.state.PouchState;
import com.fabulousotter.gotr.state.Snapshot;
import java.util.List;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class PlannerTest
{
	private final Planner planner = new Planner();
	private final PlannerSettings settings = PlannerSettings.builder().build();
	private static final WorldPoint ME = new WorldPoint(3600, 9500, 0);

	private static Snapshot.SnapshotBuilder active()
	{
		return Snapshot.builder()
			.location(Location.TEMPLE)
			.phase(GamePhase.ACTIVE)
			.playerLocation(ME)
			.secondsSinceStart(60)
			.power(25).maxPower(250)
			.activeElemental(Altar.FIRE).activeCatalytic(Altar.MIND)
			.altarSecondsRemaining(100)
			.runecraftLevel(85).agilityLevel(60).magicLevel(90)
			.freeSlots(24)
			.chisel(true).pickaxe(true)
			.pouches(ImmutableList.of(new PouchState(PouchType.COLOSSAL, 0, 40, false)));
	}

	@Test
	public void outsideTheTempleSaysNothing()
	{
		Instruction i = planner.plan(Snapshot.builder().build(), settings);
		assertEquals(Step.NOT_IN_GAME, i.getStep());
	}

	@Test
	public void degradedPouchIsRepairedBeforeTheGame()
	{
		Snapshot s = Snapshot.builder().location(Location.TEMPLE).phase(GamePhase.WAITING)
			.pouches(ImmutableList.of(new PouchState(PouchType.COLOSSAL, 0, 35, true))).build();
		Instruction i = planner.plan(s, settings);
		assertEquals(Step.PRE_REPAIR_POUCHES, i.getStep());
		assertTrue(i.getItems().contains(ItemID.RCU_POUCH_COLOSSAL_DEGRADE));
	}

	@Test
	public void countdownAsksForCellsThenAWeakCell()
	{
		Snapshot s = Snapshot.builder().location(Location.TEMPLE).phase(GamePhase.COUNTDOWN).secondsToStart(30)
			.unchargedCells(3).weakCellTablePresent(true).build();
		assertEquals(Step.PRE_TAKE_CELLS, planner.plan(s, settings).getStep());
		s = s.toBuilder().unchargedCells(10).build();
		assertEquals(Step.PRE_TAKE_WEAK_CELL, planner.plan(s, settings).getStep());
	}

	@Test
	public void openingMinesUntilTheTargetOrTheCraftWindow()
	{
		Snapshot s = active().fragments(40).altarSecondsRemaining(100).build();
		assertEquals(Step.MINE_FRAGMENTS, planner.plan(s, settings).getStep());

		s = s.toBuilder().altarSecondsRemaining(40).build();
		assertEquals(Step.CRAFT_ESSENCE, planner.plan(s, settings).getStep());

		s = s.toBuilder().altarSecondsRemaining(100).fragments(130).build();
		assertEquals(Step.CRAFT_ESSENCE, planner.plan(s, settings).getStep());
	}

	@Test
	public void aHandfulOfFragmentsIsNotCraftedInTheOpeningWindow()
	{
		for (Strategy st : Strategy.values())
		{
			PlannerSettings cfg = settings.toBuilder().strategy(st).build();
			Snapshot s = active().activeElemental(null).activeCatalytic(null).altarSecondsRemaining(38)
				.secondsSinceStart(29).fragments(13).build();
			assertEquals(st.name(), Step.MINE_FRAGMENTS, planner.plan(s, cfg).getStep());
			s = s.toBuilder().fragments(30).build();
			assertEquals(st.name(), Step.CRAFT_ESSENCE, planner.plan(s, cfg).getStep());
		}
	}

	@Test
	public void fullOfEssenceGoesToTheOverchargedAltar()
	{
		Snapshot s = active().fragments(60).essence(24).freeSlots(0)
			.pouches(ImmutableList.of(new PouchState(PouchType.COLOSSAL, 40, 40, false))).build();
		Instruction i = planner.plan(s, settings);
		assertEquals(Step.GO_TO_ALTAR, i.getStep());
		assertEquals(Altar.FIRE, i.getAltar());
		assertEquals(Target.ALTAR_PORTAL, i.getTarget());
	}

	@Test
	public void fillsPouchesBeforeCraftingMore()
	{
		Snapshot s = active().anyPortalThisGame(true).fragments(60).essence(24).freeSlots(0)
			.pouches(ImmutableList.of(new PouchState(PouchType.COLOSSAL, 0, 40, false))).build();
		Instruction i = planner.plan(s, settings);
		assertEquals(Step.FILL_POUCHES, i.getStep());
		assertTrue(i.getItems().contains(ItemID.RCU_POUCH_COLOSSAL));
	}

	@Test
	public void imbalanceSendsYouToTheOtherType()
	{
		Snapshot s = active().essence(24).freeSlots(0).fragments(0)
			.elementalEnergy(900).catalyticEnergy(100)
			.pouches(ImmutableList.of(new PouchState(PouchType.COLOSSAL, 40, 40, false))).build();
		Instruction i = planner.plan(s, settings);
		assertEquals(Step.GO_TO_ALTAR, i.getStep());
		assertEquals(Altar.MIND, i.getAltar());
	}

	@Test
	public void talismanOpensAClosedAltar()
	{
		Snapshot s = active().essence(24).freeSlots(0).fragments(0)
			.activeElemental(Altar.AIR).activeCatalytic(Altar.MIND).altarSecondsRemaining(90)
			.talismans(ImmutableSet.of(Altar.BLOOD))
			.pouches(ImmutableList.of(new PouchState(PouchType.COLOSSAL, 40, 40, false))).build();
		Instruction i = planner.plan(s, settings);
		assertEquals(Altar.BLOOD, i.getAltar());
		assertTrue(i.getItems().contains(ItemID.GOTR_PORTAL_TALISMAN_BLOOD));
	}

	@Test
	public void combinationRunesBeatPlainElemental()
	{
		PlannerSettings combo = settings.toBuilder().combinationRunes(true).baseRune(Altar.AIR).build();
		Snapshot s = active().essence(24).freeSlots(0).fragments(0)
			.activeElemental(Altar.WATER).activeCatalytic(Altar.NATURE)
			.baseRune(Altar.AIR).baseRuneCount(500).bindingNecklaceWorn(true).necklaceCharges(10).lunarSpellbook(true)
			.pouches(ImmutableList.of(new PouchState(PouchType.COLOSSAL, 40, 40, false))).build();
		Instruction i = planner.plan(s, combo);
		assertEquals(Altar.WATER, i.getAltar());
		assertTrue(i.getDetail().contains("Mist"));

		// No necklace charges: plain crafting, so the strong nature altar wins over medium water.
		s = s.toBuilder().necklaceCharges(0).build();
		assertEquals(Altar.NATURE, planner.plan(s, combo).getAltar());
	}

	@Test
	public void stonesArePoweredUpUnlessAPortalIsOpen()
	{
		Snapshot s = active().anyPortalThisGame(true).secondsSinceLastPortal(50).elementalStones(30).freeSlots(0).build();
		Instruction i = planner.plan(s, settings);
		assertEquals(Step.POWER_UP, i.getStep());
		assertEquals(Target.GREAT_GUARDIAN, i.getTarget());

		s = s.toBuilder().portalOpen(true).portalSecondsRemaining(20).freeSlots(20).build();
		assertEquals(Step.ENTER_PORTAL, planner.plan(s, settings).getStep());
	}

	@Test
	public void stonesAreHeldForTheGracePortal()
	{
		Snapshot s = active().anyPortalThisGame(true).secondsSinceLastPortal(30)
			.power(240).maxPower(250).elementalStones(20).freeSlots(8).build();
		// Holding is a note on the next productive step, never a step that stands still.
		Instruction held = planner.plan(s, settings);
		assertEquals(Step.MINE_FRAGMENTS, held.getStep());
		assertTrue(held.getDetail().contains("keep your stones 65s"));

		s = s.toBuilder().elementalEnergy(1000).catalyticEnergy(1000).build();
		assertEquals(Step.HOLD_STONES, planner.plan(s, settings).getStep());
		s = s.toBuilder().elementalEnergy(0).catalyticEnergy(0).build();

		s = s.toBuilder().secondsSinceLastPortal(96).build();
		assertEquals(Step.POWER_UP, planner.plan(s, settings).getStep());

		s = s.toBuilder().secondsSinceLastPortal(30).elementalStones(5).build();
		assertEquals(Step.POWER_UP, planner.plan(s, settings).getStep());
	}

	@Test
	public void strongCellMakesAGuardianWhenShort()
	{
		Snapshot s = active().chargedCell(CellTier.STRONG).guardiansActive(1).build();
		Instruction i = planner.plan(s, settings);
		assertEquals(Step.CELL_GUARDIAN, i.getStep());
		assertEquals(Target.ESSENCE_PILE_ELEMENTAL, i.getTarget());
	}

	@Test
	public void weakCellRechargesTheDamagedBarrier()
	{
		WorldPoint hurt = new WorldPoint(3610, 9490, 0);
		WorldPoint fine = new WorldPoint(3612, 9490, 0);
		Snapshot s = active().chargedCell(CellTier.WEAK).guardiansActive(2)
			.barriers(ImmutableList.of(new BarrierState(fine, CellTier.STRONG, 100), new BarrierState(hurt, CellTier.STRONG, 40))).build();
		Instruction i = planner.plan(s, settings);
		assertEquals(Step.CELL_BARRIER_RECHARGE, i.getStep());
		assertEquals(hurt, i.getLocation());
	}

	@Test
	public void higherCellUpgradesTheLowestBarrier()
	{
		WorldPoint weak = new WorldPoint(3610, 9490, 0);
		Snapshot s = active().chargedCell(CellTier.OVERCHARGED).guardiansActive(2)
			.barriers(ImmutableList.of(new BarrierState(weak, CellTier.WEAK, 100))).build();
		Instruction i = planner.plan(s, settings);
		assertEquals(Step.CELL_BARRIER_UPGRADE, i.getStep());
		assertEquals(weak, i.getLocation());
	}

	@Test
	public void soloLeavesTheRightBarrierAlone()
	{
		PlannerSettings solo = settings.toBuilder().strategy(Strategy.SOLO).build();
		WorldPoint centre = new WorldPoint(3610, 9490, 0);
		WorldPoint right = new WorldPoint(3620, 9490, 0);
		Snapshot s = active().chargedCell(CellTier.MEDIUM).guardiansActive(2)
			.barriers(ImmutableList.of(new BarrierState(centre, CellTier.STRONG, 70), new BarrierState(right, CellTier.WEAK, 30))).build();
		Instruction i = planner.plan(s, solo);
		assertEquals(centre, i.getLocation());
	}

	@Test
	public void brokenTileIsRepairedFirst()
	{
		WorldPoint tile = new WorldPoint(3610, 9490, 0);
		Snapshot s = active().brokenTiles(ImmutableList.of(tile)).fragments(30).elementalStones(10).build();
		Instruction i = planner.plan(s, settings);
		assertEquals(Step.REPAIR_TILE, i.getStep());
		assertEquals(Urgency.HIGH, i.getUrgency());
		assertEquals(tile, i.getLocation());

		s = s.toBuilder().fragments(5).elementalStones(0).build();
		assertEquals(Step.MINE_FRAGMENTS_FOR_REPAIR, planner.plan(s, settings).getStep());
	}

	@Test
	public void insideTheAltarYouCraftThenLeave()
	{
		Snapshot s = active().location(Location.ALTAR_ROOM).altarRoom(Altar.FIRE).essence(24).build();
		Instruction i = planner.plan(s, settings);
		assertEquals(Step.CRAFT_RUNES, i.getStep());
		assertTrue(i.getItems().contains(ItemID.GOTR_GUARDIAN_ESSENCE));

		s = s.toBuilder().essence(0).pouches(ImmutableList.of(new PouchState(PouchType.COLOSSAL, 40, 40, false))).build();
		i = planner.plan(s, settings);
		assertEquals(Step.CRAFT_RUNES, i.getStep());
		assertTrue(i.getHeadline().startsWith("Empty your pouches"));

		s = s.toBuilder().pouches(ImmutableList.of(new PouchState(PouchType.COLOSSAL, 0, 40, false))).build();
		assertEquals(Step.LEAVE_ALTAR, planner.plan(s, settings).getStep());
	}

	@Test
	public void combinationCraftingNamesTheRuneAndImbue()
	{
		PlannerSettings combo = settings.toBuilder().combinationRunes(true).baseRune(Altar.AIR).build();
		Snapshot s = active().location(Location.ALTAR_ROOM).altarRoom(Altar.FIRE).essence(24)
			.baseRune(Altar.AIR).baseRuneCount(300).bindingNecklaceWorn(true).necklaceCharges(12).lunarSpellbook(true).build();
		Instruction i = planner.plan(s, combo);
		assertEquals("Craft smoke runes", i.getHeadline());
		assertTrue(i.getDetail().startsWith("Cast Magic Imbue"));
		assertTrue(i.getItems().contains(ItemID.AIRRUNE));
	}

	@Test
	public void hugeRemainsFillPouchesThenLeave()
	{
		Snapshot s = active().location(Location.HUGE_REMAINS).freeSlots(0).essence(24)
			.pouches(ImmutableList.of(new PouchState(PouchType.COLOSSAL, 10, 40, false))).build();
		assertEquals(Step.FILL_POUCHES, planner.plan(s, settings).getStep());

		s = s.toBuilder().pouches(ImmutableList.of(new PouchState(PouchType.COLOSSAL, 40, 40, false))).build();
		assertEquals(Step.LEAVE_HUGE_REMAINS, planner.plan(s, settings).getStep());

		s = s.toBuilder().freeSlots(5).build();
		assertEquals(Step.MINE_HUGE_REMAINS, planner.plan(s, settings).getStep());
	}

	@Test
	public void cappedEnergyMeansDefend()
	{
		Snapshot s = active().elementalEnergy(1000).catalyticEnergy(200).fragments(50).build();
		assertEquals(Step.IDLE_DEFEND, planner.plan(s, settings).getStep());
	}

	@Test
	public void runesAreDepositedBeforeALongCraft()
	{
		Snapshot s = active().anyPortalThisGame(true).fragments(60).freeSlots(10).depositableRunes(3).build();
		assertEquals(Step.DEPOSIT_RUNES, planner.plan(s, settings).getStep());
	}

	@Test
	public void weakAltarsAreUsedUnlessAWaitIsConfigured()
	{
		Snapshot s = active().essence(24).freeSlots(0).fragments(0)
			.activeElemental(Altar.AIR).activeCatalytic(Altar.MIND).altarSecondsRemaining(10)
			.pouches(ImmutableList.of(new PouchState(PouchType.COLOSSAL, 40, 40, false))).build();
		// Default: a weak altar is an altar; points come first.
		assertEquals(Step.GO_TO_ALTAR, planner.plan(s, settings).getStep());

		PlannerSettings patient = settings.toBuilder().weakAltarWaitSeconds(20).build();
		assertEquals(Step.WAIT_FOR_ALTAR, planner.plan(s, patient).getStep());
		s = s.toBuilder().altarSecondsRemaining(60).build();
		assertEquals(Step.GO_TO_ALTAR, planner.plan(s, patient).getStep());
	}

	@Test
	public void weakWaitExplainsTheLockedAltar()
	{
		Snapshot s = active().essence(24).freeSlots(0).fragments(0)
			.activeElemental(Altar.AIR).activeCatalytic(Altar.DEATH).altarSecondsRemaining(13)
			.questLockedAltars(ImmutableSet.of(Altar.DEATH))
			.pouches(ImmutableList.of(new PouchState(PouchType.COLOSSAL, 40, 40, false))).build();
		// By default Air is taken at once; with a configured wait the reason is spelled out.
		assertEquals(Altar.AIR, planner.plan(s, settings).getAltar());
		PlannerSettings patient = settings.toBuilder().weakAltarWaitSeconds(20).build();
		Instruction i = planner.plan(s, patient);
		assertEquals(Step.WAIT_FOR_ALTAR, i.getStep());
		assertEquals("Wait 13s for the next altars", i.getHeadline());
		assertTrue(i.getDetail().contains("Death: needs Mourning's End Part II"));

		s = s.toBuilder().questLockedAltars(ImmutableSet.of()).build();
		i = planner.plan(s, settings);
		assertEquals(Step.GO_TO_ALTAR, i.getStep());
		assertEquals(Altar.DEATH, i.getAltar());
	}

	@Test
	public void altarChooserHonoursLevelAndQuests()
	{
		Snapshot s = active().essence(10).runecraftLevel(50).activeElemental(Altar.FIRE).activeCatalytic(Altar.BLOOD)
			.questLockedAltars(ImmutableSet.of(Altar.BLOOD)).build();
		AltarChoice c = AltarChooser.choose(s, settings);
		assertNotNull(c);
		assertEquals(Altar.FIRE, c.getAltar());

		s = s.toBuilder().runecraftLevel(10).activeElemental(Altar.FIRE).activeCatalytic(Altar.BLOOD).build();
		assertNull(AltarChooser.choose(s, settings));
	}

	@Test
	public void massValuesTheCellFromTheTrip()
	{
		PlannerSettings mass = settings.toBuilder().strategy(Strategy.MASS).build();
		WorldPoint b = new WorldPoint(3610, 9490, 0);
		Snapshot s = active().essence(24).freeSlots(0).fragments(0).unchargedCells(10)
			.activeElemental(Altar.AIR).activeCatalytic(Altar.CHAOS).altarSecondsRemaining(90)
			.barriers(ImmutableList.of(new BarrierState(b, CellTier.OVERCHARGED, 100)))
			.pouches(ImmutableList.of(new PouchState(PouchType.COLOSSAL, 40, 40, false))).build();
		Instruction i = planner.plan(s, mass);
		assertEquals(Step.GO_TO_ALTAR, i.getStep());
		assertEquals(Altar.CHAOS, i.getAltar());
	}

	@Test
	public void massTopsUpUnlessThatMissesThePortal()
	{
		PlannerSettings mass = settings.toBuilder().strategy(Strategy.MASS).build();
		Snapshot s = active().anyPortalThisGame(true).essence(30).freeSlots(10).fragments(40)
			.pouches(ImmutableList.of(new PouchState(PouchType.COLOSSAL, 30, 40, false))).secondsToNextPortal(100).build();
		assertEquals(Step.CRAFT_ESSENCE, planner.plan(s, mass).getStep());

		s = s.toBuilder().secondsToNextPortal(15).build();
		assertEquals(Step.GO_TO_ALTAR, planner.plan(s, mass).getStep());
	}

	@Test
	public void massStopsStartingRunsBeforeTheClose()
	{
		PlannerSettings mass = settings.toBuilder().strategy(Strategy.MASS).build();
		Snapshot s = active().anyPortalThisGame(true).fragments(10).secondsToClose(20).depositableRunes(2).unchargedCells(4).build();
		// The wait for the close is spent on next-round prep, in checklist order.
		Instruction i = planner.plan(s, mass);
		assertEquals(Step.DEPOSIT_RUNES, i.getStep());
		assertTrue(i.getDetail().startsWith("Rift closes in ~20s"));
		s = s.toBuilder().depositableRunes(0).build();
		assertEquals(Step.PRE_TAKE_CELLS, planner.plan(s, mass).getStep());
		s = s.toBuilder().unchargedCells(10).build();
		assertEquals(Step.PRE_POSITION, planner.plan(s, mass).getStep());

		s = s.toBuilder().secondsToClose(300).build();
		assertEquals(Step.MINE_FRAGMENTS, planner.plan(s, mass).getStep());
	}

	@Test
	public void massRefillsCellsWhenPassingTheTable()
	{
		PlannerSettings mass = settings.toBuilder().strategy(Strategy.MASS).build();
		Snapshot s = active().anyPortalThisGame(true).fragments(70).unchargedCells(0)
			.travelTicks(ImmutableMap.of(Target.UNCHARGED_CELL_TABLE, 10)).build();
		assertEquals(Step.PRE_TAKE_CELLS, planner.plan(s, mass).getStep());

		s = s.toBuilder().travelTicks(ImmutableMap.of(Target.UNCHARGED_CELL_TABLE, 80)).build();
		assertEquals(Step.CRAFT_ESSENCE, planner.plan(s, mass).getStep());
	}

	@Test
	public void lastPortalOfTheRoundIsEnteredResupplied()
	{
		Snapshot s = active().anyPortalThisGame(true).portalOpen(true).portalSecondsRemaining(28).secondsToClose(20)
			.unchargedCells(3).elementalStones(12).depositableRunes(1)
			.travelTicks(ImmutableMap.of(Target.PORTAL, 12, Target.GREAT_GUARDIAN, 6, Target.DEPOSIT_POOL, 8, Target.UNCHARGED_CELL_TABLE, 8))
			.build();
		assertEquals(Step.POWER_UP, planner.plan(s, settings).getStep());
		s = s.toBuilder().elementalStones(0).build();
		assertEquals(Step.DEPOSIT_RUNES, planner.plan(s, settings).getStep());
		s = s.toBuilder().depositableRunes(0).build();
		assertEquals(Step.PRE_TAKE_CELLS, planner.plan(s, settings).getStep());
		s = s.toBuilder().unchargedCells(10).build();
		Instruction i = planner.plan(s, settings);
		assertEquals(Step.ENTER_PORTAL, i.getStep());
		assertTrue(i.getHeadline().contains("wait inside"));

		// No time left for the errand: go in as you are.
		s = s.toBuilder().unchargedCells(3).portalSecondsRemaining(6).build();
		assertEquals(Step.ENTER_PORTAL, planner.plan(s, settings).getStep());

		// A portal well before the close is an ordinary one, even with cells short.
		s = s.toBuilder().portalSecondsRemaining(28).secondsToClose(200).build();
		assertEquals("Enter the portal", planner.plan(s, settings).getHeadline());

		// Capped energy makes any portal the grace portal.
		s = s.toBuilder().secondsToClose(-1).elementalEnergy(1000).catalyticEnergy(1000).build();
		assertEquals(Step.PRE_TAKE_CELLS, planner.plan(s, settings).getStep());
	}

	@Test
	public void fullLoadWithNoAltarTopsUpCellsThenWaitsInTheCentre()
	{
		Snapshot s = active().activeElemental(null).activeCatalytic(null).altarSecondsRemaining(94)
			.essence(22).freeSlots(0).fragments(0).unchargedCells(5)
			.pouches(ImmutableList.of(new PouchState(PouchType.COLOSSAL, 19, 19, false))).build();
		assertEquals(Step.PRE_TAKE_CELLS, planner.plan(s, settings).getStep());
		s = s.toBuilder().unchargedCells(10).build();
		// 94 s of waiting with a full bag: drop one essence and mine into the slot.
		Instruction i = planner.plan(s, settings);
		assertEquals(Step.DROP_ESSENCE, i.getStep());
		assertTrue(i.getItems().contains(ItemID.GOTR_GUARDIAN_ESSENCE));
		// A short wait is just waited out.
		i = planner.plan(s.toBuilder().altarSecondsRemaining(20).build(), settings);
		assertEquals(Step.WAIT_FOR_ALTAR, i.getStep());
		assertEquals(Target.CENTRE_WAIT, i.getTarget());
		assertTrue(i.getDetail().startsWith("Wait in the centre"));

		// One free slot is a fragment stack: mine into it for the 92 s until the altars open,
		// to the opening target, and never craft one essence into that slot.
		for (Strategy st : Strategy.values())
		{
			PlannerSettings cfg = settings.toBuilder().strategy(st).build();
			s = s.toBuilder().freeSlots(1).essence(21).fragments(0).build();
			i = planner.plan(s, cfg);
			assertEquals(st.name(), Step.MINE_FRAGMENTS, i.getStep());
			assertEquals(st.name(), Target.LARGE_REMAINS, i.getTarget());
			assertTrue(st.name(), i.getDetail().startsWith("0 / 120"));
			s = s.toBuilder().fragments(45).build();
			assertEquals(st.name(), Step.MINE_FRAGMENTS, planner.plan(s, cfg).getStep());
			// The altars open: go, with the fragments banked for the next load.
			Snapshot open = s.toBuilder().activeElemental(Altar.FIRE).activeCatalytic(Altar.BLOOD).altarSecondsRemaining(118).build();
			assertEquals(st.name(), Step.GO_TO_ALTAR, planner.plan(open, cfg).getStep());
		}
	}

	@Test
	public void hugeRemainsLeaveOneSlotOnAGraceStart()
	{
		Snapshot s = active().location(Location.HUGE_REMAINS).activeElemental(null).activeCatalytic(null)
			.essence(21).freeSlots(1).fragments(0).pouches(ImmutableList.of(new PouchState(PouchType.COLOSSAL, 40, 40, false))).build();
		assertEquals(Step.LEAVE_HUGE_REMAINS, planner.plan(s, settings).getStep());
		// Mid-round, with an altar open, the last slot is filled as before.
		s = s.toBuilder().activeElemental(Altar.FIRE).build();
		assertEquals(Step.MINE_HUGE_REMAINS, planner.plan(s, settings).getStep());
	}

	@Test
	public void oneFreeSlotDoesNotEarnAWorkbenchDetour()
	{
		for (Strategy st : Strategy.values())
		{
			PlannerSettings cfg = settings.toBuilder().strategy(st).build();
			Snapshot s = active().anyPortalThisGame(true).fragments(7).essence(0).freeSlots(1)
				.activeElemental(Altar.EARTH).activeCatalytic(Altar.BLOOD).altarSecondsRemaining(60)
				.pouches(ImmutableList.of(new PouchState(PouchType.COLOSSAL, 39, 39, false)))
				.travelTicks(ImmutableMap.of(Target.WORKBENCH, 20)).build();
			assertEquals(st.name(), Step.GO_TO_ALTAR, planner.plan(s, cfg).getStep());
			// Even beside the workbench (two slots after a power-up and a cell): straight to the altar.
			s = s.toBuilder().freeSlots(2).travelTicks(ImmutableMap.of(Target.WORKBENCH, 1)).build();
			assertEquals(st.name(), Step.GO_TO_ALTAR, planner.plan(s, cfg).getStep());
			// A crafting run already under way is finished, though.
			assertEquals(st.name(), Step.CRAFT_ESSENCE, planner.plan(s.toBuilder().lastStep(Step.CRAFT_ESSENCE).build(), cfg).getStep());
			s = s.toBuilder().freeSlots(1).build();
			// A real top-up is still done first.
			s = s.toBuilder().freeSlots(8).travelTicks(ImmutableMap.of(Target.WORKBENCH, 20)).build();
			assertEquals(st.name(), Step.CRAFT_ESSENCE, planner.plan(s, cfg).getStep());
		}
	}

	@Test
	public void massKeepsCraftingThroughTheAltarRotation()
	{
		PlannerSettings mass = settings.toBuilder().strategy(Strategy.MASS).build();
		Snapshot s = active().anyPortalThisGame(true).fragments(127).essence(5).freeSlots(17)
			.activeElemental(Altar.WATER).activeCatalytic(Altar.NATURE).altarSecondsRemaining(1).secondsToNextPortal(75)
			.pouches(ImmutableList.of(new PouchState(PouchType.COLOSSAL, 22, 40, false)))
			.lastStep(Step.CRAFT_ESSENCE).travelTicks(ImmutableMap.of(Target.WORKBENCH, 1)).build();
		assertEquals(Step.CRAFT_ESSENCE, planner.plan(s, mass).getStep());
		// Not yet crafting: still a 35-essence top-up, worth doing before any altar.
		assertEquals(Step.CRAFT_ESSENCE, planner.plan(s.toBuilder().lastStep(null).build(), mass).getStep());
	}

	@Test
	public void gracePortalIsUsedBetweenGames()
	{
		Snapshot s = Snapshot.builder().location(Location.TEMPLE).phase(GamePhase.ENDED).portalOpen(true)
			.unchargedCells(10).build();
		assertEquals(Step.ENTER_PORTAL, planner.plan(s, settings).getStep());

		s = s.toBuilder().phase(GamePhase.COUNTDOWN).secondsToStart(40).build();
		assertEquals(Step.ENTER_PORTAL, planner.plan(s, settings).getStep());

		s = s.toBuilder().location(Location.HUGE_REMAINS).build();
		assertEquals(Step.WAIT_FOR_PORTAL, planner.plan(s, settings).getStep());
	}

	@Test
	public void gameOverHandsInStonesThenRests()
	{
		Snapshot s = active().phase(GamePhase.CLOSING).elementalStones(4).build();
		assertEquals(Step.POWER_UP, planner.plan(s, settings).getStep());
		s = s.toBuilder().elementalStones(0).depositableRunes(2).build();
		assertEquals(Step.DEPOSIT_RUNES, planner.plan(s, settings).getStep());
		s = s.toBuilder().depositableRunes(0).unchargedCells(10).build();
		Instruction i = planner.plan(s, settings);
		assertEquals(Step.PRE_POSITION, i.getStep());
		assertEquals(Target.LARGE_REMAINS, i.getTarget());
	}
}
