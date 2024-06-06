package kinoko.server.dialog.miniroom;

import kinoko.packet.field.MiniRoomPacket;
import kinoko.packet.world.WvsContext;
import kinoko.packet.world.broadcast.BroadcastMessage;
import kinoko.packet.world.message.Message;
import kinoko.provider.ItemProvider;
import kinoko.provider.item.ItemInfo;
import kinoko.server.packet.InPacket;
import kinoko.util.Locked;
import kinoko.world.GameConstants;
import kinoko.world.item.*;
import kinoko.world.user.User;
import kinoko.world.user.stat.Stat;

import java.util.*;
import java.util.stream.Collectors;

public final class TradingRoom extends MiniRoom {
    private final Map<User, Map<Integer, Item>> items = new HashMap<>(); // position -> slot, items
    private final Map<User, Integer> money = new HashMap<>(); // position -> money
    private final Map<User, Boolean> confirm = new HashMap<>(); // position -> trade confirmation
    private final User inviter;
    private User target;

    public TradingRoom(User inviter) {
        this.inviter = inviter;
    }

    public User getInviter() {
        return inviter;
    }

    public boolean isInviter(User user) {
        return inviter.getCharacterId() == user.getCharacterId();
    }

    public User getTarget() {
        return target;
    }

    public void setTarget(User target) {
        this.target = target;
    }

    @Override
    public MiniRoomType getType() {
        return MiniRoomType.TRADING_ROOM;
    }

    @Override
    public boolean checkPassword(String password) {
        return true;
    }

    @Override
    public int getMaxUsers() {
        return 2;
    }

    @Override
    public boolean addUser(User user) {
        if (getTarget() != null) {
            return false;
        }
        setTarget(user);
        getInviter().write(MiniRoomPacket.enterBase(1, user));
        return true;
    }

    @Override
    public Map<Integer, User> getUsers() {
        if (getTarget() == null) {
            return Map.of(
                    0, getInviter()
            );
        } else {
            return Map.of(
                    0, getInviter(),
                    1, getTarget()
            );
        }
    }

    @Override
    public void handlePacket(Locked<User> locked, MiniRoomProtocol mrp, InPacket inPacket) {
        final User user = locked.get();
        final User other = isInviter(user) ? getTarget() : getInviter();
        if (other == null) {
            log.error("Received trading room action {} without another player in the trading room", mrp);
            return;
        }
        switch (mrp) {
            case TRP_PutItem -> {
                // CTradingRoomDlg::PutItem
                final int type = inPacket.decodeByte(); // nItemTI
                final InventoryType inventoryType = InventoryType.getByValue(type);
                if (inventoryType == null) {
                    log.error("Unknown inventory type : {}", type);
                    return;
                }
                final int position = inPacket.decodeShort(); // nSlotPosition
                final int quantity = inPacket.decodeShort(); // nInputNo_Result
                final int index = inPacket.decodeByte(); // ItemIndexFromPoint
                if (!addItem(locked, inventoryType, position, quantity, index)) {
                    log.error("Failed to add item to trading room");
                    user.write(WvsContext.broadcastMsg(BroadcastMessage.alert("This request has failed due to an unknown error.")));
                }
            }
            case TRP_PutMoney -> {
                // CTradingRoomDlg::PutMoney
                final int addMoney = inPacket.decodeInt(); // nInputNo_Result
                if (!addMoney(locked, addMoney)) {
                    log.error("Failed to add money to trading room");
                    user.write(WvsContext.broadcastMsg(BroadcastMessage.alert("This request has failed due to an unknown error.")));
                }
            }
            case TRP_Trade -> {
                // CTradingRoomDlg::Trade
                confirm.put(user, true);
                // Update other
                if (!confirm.getOrDefault(other, false)) {
                    other.write(MiniRoomPacket.TradingRoom.trade());
                    return;
                }
                // Complete trade
                if (!completeTrade(locked)) {
                    cancelTrade(locked, LeaveType.TRADE_FAIL); // Trade unsuccessful.
                }
            }
            case TRP_ItemCRC -> {
                // ignored
            }
            default -> {
                log.error("Unhandled trading room action {}", mrp);
            }
        }
    }


    // UTILITY METHODS -------------------------------------------------------------------------------------------------

