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
import com.fabulousotter.gotr.plan.Step;
import com.fabulousotter.gotr.plan.Urgency;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Stroke;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Point;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.ui.overlay.OverlayUtil;
import net.runelite.client.ui.overlay.WidgetItemOverlay;

public class ItemHighlightOverlay extends WidgetItemOverlay
{
	private static final Stroke STROKE = new BasicStroke(2f);
	private static final String DROP_LABEL = "DROP";

	private final Client client;
	private final GotrAutopilotPlugin plugin;
	private final GotrAutopilotConfig config;

	// The slot of the essence to drop, looked up once per game tick rather than per item drawn.
	private int dropSlotTick = -1;
	private int dropSlot = -1;

	@Inject
	ItemHighlightOverlay(Client client, GotrAutopilotPlugin plugin, GotrAutopilotConfig config)
	{
		this.client = client;
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
		if (instruction.getStep() == Step.DROP_ESSENCE)
		{
			// Only one essence goes: mark the first stack in the bag and nothing else.
			if (item.getWidget() == null || item.getWidget().getIndex() != firstSlotOf(itemId))
			{
				return;
			}
			outline(graphics, bounds, Color.RED);
			FontMetrics metrics = graphics.getFontMetrics();
			int x = bounds.x + (bounds.width - metrics.stringWidth(DROP_LABEL)) / 2;
			int y = bounds.y + (bounds.height + metrics.getAscent()) / 2 - 1;
			OverlayUtil.renderTextLocation(graphics, new Point(x, y), DROP_LABEL, Color.RED);
			return;
		}
		Color color = instruction.getUrgency() == Urgency.HIGH ? config.urgentColor() : config.highlightColor();
		outline(graphics, bounds, color);
	}

	private static void outline(Graphics2D graphics, Rectangle bounds, Color color)
	{
		Stroke old = graphics.getStroke();
		graphics.setStroke(STROKE);
		graphics.setColor(color);
		graphics.drawRect(bounds.x, bounds.y, bounds.width, bounds.height);
		graphics.setStroke(old);
	}

	private int firstSlotOf(int itemId)
	{
		int tick = client.getTickCount();
		if (tick == dropSlotTick)
		{
			return dropSlot;
		}
		dropSlotTick = tick;
		dropSlot = -1;
		ItemContainer inventory = client.getItemContainer(InventoryID.INV);
		if (inventory == null)
		{
			return dropSlot;
		}
		Item[] items = inventory.getItems();
		for (int i = 0; i < items.length; i++)
		{
			if (items[i].getId() == itemId)
			{
				dropSlot = i;
				break;
			}
		}
		return dropSlot;
	}
}
