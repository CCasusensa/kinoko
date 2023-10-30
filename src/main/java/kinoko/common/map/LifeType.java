package kinoko.common.map;

public enum LifeType {
    NPC("n"),
    MOB("m");

    private final String id;

    LifeType(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public static LifeType fromString(String id) {
        for (LifeType type : LifeType.values()) {
            if (type.getId().equals(id)) {
                return type;
            }
        }
        return null;
    }
}
