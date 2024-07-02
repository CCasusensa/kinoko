package kinoko.server.script;

import kinoko.packet.field.FieldEffectPacket;
import kinoko.packet.field.FieldPacket;
import kinoko.packet.user.UserLocal;
import kinoko.packet.user.UserRemote;
import kinoko.packet.world.MessagePacket;
import kinoko.packet.world.WvsContext;
import kinoko.provider.ItemProvider;
import kinoko.provider.item.ItemInfo;
import kinoko.provider.map.PortalInfo;
import kinoko.server.field.Instance;
import kinoko.util.Tuple;
import kinoko.world.field.Field;
import kinoko.world.item.InventoryManager;
import kinoko.world.item.InventoryOperation;
import kinoko.world.item.Item;
import kinoko.world.quest.QuestRecord;
import kinoko.world.user.User;
import kinoko.world.user.effect.Effect;
import kinoko.world.user.stat.Stat;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The inheritors of this abstract class will be the respective interfaces for the different types of Python scripts.
 * The utility methods implemented by these classes are designed to be executed inside the Python context, which
 * acquires and holds onto the user's lock during its execution, only releasing it while waiting for the user input in
 * the case of {@link NpcScriptManager}.
 */
public abstract class ScriptManager {
    protected static final Logger log = LogManager.getLogger(ScriptManager.class);
    protected final User user;

    public ScriptManager(User user) {
        this.user = user;
    }

    public abstract void disposeManager();

    public final User getUser() {
        return user;
    }


    // UTILITY METHODS --------------------------------------------------------------------------------------------

    public final void dispose() {
        user.dispose();
        disposeManager();
    }

    public final void message(String message) {
        user.write(MessagePacket.system(message));
    }

    public final void broadcastMessage(String message) {
        user.getField().broadcastPacket(MessagePacket.system(message));
    }

    public final void playPortalSE() {
        user.write(UserLocal.effect(Effect.playPortalSE()));
    }

    public final void avatarOriented(String effectPath) {
        user.write(UserLocal.effect(Effect.avatarOriented(effectPath)));
        user.dispose();
    }

    public final void squibEffect(String effectPath) {
        user.write(UserLocal.effect(Effect.squibEffect(effectPath)));
        user.dispose();
    }

    public final void balloonMsg(String text, int width, int duration) {
        user.write(UserLocal.balloonMsg(text, width, duration));
        user.dispose();
    }

    public final void tutorMsg(int index, int duration) {
        user.write(UserLocal.tutorMsg(index, duration));
        user.dispose();
    }

    public final void setDirectionMode(boolean set, int delay) {
        user.write(UserLocal.setDirectionMode(set, delay));
    }

    public final void screenEffect(String effectPath) {
        user.write(FieldEffectPacket.screen(effectPath));
    }

    public final void clock(int remain) {
        user.write(FieldPacket.clock(remain));
    }

    public final int getFieldId() {
        return user.getField().getFieldId();
    }


    // STAT METHODS ----------------------------------------------------------------------------------------------------

    public final int getGender() {
        return user.getGender();
    }

    public final int getLevel() {
        return user.getLevel();
    }

    public final int getJob() {
        return user.getJob();
    }

    public final int getMaxHp() {
        return user.getMaxHp();
    }

    public final int getHp() {
        return user.getHp();
    }

    public final void setHp(int hp) {
        user.setHp(hp);
    }

    public final void addExp(int exp) {
        user.addExp(exp);
        user.write(MessagePacket.incExp(exp, 0, true, true));
    }


    // INVENTORY METHODS -----------------------------------------------------------------------------------------------

    public final boolean addMoney(int money) {
        final InventoryManager im = user.getInventoryManager();
        if (!im.addMoney(money)) {
            return false;
        }
        user.write(WvsContext.statChanged(Stat.MONEY, im.getMoney(), false));
        user.write(MessagePacket.incMoney(money));
        return true;
    }

    public final boolean canAddMoney(int money) {
        return user.getInventoryManager().canAddMoney(money);
    }

    public final boolean addItem(int itemId, int quantity) {
        return addItems(List.of(List.of(itemId, quantity)));
    }

