package com.github.nicDamours;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("pohtreasurechestgevalue")
public interface POHTreasureChestGEValueConfig extends Config
{
	@ConfigItem(
			keyName = "showGE",
			name = "Show Grand Exchange price",
			description = "Show grand exchange price total (GE)",
			position = 1
	)
	default boolean showGE()
	{
		return true;
	}

	@ConfigItem(
			keyName = "showHA",
			name = "Show high alchemy price",
			description = "Show high alchemy price total (HA)",
			position = 2
	)
	default boolean showHA()
	{
		return false;
	}

	@ConfigItem(
			keyName = "showExact",
			name = "Show exact value",
			description = "Show exact value",
			position = 3
	)
	default boolean showExact()
	{
		return false;
	}

}
