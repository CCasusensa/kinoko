package kinoko.provider;

import kinoko.provider.item.ItemInfo;
import kinoko.provider.item.ItemOptionInfo;
import kinoko.provider.item.ItemOptionLevelData;
import kinoko.provider.item.PetInteraction;
import kinoko.provider.wz.*;
import kinoko.provider.wz.property.WzListProperty;
import kinoko.server.ServerConfig;
import kinoko.server.ServerConstants;
import kinoko.util.Util;
import kinoko.world.item.BodyPart;
import kinoko.world.item.ItemConstants;
import kinoko.world.item.ItemGrade;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

public final class ItemProvider implements WzProvider {
    public static final Path CHARACTER_WZ = Path.of(ServerConfig.WZ_DIRECTORY, "Character.wz");
    public static final Path ITEM_WZ = Path.of(ServerConfig.WZ_DIRECTORY, "Item.wz");
    public static final List<String> EQUIP_TYPES = List.of("Accessory", "Cap", "Cape", "Coat", "Dragon", "Face", "Glove", "Hair", "Longcoat", "Mechanic", "Pants", "PetEquip", "Ring", "Shield", "Shoes", "TamingMob", "Weapon");
    public static final List<String> ITEM_TYPES = List.of("Consume", "Install", "Etc", "Cash");
    private static final Map<Integer, ItemInfo> itemInfos = new HashMap<>();
    private static final Map<Integer, ItemOptionInfo> itemOptionInfos = new HashMap<>(); // item option id -> item option info
    private static final Map<Integer, Set<Integer>> petEquips = new HashMap<>(); // petEquipId -> set<petTemplateId>
    private static final Map<Integer, Map<Integer, PetInteraction>> petActions = new HashMap<>(); // petTemplateId -> (action -> PetInteraction)

    public static void initialize() {
        // Character.wz
        try (final WzReader reader = WzReader.build(CHARACTER_WZ, new WzReaderConfig(WzConstants.WZ_GMS_IV, ServerConstants.GAME_VERSION))) {
            final WzPackage wzPackage = reader.readPackage();
            loadEquipInfos(wzPackage);
        } catch (IOException | ProviderError e) {
            throw new IllegalArgumentException("Exception caught while loading Character.wz", e);
        }
        // Item.wz
        try (final WzReader reader = WzReader.build(ITEM_WZ, new WzReaderConfig(WzConstants.WZ_GMS_IV, ServerConstants.GAME_VERSION))) {
            final WzPackage wzPackage = reader.readPackage();
            loadItemInfos(wzPackage);
            loadItemOptionInfos(wzPackage);
        } catch (IOException | ProviderError e) {
            throw new IllegalArgumentException("Exception caught while loading Item.wz", e);
        }
    }

    public static Optional<ItemInfo> getItemInfo(int itemId) {
        return Optional.ofNullable(itemInfos.get(itemId));
    }

    public static Optional<ItemOptionLevelData> getItemOptionInfo(int itemOptionId, int optionLevel) {
        if (!itemOptionInfos.containsKey(itemOptionId)) {
            return Optional.empty();
        }
        return itemOptionInfos.get(itemOptionId).getLevelData(optionLevel);
    }

    public static List<ItemOptionInfo> getPossibleItemOptions(ItemInfo itemInfo, ItemGrade itemGrade) {
        final Optional<BodyPart> bodyPartResult = BodyPart.getByItemId(itemInfo.getItemId()).stream().findFirst();
        if (bodyPartResult.isEmpty()) {
            return List.of();
        }
        final BodyPart bodyPart = bodyPartResult.get();
        final int reqLevel = itemInfo.getReqLevel();
        final List<ItemOptionInfo> possibleItemOptions = new ArrayList<>();
        for (ItemOptionInfo itemOptionInfo : itemOptionInfos.values()) {
            // Skip decent skill options
            if (itemOptionInfo.getItemOptionId() >= 31001 && itemOptionInfo.getItemOptionId() <= 31004) {
                continue;
            }
            // Check if option matches target item
            if (itemOptionInfo.isMatchingGrade(itemGrade) && itemOptionInfo.isMatchingLevel(reqLevel) && itemOptionInfo.isMatchingType(bodyPart)) {
                possibleItemOptions.add(itemOptionInfo);
            }
        }
        return possibleItemOptions;
    }