    public final boolean addItems(List<List<Integer>> itemList) {
        // Check inventory
        final List<Tuple<Integer, Integer>> itemCheck = new ArrayList<>();
        for (List<Integer> itemTuple : itemList) {
            if (itemTuple.size() != 2) {
                log.error("Invalid tuple length for ScriptManager.addItems {}", itemTuple);
                return false;
            }
            itemCheck.add(new Tuple<>(itemTuple.get(0), itemTuple.get(1)));
        }
        if (!user.getInventoryManager().canAddItems(itemCheck)) {
            return false;
        }
        // Create items
        final List<Item> items = new ArrayList<>();
        for (var tuple : itemCheck) {
            final int itemId = tuple.getLeft();
            final int quantity = tuple.getRight();
            final Optional<ItemInfo> itemInfoResult = ItemProvider.getItemInfo(itemId);
            if (itemInfoResult.isEmpty()) {
                log.error("Could not resolve item info for item ID : {}", itemId);
                return false;
            }
            final ItemInfo itemInfo = itemInfoResult.get();
            items.add(itemInfo.createItem(user.getNextItemSn(), Math.min(quantity, itemInfo.getSlotMax())));
        }
        // Add items to inventory
        for (Item item : items) {
            final Optional<List<InventoryOperation>> addItemResult = user.getInventoryManager().addItem(item);
            if (addItemResult.isEmpty()) {
                throw new IllegalStateException("Failed to add item to inventory");
            }
            user.write(WvsContext.inventoryOperation(addItemResult.get(), true));
            user.write(UserLocal.effect(Effect.gainItem(item)));
        }
        return true;
    }

    public final boolean canAddItem(int itemId, int quantity) {
        return canAddItems(List.of(List.of(itemId, quantity)));
    }

    public final boolean canAddItems(List<List<Integer>> itemList) {
        final List<Tuple<Integer, Integer>> itemCheck = new ArrayList<>();
        for (List<Integer> itemTuple : itemList) {
            if (itemTuple.size() != 2) {
                log.error("Invalid tuple length for ScriptManager.addItems {}", itemTuple);
                return false;
            }
            itemCheck.add(new Tuple<>(itemTuple.get(0), itemTuple.get(1)));
        }
        return user.getInventoryManager().canAddItems(itemCheck);
    }

    public final boolean removeItem(int itemId, int quantity) {
        final Optional<List<InventoryOperation>> removeItemResult = user.getInventoryManager().removeItem(itemId, quantity);
        if (removeItemResult.isPresent()) {
            user.write(WvsContext.inventoryOperation(removeItemResult.get(), true));
            user.write(UserLocal.effect(Effect.gainItem(itemId, -quantity)));
            return true;
        } else {
            return false;
        }
    }

    public final boolean hasItem(int itemId) {
        return hasItem(itemId, 1);
    }

    public final boolean hasItem(int itemId, int quantity) {
        return user.getInventoryManager().hasItem(itemId, quantity);
    }


    // QUEST METHODS ---------------------------------------------------------------------------------------------------

    public final boolean hasQuestStarted(int questId) {
        return user.getQuestManager().hasQuestStarted(questId);
    }

    public final void forceStartQuest(int questId) {
        final QuestRecord qr = user.getQuestManager().forceStartQuest(questId);
        user.write(MessagePacket.questRecord(qr));
        user.validateStat();
    }

    public final void forceCompleteQuest(int questId) {
        final QuestRecord qr = user.getQuestManager().forceCompleteQuest(questId);
        user.write(MessagePacket.questRecord(qr));
        user.validateStat();
        // Quest complete effect
        user.write(UserLocal.effect(Effect.questComplete()));
        user.getField().broadcastPacket(UserRemote.effect(user, Effect.questComplete()), user);
    }

    public final String getQRValue(int questId) {
        final Optional<QuestRecord> questRecordResult = user.getQuestManager().getQuestRecord(questId);
        return questRecordResult.map(QuestRecord::getValue).orElse("");
    }

    public final void setQRValue(int questId, String value) {
        final QuestRecord qr = user.getQuestManager().setQuestInfoEx(questId, value);
        user.write(MessagePacket.questRecord(qr));
        user.validateStat();
    }


