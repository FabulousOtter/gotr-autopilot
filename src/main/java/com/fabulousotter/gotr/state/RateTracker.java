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
package com.fabulousotter.gotr.state;

import java.util.ArrayDeque;

/**
 * Estimates gain per active tick using decayed sums with a thirty-tick prior.
 */
public class RateTracker
{
	public enum Activity
	{
		NONE,
		MINING_LARGE,
		MINING_PARTS,
		CRAFTING_ESSENCE,
		MINING_HUGE
	}

	private static final double DECAY = 0.995;
	private static final double PRIOR_WEIGHT = 30;
	private static final double PRIOR_LARGE = 0.6;
	private static final double PRIOR_PARTS = 0.4;
	private static final double PRIOR_CRAFT = 1.0;
	private static final double PRIOR_HUGE = 0.6;
	private static final int POWER_WINDOW_TICKS = 100;
	// Allow for the HUD lagging inventory updates by a few ticks.
	private static final int OWN_HAND_IN_WINDOW_TICKS = 3;

	private double largeGain;
	private double largeTicks;
	private double partsGain;
	private double partsTicks;
	private double craftGain;
	private double craftTicks;
	private double hugeGain;
	private double hugeTicks;

	private final ArrayDeque<int[]> powerSamples = new ArrayDeque<>();
	// Subtract observed own hand-ins from the crowd's power trend.
	private int ownPower;
	private int ownUntilTick = -1;
	private int lastPower = -1;

	public void reset()
	{
		powerSamples.clear();
		ownPower = 0;
		ownUntilTick = -1;
		lastPower = -1;
	}

	public void noteOwnHandIn(int tick)
	{
		ownUntilTick = tick + OWN_HAND_IN_WINDOW_TICKS;
	}

	public void tick(Activity activity, int fragmentsGained, int essenceGained)
	{
		switch (activity)
		{
			case MINING_LARGE:
				largeGain = largeGain * DECAY + Math.max(0, fragmentsGained);
				largeTicks = largeTicks * DECAY + 1;
				break;
			case MINING_PARTS:
				partsGain = partsGain * DECAY + Math.max(0, fragmentsGained);
				partsTicks = partsTicks * DECAY + 1;
				break;
			case CRAFTING_ESSENCE:
				craftGain = craftGain * DECAY + Math.max(0, essenceGained);
				craftTicks = craftTicks * DECAY + 1;
				break;
			case MINING_HUGE:
				hugeGain = hugeGain * DECAY + Math.max(0, essenceGained);
				hugeTicks = hugeTicks * DECAY + 1;
				break;
			default:
				break;
		}
	}

	public void samplePower(int tick, int power)
	{
		int[] last = powerSamples.peekLast();
		if (last != null && last[0] == tick)
		{
			return;
		}
		if (lastPower >= 0 && tick <= ownUntilTick && power > lastPower)
		{
			ownPower += power - lastPower;
		}
		lastPower = power;
		powerSamples.addLast(new int[]{tick, power - ownPower});
		while (!powerSamples.isEmpty() && tick - powerSamples.peekFirst()[0] > POWER_WINDOW_TICKS)
		{
			powerSamples.pollFirst();
		}
	}

	public double largePerTick()
	{
		return (PRIOR_WEIGHT * PRIOR_LARGE + largeGain) / (PRIOR_WEIGHT + largeTicks);
	}

	public double partsPerTick()
	{
		return (PRIOR_WEIGHT * PRIOR_PARTS + partsGain) / (PRIOR_WEIGHT + partsTicks);
	}

	public double craftPerTick()
	{
		return (PRIOR_WEIGHT * PRIOR_CRAFT + craftGain) / (PRIOR_WEIGHT + craftTicks);
	}

	public double hugePerTick()
	{
		return (PRIOR_WEIGHT * PRIOR_HUGE + hugeGain) / (PRIOR_WEIGHT + hugeTicks);
	}

	/**
	 * Excludes own hand-ins. Returns 0 for a flat, falling or insufficient sample window.
	 */
	public double powerPerTick()
	{
		int[] first = powerSamples.peekFirst();
		int[] last = powerSamples.peekLast();
		if (first == null || last == null || last[0] - first[0] < 20)
		{
			return 0;
		}
		return Math.max(0, (double) (last[1] - first[1]) / (last[0] - first[0]));
	}

	/**
	 * Returns seconds, or -1 if the rate or maximum power is unknown.
	 */
	public int secondsToClose(int power, int maxPower)
	{
		double rate = powerPerTick();
		if (rate <= 0 || maxPower <= 0)
		{
			return -1;
		}
		return (int) Math.round((maxPower - power) / rate * 0.6);
	}
}
