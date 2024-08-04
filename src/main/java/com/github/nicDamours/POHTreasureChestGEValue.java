package com.github.nicDamours;

import com.github.nicDamours.Objects.ContainerPrices;
import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.util.QuantityFormatter;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@PluginDescriptor(
        name = "POH Treasure Chest GE Value",
        description = "Show the total GE value of your POH treasure chest",
        tags = {"poh", "ge", "value", "bank"}
)
public class POHTreasureChestGEValue extends Plugin {
    @Inject
    private Client client;

    @Inject
    private ItemManager itemManager;

    @Inject
    private POHTreasureChestGEValueConfig config;

    private String tierRegexp = "Treasure Chest: (\\w+) rewards \\(\\d+ / \\d+\\)";


    @Override
    protected void startUp() throws Exception {
        log.info("POHTreasureChestGEValue started");
    }

    @Override
    protected void shutDown() throws Exception {
        log.info("POHTreasureChestGEValue stopped");
    }


    @Subscribe
    public void onScriptPostFired(ScriptPostFired event) throws Exception {
        if(event.getScriptId() == 3534) {
            Widget treasureChestTitle = client.getWidget(44236802);

            if(treasureChestTitle.getChildren().length >= 2) {
                String title = treasureChestTitle.getChildren()[1].getText();

                log.debug("text: " + treasureChestTitle.getText());

                Pattern pattern = Pattern.compile(this.tierRegexp, Pattern.CASE_INSENSITIVE);

                Matcher matcher = pattern.matcher(title);


                if(!matcher.matches()) {
                    throw new Exception("Could not determine selected tier");
                }

                String selectedTier = matcher.group(1);

                int selectedTierEnumID;

                switch(selectedTier.toLowerCase()) {
                    case "beginner":
                        selectedTierEnumID = EnumID.POH_COSTUME_CLUE_BEGINNER;
                        break;
                    case "easy":
                        selectedTierEnumID = EnumID.POH_COSTUME_CLUE_EASY;
                        break;
                    case "medium":
                        selectedTierEnumID = EnumID.POH_COSTUME_CLUE_MEDIUM;
                        break;
                    case "hard":
                        selectedTierEnumID = EnumID.POH_COSTUME_CLUE_HARD;
                        break;
                    case "elite":
                        selectedTierEnumID = EnumID.POH_COSTUME_CLUE_ELITE;
                        break;
                    case "master":
                        selectedTierEnumID = EnumID.POH_COSTUME_CLUE_MASTER;
                        break;
                    default:
                        throw new Exception("invalid clue tier " + selectedTier.toLowerCase());
                };


                List<Integer> hardClueItems = this.getPohPossibleItemsForTier(selectedTierEnumID);

                ItemContainer itemContainers = client.getItemContainer(33405);

                Item[] relevantItemsInStorage = this.filterItems(itemContainers.getItems(), hardClueItems);

                ContainerPrices price = calculate(relevantItemsInStorage);

                log.debug("found " + relevantItemsInStorage.length + " items");

                String formattedPrice = createValueText(price.getGePrice(), price.getHighAlchPrice());

                treasureChestTitle.getChildren()[1].setText(title + formattedPrice);
            }
        }
    }


    @Provides
    POHTreasureChestGEValueConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(POHTreasureChestGEValueConfig.class);
    }

    private int getHaPrice(int itemId)
    {
        switch (itemId)
        {
            case ItemID.COINS_995:
                return 1;
            case ItemID.PLATINUM_TOKEN:
                return 1000;
            default:
                return itemManager.getItemComposition(itemId).getHaPrice();
        }
    }

    @Nullable
    ContainerPrices calculate(@Nullable Item[] items)
    {
        if (items == null)
        {
            return null;
        }

        long ge = 0;
        long alch = 0;

        for (final Item item : items)
        {
            final int qty = item.getQuantity();
            final int id = item.getId();

            if (id <= 0 || qty == 0)
            {
                continue;
            }

            alch += (long) getHaPrice(id) * qty;
            ge += (long) itemManager.getItemPrice(id) * qty;
        }

        return new ContainerPrices(ge, alch);
    }

    private List<Integer> getPohPossibleItemsForTier(int tierEnumId) {
        EnumComposition members = client.getEnum(EnumID.POH_COSTUME_MEMBERS);
        EnumComposition alt = client.getEnum(EnumID.POH_COSTUME_ALTERNATE);
        EnumComposition alts = client.getEnum(EnumID.POH_COSTUME_ALTERNATES);

        ArrayList<Integer> allItems = new ArrayList<>();

        var tierEnum = client.getEnum(tierEnumId);


        for (int baseItem : tierEnum.getIntVals())
        {
            int membersEnumId = members.getIntValue(baseItem);
            if (membersEnumId != -1)
            {
                // check members in the group
                var memberEnum = client.getEnum(membersEnumId);
                for (int memberItem : memberEnum.getIntVals())
                {
                    allItems.add(memberItem);
                }
            }
            else
            {
                allItems.add(baseItem);
            }
        }

        return allItems;
    }

    private Item[] filterItems(Item[] allItems, List<Integer> wantedItems) {
        return Arrays.stream(allItems).filter(x -> wantedItems.contains(x.getId())).toArray(Item[]::new);
    }

    private String createValueText(long gePrice, long haPrice)
    {
        StringBuilder stringBuilder = new StringBuilder();
        if (config.showGE() && gePrice != 0)
        {
            stringBuilder.append(" (");

            if (config.showHA())
            {
                stringBuilder.append("GE: ");
            }

            if (config.showExact())
            {
                stringBuilder.append(QuantityFormatter.formatNumber(gePrice));
            }
            else
            {
                stringBuilder.append(QuantityFormatter.quantityToStackSize(gePrice));
            }
            stringBuilder.append(')');
        }

        if (config.showHA() && haPrice != 0)
        {
            stringBuilder.append(" (");

            if (config.showGE())
            {
                stringBuilder.append("HA: ");
            }

            if (config.showExact())
            {
                stringBuilder.append(QuantityFormatter.formatNumber(haPrice));
            }
            else
            {
                stringBuilder.append(QuantityFormatter.quantityToStackSize(haPrice));
            }
            stringBuilder.append(')');
        }

        return stringBuilder.toString();
    }
}
