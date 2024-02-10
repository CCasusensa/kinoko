package kinoko.world.item;

public enum WeaponType {
    NONE(0),
    COUNT(17),
    EXCOUNT(18),
    OH_SWORD(30),
    OH_AXE(31),
    OH_MACE(32),
    DAGGER(33),
    SUB_DAGGER(34),
    WAND(37),
    STAFF(38),
    BAREHAND(39),
    TH_SWORD(40),
    TH_AXE(41),
    TH_MACE(42),
    SPEAR(43),
    POLEARM(44),
    BOW(45),
    CROSSBOW(46),
    THROWINGGLOVE(47),
    KNUCKLE(48),
    GUN(49),
    NOTCHECK_SUBWEPPON(-1);

    private final int value;

    WeaponType(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static WeaponType getByValue(int value) {
        for (WeaponType type : values()) {
            if (type.getValue() == value) {
                return type;
            }
        }
        return null;
    }
}
