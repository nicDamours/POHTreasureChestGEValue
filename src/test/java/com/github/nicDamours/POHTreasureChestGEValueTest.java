package com.github.nicDamours;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

import java.util.ArrayList;

public class POHTreasureChestGEValueTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(POHTreasureChestGEValue.class);
		RuneLite.main(args);
	}
}