package kinoko.world.quest;

public enum QuestRequestType {
    // QuestReq
    LOST_ITEM(0),
    ACCEPT_QUEST(1),
    COMPLETE_QUEST(2),
    RESIGN_QUEST(3),
    OPENING_SCRIPT(4),
    COMPLETE_SCRIPT(5);

    private final byte value;

    QuestRequestType(int value) {
        this.value = (byte) value;
    }

    public final byte getValue() {
        return value;
    }

    public static QuestRequestType getByValue(int value) {
        for (QuestRequestType action : values()) {
            if (action.getValue() == value) {
                return action;
            }
        }
        return null;
    }
}
