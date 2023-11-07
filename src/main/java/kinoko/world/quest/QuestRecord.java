package kinoko.world.quest;

import kinoko.util.FileTime;
import lombok.Data;

@Data
public final class QuestRecord {
    private int questId;
    private String questInfo;
    private FileTime completedTime;
}
