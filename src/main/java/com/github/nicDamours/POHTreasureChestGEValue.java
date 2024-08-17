package com.github.nicDamours;

import com.github.nicDamours.Enums.ComponentID;
import com.github.nicDamours.Enums.EventScriptID;
import com.github.nicDamours.Exceptions.InvalidContainerException;
import com.github.nicDamours.Objects.ContainerPrices;
import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.ScriptPostFired;
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
import java.util.Objects;
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
        if(event.getScriptId() == EventScriptID.POH_CUSTOM_BUILD) {
            Widget treasureChestTitle = client.getWidget(ComponentID.POH_TREASURE_ROOM_WIDGET);

            if(this.hasTextWidget(treasureChestTitle)) {
                Widget treasureChestTitleText = Objects.requireNonNull(treasureChestTitle.getChildren())[1];
                String title = treasureChestTitleText.getText();

                try {
                    int selectedTierEnumID = this.getClueTierFromTitle(title);

                    List<Integer> hardClueItems = this.getPohPossibleItemsForTier(selectedTierEnumID);

                    ItemContainer itemContainers = client.getItemContainer(ComponentID.POH_CONTAINER_ID);

                    if(itemContainers == null) {
                        throw new InvalidContainerException();
                    }

                    Item[] relevantItemsInStorage = this.filterItems(itemContainers.getItems(), hardClueItems);

                    if(relevantItemsInStorage == null) {
                        return;
                    }

                    ContainerPrices price = calculate(relevantItemsInStorage);

                    if(price == null) {
                        return;
                    }

                    String formattedPrice = createValueText(price.getGePrice(), price.getHighAlchPrice());

                    treasureChestTitleText.setText(title + formattedPrice);
                } catch(InvalidContainerException exception) {
                    log.debug("opened interface is not treasure chest, abording.");
                } catch(Exception exception) {
                    log.debug("Error while retrieving container info: " + exception.getMessage());
                }
            }
        }
    }


    @Provides
    POHTreasureChestGEValueConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(POHTreasureChestGEValueConfig.class);
    }

    private int getClueTierFromTitle(String title)  throws Exception {
        String selectedTier = this.getTitleMatch(title);

        switch(selectedTier.toLowerCase()) {
            case "beginner": return EnumID.POH_COSTUME_CLUE_BEGINNER;
            case "easy": return EnumID.POH_COSTUME_CLUE_EASY;
            case "medium": return EnumID.POH_COSTUME_CLUE_MEDIUM;
            case "hard": return EnumID.POH_COSTUME_CLUE_HARD;
            case "elite": return EnumID.POH_COSTUME_CLUE_ELITE;
            case "master": return EnumID.POH_COSTUME_CLUE_MASTER;
            default: throw new InvalidContainerException();
        }
    }

    private String getTitleMatch(String title) throws Exception {
        Pattern pattern = Pattern.compile(this.tierRegexp, Pattern.CASE_INSENSITIVE);

        Matcher matcher = pattern.matcher(title);

        if(!matcher.matches()) {
            throw new InvalidContainerException();
        }

        return matcher.group(1);
    }

    private boolean hasTextWidget(Widget treasureChestTitle) {
        return treasureChestTitle != null && treasureChestTitle.getChildren() != null && treasureChestTitle.getChildren().length >= 2;
    }

    /**
     * Took from Runelite's BankPlugin
     * @see https://github.com/runelite/runelite/blob/500e294fc06884734cbf74590446930363f20334/runelite-client/src/main/java/net/runelite/client/plugins/bank/BankPlugin.java#L605
     * @param itemId
     * @return
     */
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

    /**
     * Took from Runelite's BankPlugin
     * @see https://github.com/runelite/runelite/blob/500e294fc06884734cbf74590446930363f20334/runelite-client/src/main/java/net/runelite/client/plugins/bank/BankPlugin.java#L578
     * @param items
     * @return
     */
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

    @Nullable
    private Item[] filterItems(@Nullable Item[] allItems, List<Integer> wantedItems) {
        return Arrays.stream(allItems).filter(x -> wantedItems.contains(x.getId())).toArray(Item[]::new);
    }

    /**
     * Took from RuneLite's BankPlugin
     * @see https://github.com/runelite/runelite/blob/500e294fc06884734cbf74590446930363f20334/runelite-client/src/main/java/net/runelite/client/plugins/bank/BankPlugin.java#L386
     * @param gePrice
     * @param haPrice
     * @return
     */
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