    /**
     * This should only be called after acquiring the {@link kinoko.util.Lockable<User>} object.
     *
     * @see User#isLocked()
     */
    public void cancelTradeUnsafe(User user) {
        // Return items and update client
        assert user.isLocked();
        addItemsAndMoney(user, items.getOrDefault(user, Map.of()).values(), money.getOrDefault(user, 0));
        user.write(MiniRoomPacket.leave(0, LeaveType.USER_REQUEST)); // no message
        user.setDialog(null);
        final User other = isInviter(user) ? getTarget() : getInviter();
        if (other != null) {
            try (var lockedOther = other.acquire()) {
                // Return the other user's items and update their client
                addItemsAndMoney(lockedOther.get(), items.getOrDefault(other, Map.of()).values(), money.getOrDefault(other, 0));
                other.write(MiniRoomPacket.leave(1, LeaveType.CLOSED)); // Trade cancelled by the other character.
                other.setDialog(null);
            }
        }
        close();
    }

    public void cancelTrade(Locked<User> locked, LeaveType leaveType) {
        // Return items and update client
        final User user = locked.get();
        addItemsAndMoney(user, items.getOrDefault(user, Map.of()).values(), money.getOrDefault(user, 0));
        user.write(MiniRoomPacket.leave(0, leaveType));
        user.setDialog(null);
        final User other = isInviter(user) ? getTarget() : getInviter();
        if (other != null) {
            try (var lockedOther = other.acquire()) {
                // Return the other user's items and update their client
                addItemsAndMoney(lockedOther.get(), items.getOrDefault(other, Map.of()).values(), money.getOrDefault(other, 0));
                other.write(MiniRoomPacket.leave(1, leaveType));
                other.setDialog(null);
            }
        }
        close();
    }

    private boolean addItem(Locked<User> locked, InventoryType inventoryType, int position, int quantity, int index) {
        // Resolve other user
        final User user = locked.get();
        final User other = isInviter(user) ? getTarget() : getInviter();
        if (other == null) {
            return false;
        }
        // Check if item can be placed in the index
        if (index < 1 || index > 9) {
            return false;
        }
        if (items.getOrDefault(user, Map.of()).containsKey(index)) {
            return false;
        }
        // Resolve item
        final InventoryManager im = user.getInventoryManager();
        final Inventory inventory = im.getInventoryByType(inventoryType);
        final Item item = inventory.getItem(position);
        if (item == null) {
            return false;
        }
        final Optional<ItemInfo> itemInfoResult = ItemProvider.getItemInfo(item.getItemId());
        final boolean isQuest = itemInfoResult.map(ItemInfo::isQuest).orElse(false);
        final boolean isTradeBlock = itemInfoResult.map(ItemInfo::isTradeBlock).orElse(false);
        if ((isQuest || isTradeBlock) && !item.isPossibleTrading()) {
            return false;
        }
        if (item.getItemType() == ItemType.BUNDLE && !ItemConstants.isRechargeableItem(item.getItemId()) &&
                item.getQuantity() > quantity) {
            // Update item count
            item.setQuantity((short) (item.getQuantity() - quantity));
            user.write(WvsContext.inventoryOperation(InventoryOperation.itemNumber(inventoryType, position, item.getQuantity()), true));
            // Create partial item
            final Item partialItem = new Item(item);
            partialItem.setItemSn(user.getNextItemSn());
            partialItem.setQuantity((short) quantity);
            // Put item in trading room
            items.computeIfAbsent(user, (key) -> new HashMap<>()).put(index, partialItem);
            user.write(MiniRoomPacket.TradingRoom.putItem(0, index, partialItem));
            other.write(MiniRoomPacket.TradingRoom.putItem(1, index, partialItem));
        } else {
            // Remove full stack from inventory
            if (!inventory.removeItem(position, item)) {
                return false;
            }
            user.write(WvsContext.inventoryOperation(InventoryOperation.delItem(inventoryType, position), true));
            // Put item in trading room
            items.computeIfAbsent(user, (key) -> new HashMap<>()).put(index, item);
            user.write(MiniRoomPacket.TradingRoom.putItem(0, index, item));
            other.write(MiniRoomPacket.TradingRoom.putItem(1, index, item));
        }
        return true;
    }