    public static boolean isPetEquipSuitable(int itemId, int templateId) {
        return petEquips.getOrDefault(itemId, Set.of()).contains(templateId);
    }

    public static Optional<PetInteraction> getPetInteraction(int templateId, int action) {
        return Optional.ofNullable(petActions.getOrDefault(templateId, Map.of()).get(action));
    }

    private static void loadEquipInfos(WzPackage source) throws ProviderError, IOException {
        for (String directoryName : EQUIP_TYPES) {
            final WzDirectory directory = source.getDirectory().getDirectories().get(directoryName);
            if (directory == null) {
                throw new ProviderError("Could not resolve Character.wz/%s", directoryName);
            }
            for (var entry : directory.getImages().entrySet()) {
                final int itemId = Integer.parseInt(entry.getKey().replace(".img", ""));
                itemInfos.put(itemId, ItemInfo.from(itemId, entry.getValue().getProperty()));
                // Pet equips
                if (!ItemConstants.isPetEquipItem(itemId)) {
                    continue;
                }
                final Set<Integer> suitablePets = new HashSet<>();
                for (var petEntry : entry.getValue().getProperty().getItems().entrySet()) {
                    if (!Util.isInteger(petEntry.getKey())) {
                        continue;
                    }
                    final int petTemplateId = Integer.parseInt(petEntry.getKey());
                    suitablePets.add(petTemplateId);
                }
                if (!suitablePets.isEmpty()) {
                    petEquips.put(itemId, Collections.unmodifiableSet(suitablePets));
                }
            }
        }
    }

    private static void loadItemInfos(WzPackage source) throws ProviderError, IOException {
        for (String directoryName : ITEM_TYPES) {
            final WzDirectory directory = source.getDirectory().getDirectories().get(directoryName);
            if (directory == null) {
                throw new ProviderError("Could not resolve Item.wz/%s", directoryName);
            }
            for (var image : directory.getImages().values()) {
                for (var entry : image.getProperty().getItems().entrySet()) {
                    final int itemId = Integer.parseInt(entry.getKey());
                    if (!(entry.getValue() instanceof WzListProperty itemProp)) {
                        throw new ProviderError("Failed to resolve item property");
                    }
                    itemInfos.put(itemId, ItemInfo.from(itemId, itemProp));
                }
            }
        }
        if (!(source.getDirectory().getDirectories().get("Pet") instanceof WzDirectory petDirectory)) {
            throw new ProviderError("Could not resolve Item.wz/Pet");
        }
        for (var imageEntry : petDirectory.getImages().entrySet()) {
            final int itemId = Integer.parseInt(imageEntry.getKey().replace(".img", ""));
            itemInfos.put(itemId, ItemInfo.from(itemId, imageEntry.getValue().getProperty()));
            // Pet interactions
            if (!(imageEntry.getValue().getProperty().get("interact") instanceof WzListProperty interactList)) {
                continue;
            }
            final Map<Integer, PetInteraction> actions = new HashMap<>();
            for (var interactionEntry : interactList.getItems().entrySet()) {
                final int action = WzProvider.getInteger(interactionEntry.getKey());
                if (!(interactionEntry.getValue() instanceof WzListProperty interactProp)) {
                    throw new ProviderError("Failed to resolve pet interact prop");
                }
                final PetInteraction interaction = PetInteraction.from(interactProp);
                actions.put(action, interaction);
            }
            petActions.put(itemId, Collections.unmodifiableMap(actions));
        }
    }

    private static void loadItemOptionInfos(WzPackage source) throws ProviderError {
        if (!(source.getDirectory().getImages().get("ItemOption.img") instanceof WzImage itemOptionImage)) {
            throw new ProviderError("Could not resolve Item.wz/ItemOption.img");
        }
        for (var entry : itemOptionImage.getProperty().getItems().entrySet()) {
            final int itemOptionId = Integer.parseInt(entry.getKey());
            if (!(entry.getValue() instanceof WzListProperty itemOptionProp)) {
                throw new ProviderError("Failed to resolve item option prop");
            }
            itemOptionInfos.put(itemOptionId, ItemOptionInfo.from(itemOptionId, itemOptionProp));
        }
    }
}
