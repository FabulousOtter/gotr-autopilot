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
package com.fabulousotter.gotr.overlay;

import com.fabulousotter.gotr.GotrAutopilotConfig;
import com.fabulousotter.gotr.GotrAutopilotPlugin;
import com.fabulousotter.gotr.plan.Instruction;
import com.fabulousotter.gotr.plan.Urgency;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Stroke;
import javax.inject.Inject;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.ui.overlay.WidgetItemOverlay;

public class ItemHighlightOverlay extends WidgetItemOverlay
{
	private static final Stroke STROKE = new BasicStroke(2f);

	private final GotrAutopilotPlugin plugin;
	private final GotrAutopilotConfig config;

	@Inject
	ItemHighlightOverlay(GotrAutopilotPlugin plugin, GotrAutopilotConfig config)
	{
		this.plugin = plugin;
		this.config = config;
		showOnInventory();
	}

	@Override
	public void renderItemOverlay(Graphics2D graphics, int itemId, WidgetItem item)
	{
		Instruction instruction = plugin.getInstruction();
		if (!instruction.getItems().contains(itemId))
		{
			return;
		}
		Rectangle bounds = item.getCanvasBounds();
		if (bounds == null)
		{
			return;
		}
		Color color = instruction.getUrgency() == Urgency.HIGH ? config.urgentColor() : config.highlightColor();
		Stroke old = graphics.getStroke();
		graphics.setStroke(STROKE);
		graphics.setColor(color);
		graphics.drawRect(bounds.x, bounds.y, bounds.width, bounds.height);
		graphics.setStroke(old);
	}
}