    // WARP METHODS ----------------------------------------------------------------------------------------------------

    public final void warp(int fieldId) {
        final Optional<Field> fieldResult = user.getConnectedServer().getFieldById(fieldId);
        if (fieldResult.isEmpty()) {
            log.error("Could not resolve field ID : {}", fieldId);
            dispose();
            return;
        }
        final Field targetField = fieldResult.get();
        final Optional<PortalInfo> portalResult = targetField.getPortalById(0);
        if (portalResult.isEmpty()) {
            log.error("Tried to warp to portal : {} on field ID : {}", 0, targetField.getFieldId());
            dispose();
            return;
        }
        user.warp(fieldResult.get(), portalResult.get(), false, false);
    }

    public final void warp(int fieldId, String portalName) {
        // Resolve field
        final Optional<Field> fieldResult = user.getConnectedServer().getFieldById(fieldId);
        if (fieldResult.isEmpty()) {
            log.error("Could not resolve field ID : {}", fieldId);
            dispose();
            return;
        }
        final Field targetField = fieldResult.get();
        // Resolve portal
        final Optional<PortalInfo> portalResult = targetField.getPortalByName(portalName);
        if (portalResult.isEmpty()) {
            log.error("Tried to warp to portal : {} on field ID : {}", portalName, targetField.getFieldId());
            dispose();
            return;
        }
        user.warp(targetField, portalResult.get(), false, false);
    }

    public final void warpInstance(int mapId, String portalName, int returnMap, int timeLimit) {
        warpInstance(List.of(mapId), portalName, returnMap, timeLimit);
    }

    public final void warpInstance(List<Integer> mapIds, String portalName, int returnMap, int timeLimit) {
        // Create instance
        final Optional<Instance> instanceResult = user.getConnectedServer().createInstance(mapIds, returnMap, timeLimit);
        if (instanceResult.isEmpty()) {
            log.error("Could not create instance for map IDs : {}", mapIds);
            dispose();
            return;
        }
        final Instance instance = instanceResult.get();
        final Field targetField = instance.getFieldStorage().getFieldById(mapIds.get(0)).orElseThrow();
        // Resolve portal
        final Optional<PortalInfo> portalResult = targetField.getPortalByName(portalName);
        if (portalResult.isEmpty()) {
            log.error("Tried to warp to portal : {} on field ID : {}", portalName, targetField.getFieldId());
            dispose();
            return;
        }
        // Warp user
        user.warp(targetField, portalResult.get(), false, false);
    }


    // PARTY METHODS ---------------------------------------------------------------------------------------------------

    public final boolean hasParty() {
        return getUser().getPartyId() != 0;
    }

    public final boolean isPartyBoss() {
        return getUser().isPartyBoss();
    }

    public final void partyWarpInstance(int mapId, String portalName, int returnMap, int timeLimit) {
        partyWarpInstance(List.of(mapId), portalName, returnMap, timeLimit);
    }

    public final void partyWarpInstance(List<Integer> mapIds, String portalName, int returnMap, int timeLimit) {
        // Create instance
        final Optional<Instance> instanceResult = user.getConnectedServer().createInstance(mapIds, returnMap, timeLimit);
        if (instanceResult.isEmpty()) {
            log.error("Could not create instance for map IDs : {}", mapIds);
            dispose();
            return;
        }
        final Instance instance = instanceResult.get();
        final Field targetField = instance.getFieldStorage().getFieldById(mapIds.get(0)).orElseThrow();
        // Resolve portal
        final Optional<PortalInfo> portalResult = targetField.getPortalByName(portalName);
        if (portalResult.isEmpty()) {
            log.error("Tried to warp to portal : {} on field ID : {}", portalName, targetField.getFieldId());
            dispose();
            return;
        }
        final PortalInfo targetPortal = portalResult.get();
        // Warp user and party members in field
        user.warp(targetField, targetPortal, false, false);
        user.getField().getUserPool().forEachPartyMember(user, (member) -> {
            try (var lockedMember = member.acquire()) {
                lockedMember.get().warp(targetField, targetPortal, false, false);
            }
        });

    }
}
