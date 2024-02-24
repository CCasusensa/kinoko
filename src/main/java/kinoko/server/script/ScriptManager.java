package kinoko.server.script;

import kinoko.packet.field.FieldPacket;
import kinoko.packet.field.effect.FieldEffect;
import kinoko.packet.user.UserLocal;
import kinoko.packet.user.effect.Effect;
import kinoko.packet.world.WvsContext;
import kinoko.packet.world.message.IncExpMessage;
import kinoko.packet.world.message.Message;
import kinoko.provider.ItemProvider;
import kinoko.provider.item.ItemInfo;
import kinoko.provider.map.PortalInfo;
import kinoko.world.field.Field;
import kinoko.world.item.InventoryOperation;
import kinoko.world.item.Item;
import kinoko.world.user.User;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Optional;

public abstract class ScriptManager {
    protected static final Logger log = LogManager.getLogger(ScriptManager.class);
    protected final User user;

    public ScriptManager(User user) {
        this.user = user;
    }

    public abstract void disposeManager();

    public User getUser() {
        return user;
    }


    // UTILITY METHODS --------------------------------------------------------------------------------------------

    public final void dispose() {
        user.dispose();
        disposeManager();
    }

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
        final Optional<Field> fieldResult = user.getConnectedServer().getFieldById(fieldId);
        if (fieldResult.isEmpty()) {
            log.error("Could not resolve field ID : {}", fieldId);
            dispose();
            return;
        }
        final Field targetField = fieldResult.get();
        final Optional<PortalInfo> portalResult = targetField.getPortalByName(portalName);
        if (portalResult.isEmpty()) {
            log.error("Tried to warp to portal : {} on field ID : {}", portalName, targetField.getFieldId());
            dispose();
            return;
        }
        user.warp(fieldResult.get(), portalResult.get(), false, false);
    }

    public final void avatarOriented(String effectPath) {
        user.write(UserLocal.effect(Effect.avatarOriented(effectPath)));
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

    public final void screenEffect(String effectPath) {
        user.write(FieldPacket.effect(FieldEffect.screen(effectPath)));
    }


    // STAT METHODS ----------------------------------------------------------------------------------------------------

    public final int getGender() {
        return user.getCharacterStat().getGender();
    }

    public final int getHp() {
        return user.getHp();
    }

    public final void setHp(int hp) {
        user.setHp(hp);
    }

    public final void addExp(int exp) {
        user.addExp(exp);
        user.write(WvsContext.message(IncExpMessage.quest(exp)));
    }


    // INVENTORY METHODS -----------------------------------------------------------------------------------------------

    public final boolean addMoney(int money) {
        if (!user.getInventoryManager().addMoney(money)) {
            return false;
        }
        user.write(WvsContext.message(Message.incMoney(money)));
        return true;
    }

    public final boolean addItem(int itemId) {
        return addItem(itemId, 1);
    }

    public final boolean addItem(int itemId, int quantity) {
        final Optional<ItemInfo> itemInfoResult = ItemProvider.getItemInfo(itemId);
        if (itemInfoResult.isEmpty()) {
            return false;
        }
        final ItemInfo ii = itemInfoResult.get();
        final Item item = ii.createItem(user.getNextItemSn(), Math.min(quantity, ii.getSlotMax()));
        final Optional<List<InventoryOperation>> addItemResult = user.getInventoryManager().addItem(item);
        if (addItemResult.isPresent()) {
            user.write(WvsContext.inventoryOperation(addItemResult.get(), true));
            user.write(UserLocal.effect(Effect.gainItem(item)));
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
}
