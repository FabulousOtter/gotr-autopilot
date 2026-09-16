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

/**
 * Step changes trigger notifications. Keep steps independent of display wording.
 */
public enum Step
{
	NOT_IN_GAME,
	IDLE_LOBBY,
	PRE_REPAIR_POUCHES,
	PRE_TAKE_CELLS,
	PRE_TAKE_WEAK_CELL,
	PRE_BUILD_GUARDIAN,
	PRE_BUILD_BARRIER,
	PRE_POSITION,
	REPAIR_TILE,
	MINE_FRAGMENTS_FOR_REPAIR,
	ENTER_PORTAL,
	WAIT_FOR_PORTAL,
	MINE_HUGE_REMAINS,
	FILL_POUCHES,
	LEAVE_HUGE_REMAINS,
	POWER_UP,
	HOLD_STONES,
	CELL_GUARDIAN,
	CELL_BARRIER_BUILD,
	CELL_BARRIER_UPGRADE,
	CELL_BARRIER_RECHARGE,
	DEPOSIT_RUNES,
	GO_TO_ALTAR,
	WAIT_FOR_ALTAR,
	CRAFT_RUNES,
	LEAVE_ALTAR,
	CRAFT_ESSENCE,
	DROP_ESSENCE,
	MINE_FRAGMENTS,
	FREE_SPACE,
	IDLE_DEFEND,
	GAME_OVER
}
