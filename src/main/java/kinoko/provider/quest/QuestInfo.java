package kinoko.provider.quest;

import kinoko.provider.ProviderError;
import kinoko.provider.WzProvider;
import kinoko.provider.quest.act.*;
import kinoko.provider.quest.check.*;
import kinoko.provider.wz.property.WzListProperty;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class QuestInfo {
    private final int questId;
    private final int nextQuest;
    private final boolean autoStart;
    private final boolean autoComplete;
    private final Set<QuestAct> startActs;
    private final Set<QuestAct> completeActs;
    private final Set<QuestCheck> startChecks;
    private final Set<QuestCheck> completeChecks;

    public QuestInfo(int questId, int nextQuest, boolean autoStart, boolean autoComplete, Set<QuestAct> startActs, Set<QuestAct> completeActs,
                     Set<QuestCheck> startChecks, Set<QuestCheck> completeChecks) {
        this.questId = questId;
        this.nextQuest = nextQuest;
        this.autoStart = autoStart;
        this.autoComplete = autoComplete;
        this.startActs = startActs;
        this.completeActs = completeActs;
        this.startChecks = startChecks;
        this.completeChecks = completeChecks;
    }

    public int getQuestId() {
        return questId;
    }

    public int getNextQuest() {
        return nextQuest;
    }

    public boolean isAutoStart() {
        return autoStart;
    }

    public boolean isAutoComplete() {
        return autoComplete;
    }

    public Set<QuestAct> getStartActs() {
        return startActs;
    }

    public Set<QuestAct> getCompleteActs() {
        return completeActs;
    }

    public Set<QuestCheck> getStartChecks() {
        return startChecks;
    }

    public Set<QuestCheck> getCompleteChecks() {
        return completeChecks;
    }

    public boolean isAutoAlert() {
        return autoStart || autoComplete;
    }

    @Override
    public String toString() {
        return "QuestInfo[" +
                "id=" + questId + ", " +
                "nextQuest=" + nextQuest + ", " +
                "autoStart=" + autoStart + ", " +
                "autoComplete=" + autoComplete + ", " +
                "startActs=" + startActs + ", " +
                "completeActs=" + completeActs + ", " +
                "startChecks=" + startChecks + ", " +
                "completeChecks=" + completeChecks + ']';
    }

    public static QuestInfo from(int questId, WzListProperty questInfo, WzListProperty questAct, WzListProperty questCheck) throws ProviderError {
        boolean autoStart = false;
        boolean autoComplete = false;
        for (var infoEntry : questInfo.getItems().entrySet()) {
            switch (infoEntry.getKey()) {
                case "autoStart" -> {
                    autoStart = (int) infoEntry.getValue() != 0;
                }
                case "autoComplete" -> {
                    autoComplete = (int) infoEntry.getValue() != 0;
                }
            }
        }
        // extract nextQuest from Act.img/%d/1
        final int nextQuest = WzProvider.getInteger(((WzListProperty) questAct.get("1")).getItems().get("nextQuest"), 0);
        return new QuestInfo(
                questId,
                nextQuest,
                autoStart,
                autoComplete,
                Collections.unmodifiableSet(resolveQuestActs(questAct.get("0"))),
                Collections.unmodifiableSet(resolveQuestActs(questAct.get("1"))),
                Collections.unmodifiableSet(resolveQuestChecks(questId, questCheck.get("0"))),
                Collections.unmodifiableSet(resolveQuestChecks(questId, questCheck.get("1")))
        );
    }

    private static Set<QuestAct> resolveQuestActs(WzListProperty actProps) {
        final Set<QuestAct> questActs = new HashSet<>();
        for (var entry : actProps.getItems().entrySet()) {
            final String actType = entry.getKey();
            switch (actType) {
                case "item" -> {
                    if (!(entry.getValue() instanceof WzListProperty itemList)) {
                        throw new ProviderError("Failed to resolve quest act item list");
                    }
                    questActs.add(QuestItemAct.from(itemList));
                }
                case "money" -> {
                    questActs.add(new QuestMoneyAct(WzProvider.getInteger(entry.getValue())));
                }
                case "exp" -> {
                    questActs.add(new QuestExpAct(WzProvider.getInteger(entry.getValue())));
                }
                case "pop" -> {
                    questActs.add(new QuestPopAct(WzProvider.getInteger(entry.getValue())));
                }
                case "skill" -> {
                    // TODO
                }
                case "nextQuest" -> {
                    // handled in QuestInfo.from
                }
            }
        }
        return questActs;
    }

    private static Set<QuestCheck> resolveQuestChecks(int questId, WzListProperty checkProps) {
        final Set<QuestCheck> questChecks = new HashSet<>();
        for (var entry : checkProps.getItems().entrySet()) {
            final String checkType = entry.getKey();
            switch (checkType) {
                case "item" -> {
                    if (!(entry.getValue() instanceof WzListProperty itemList)) {
                        throw new ProviderError("Failed to resolve quest check item list");
                    }
                    questChecks.add(QuestItemCheck.from(itemList));
                }
                case "mob" -> {
                    if (!(entry.getValue() instanceof WzListProperty mobList)) {
                        throw new ProviderError("Failed to resolve quest check mob list");
                    }
                    questChecks.add(QuestMobCheck.from(mobList));
                }
                case "job" -> {
                    if (!(entry.getValue() instanceof WzListProperty jobList)) {
                        throw new ProviderError("Failed to resolve quest check job list");
                    }
                    questChecks.add(QuestJobCheck.from(jobList));
                }
                case "lvmin", "lvmax" -> {
                    final int level = WzProvider.getInteger(entry.getValue());
                    final boolean isMinimum = checkType.equals("lvmin");
                    questChecks.add(new QuestLevelCheck(level, isMinimum));
                }
                case "infoex", "infoNumber" -> {
                    // TODO
                }
            }
        }
        return questChecks;
    }

}
