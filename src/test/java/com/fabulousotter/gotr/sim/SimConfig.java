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

import com.fabulousotter.gotr.plan.PlannerSettings;
import com.fabulousotter.gotr.plan.Strategy;

/**
 * One simulated world. The crowd's power rate sets the game length: 0 is a solo game that
 * ends when the player alone fills the Great Guardian, 0.35 a mass world that closes in
 * about seven minutes regardless of what the player does.
 *
 * Game timings follow the plugin's own constants (first portal at 160 s, then every 140 s,
 * open for 30 s) and the wiki (altars rotate every two minutes, the rift closes at 100%
 * power, barriers take damage from 60% power). Every rate is per game tick (0.6 s).
 */
final class SimConfig
{
	String name = "mass";
	Strategy strategy = Strategy.MASS;
	long seed = 1;
	double crowdPowerPerTick = 0.45;
	int maxPower = 250;
	/** Power added to the Great Guardian per stone the player hands in. */
	double powerPerStone = 1.0;
	int guardiansFromCrowd = 10;
	int altarRotationTicks = 200;
	int firstPortalTicks = 267;
	int portalIntervalTicks = 233;
	int portalOpenTicks = 50;
	int closingTicks = 17;
	/** Hard stop for a solo game that never fills the guardian (ticks). */
	int maxTicks = 2000;
	double largeRate = 0.6;
	double partsRate = 0.4;
	double craftRate = 1.0;
	double hugeRate = 0.6;
	/** Barrier health lost per tick during the rumble, divided by the tier rank. */
	double barrierDecay = 0.5;
	int runecraftLevel = 85;
	int agilityLevel = 60;
	boolean pickaxeWorn = true;
	int pouchCapacity = 40;
	boolean startWithCells = true;
	/** Grace-portal start: the round begins with the pouches already full of essence, standing by the Great Guardian. */
	boolean graceStart;

	SimConfig named(String n)
	{
		name = n;
		return this;
	}

	SimConfig strategy(Strategy s)
	{
		strategy = s;
		return this;
	}

	SimConfig seed(long s)
	{
		seed = s;
		return this;
	}

	SimConfig crowd(double perTick)
	{
		crowdPowerPerTick = perTick;
		return this;
	}

	SimConfig grace()
	{
		graceStart = true;
		name = name + "+g";
		return this;
	}

	SimConfig copy()
	{
		SimConfig c = new SimConfig();
		c.name = name;
		c.strategy = strategy;
		c.seed = seed;
		c.crowdPowerPerTick = crowdPowerPerTick;
		c.maxPower = maxPower;
		c.powerPerStone = powerPerStone;
		c.guardiansFromCrowd = guardiansFromCrowd;
		c.altarRotationTicks = altarRotationTicks;
		c.firstPortalTicks = firstPortalTicks;
		c.portalIntervalTicks = portalIntervalTicks;
		c.portalOpenTicks = portalOpenTicks;
		c.closingTicks = closingTicks;
		c.maxTicks = maxTicks;
		c.largeRate = largeRate;
		c.partsRate = partsRate;
		c.craftRate = craftRate;
		c.hugeRate = hugeRate;
		c.barrierDecay = barrierDecay;
		c.runecraftLevel = runecraftLevel;
		c.agilityLevel = agilityLevel;
		c.pickaxeWorn = pickaxeWorn;
		c.pouchCapacity = pouchCapacity;
		c.startWithCells = startWithCells;
		c.graceStart = graceStart;
		return c;
	}

	PlannerSettings settings()
	{
		return PlannerSettings.builder().strategy(strategy).build();
	}

	static SimConfig mass()
	{
		SimConfig c = new SimConfig().named("mass").strategy(Strategy.MASS).crowd(0.35);
		// One player among dozens: their stones barely move the bar.
		c.powerPerStone = 0.1;
		return c;
	}

	static SimConfig solo()
	{
		SimConfig c = new SimConfig().named("solo").strategy(Strategy.SOLO).crowd(0);
		c.guardiansFromCrowd = 0;
		return c;
	}

	static SimConfig team()
	{
		SimConfig c = new SimConfig().named("team").strategy(Strategy.GENERAL).crowd(0.2);
		c.guardiansFromCrowd = 4;
		c.powerPerStone = 0.5;
		return c;
	}
}