    private boolean addMoney(Locked<User> locked, int addMoney) {
        // Resolve other user
        final User user = locked.get();
        final User other = isInviter(user) ? getTarget() : getInviter();
        if (other == null) {
            return false;
        }
        // Check if money can be added to trading room
        if (addMoney < 0) {
            return false;
        }
        final long newMoney = (long) money.getOrDefault(user, 0) + addMoney;
        if (newMoney > Integer.MAX_VALUE || newMoney < 0) {
            return false;
        }
        // Move money
        final InventoryManager im = user.getInventoryManager();
        if (!im.addMoney(-addMoney)) {
            return false;
        }
        user.write(WvsContext.statChanged(Stat.MONEY, im.getMoney(), true));
        money.put(user, (int) newMoney);
        user.write(MiniRoomPacket.TradingRoom.putMoney(0, (int) newMoney));
        other.write(MiniRoomPacket.TradingRoom.putMoney(1, (int) newMoney));
        return true;
    }

    private boolean completeTrade(Locked<User> locked) {
        // Check for confirmations
        final User user = locked.get();
        if (!confirm.getOrDefault(user, false)) {
            return false;
        }
        final User other = isInviter(user) ? getTarget() : getInviter();
        if (other == null || !confirm.getOrDefault(other, false)) {
            return false;
        }
        try (var lockedOther = other.acquire()) {
            // Check that user can add items + money from other's position
            final Set<Item> itemsForUser = items.getOrDefault(other, Map.of()).values().stream().collect(Collectors.toUnmodifiableSet());
            final int moneyForUser = GameConstants.getTradeTax(money.getOrDefault(other, 0));
            if (!user.getInventoryManager().canAddItems(itemsForUser)) {
                user.write(WvsContext.message(Message.system("You do not have enough inventory space.")));
                other.write(WvsContext.message(Message.system(user.getCharacterName() + " does not have enough inventory space.")));
                return false;
            }
            if (!user.getInventoryManager().canAddMoney(moneyForUser)) {
                user.write(WvsContext.message(Message.system("You cannot hold any more mesos.")));
                other.write(WvsContext.message(Message.system(user.getCharacterName() + " cannot hold any more mesos.")));
                return false;
            }
            // Check that other can add items + money from user's position
            final Set<Item> itemsForOther = items.getOrDefault(user, Map.of()).values().stream().collect(Collectors.toUnmodifiableSet());
            final int moneyForOther = GameConstants.getTradeTax(money.getOrDefault(user, 0));
            if (!other.getInventoryManager().canAddItems(itemsForOther)) {
                other.write(WvsContext.message(Message.system("You do not have enough inventory space.")));
                user.write(WvsContext.message(Message.system(user.getCharacterName() + " does not have enough inventory space.")));
                return false;
            }
            if (!other.getInventoryManager().canAddMoney(moneyForOther)) {
                other.write(WvsContext.message(Message.system("You cannot hold any more mesos.")));
                user.write(WvsContext.message(Message.system(user.getCharacterName() + " cannot hold any more mesos.")));
                return false;
            }
            // Add all items + money
            addItemsAndMoney(user, itemsForUser, moneyForUser);
            addItemsAndMoney(other, itemsForOther, moneyForOther);
            // Complete trade
            broadcastPacket(MiniRoomPacket.leave(0, LeaveType.TRADE_DONE)); // Trade successful. Please check the results.
            user.setDialog(null);
            other.setDialog(null);
            close();
        }
        return true;
    }

    private void addItemsAndMoney(User user, Collection<Item> addItems, int addMoney) {
        assert user.isLocked();
        final InventoryManager im = user.getInventoryManager();
        final List<InventoryOperation> inventoryOperations = new ArrayList<>();
        for (Item item : addItems) {
            final Optional<List<InventoryOperation>> addResult = im.addItem(item);
            if (addResult.isEmpty()) {
                throw new IllegalStateException("Failed to add item to inventory");
            }
            inventoryOperations.addAll(addResult.get());
        }
        if (!im.addMoney(addMoney)) {
            throw new IllegalStateException("Failed to add money");
        }
        user.write(WvsContext.inventoryOperation(inventoryOperations, false));
        user.write(WvsContext.statChanged(Stat.MONEY, im.getMoney(), true));
    }
}