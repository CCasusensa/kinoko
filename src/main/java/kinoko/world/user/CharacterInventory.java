package kinoko.world.user;

import kinoko.world.item.Inventory;
import kinoko.world.item.InventoryType;

public final class CharacterInventory {
    private Inventory equipped;
    private Inventory equipInventory;
    private Inventory consumeInventory;
    private Inventory installInventory;
    private Inventory etcInventory;
    private Inventory cashInventory;
    private int money;

    public Inventory getEquipped() {
        return equipped;
    }

    public void setEquipped(Inventory equipped) {
        this.equipped = equipped;
    }

    public Inventory getEquipInventory() {
        return equipInventory;
    }

    public void setEquipInventory(Inventory equipInventory) {
        this.equipInventory = equipInventory;
    }

    public Inventory getConsumeInventory() {
        return consumeInventory;
    }

    public void setConsumeInventory(Inventory consumeInventory) {
        this.consumeInventory = consumeInventory;
    }

    public Inventory getInstallInventory() {
        return installInventory;
    }

    public void setInstallInventory(Inventory installInventory) {
        this.installInventory = installInventory;
    }

    public Inventory getEtcInventory() {
        return etcInventory;
    }

    public void setEtcInventory(Inventory etcInventory) {
        this.etcInventory = etcInventory;
    }

    public Inventory getCashInventory() {
        return cashInventory;
    }

    public void setCashInventory(Inventory cashInventory) {
        this.cashInventory = cashInventory;
    }

    public int getMoney() {
        return money;
    }

    public void setMoney(int money) {
        this.money = money;
    }

    public Inventory getInventoryByItemId(int itemId) {
        return getInventoryByType(InventoryType.getByItemId(itemId));
    }

    public Inventory getInventoryByType(InventoryType inventoryType) {
        switch (inventoryType) {
            case EQUIP -> {
                return getEquipInventory();
            }
            case CONSUME -> {
                return getConsumeInventory();
            }
            case INSTALL -> {
                return getInstallInventory();
            }
            case ETC -> {
                return getEtcInventory();
            }
            case CASH -> {
                return getCashInventory();
            }
        }
        return null;
    }
}
