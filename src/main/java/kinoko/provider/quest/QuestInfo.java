package kinoko.provider.quest;

import kinoko.provider.ProviderError;
import kinoko.provider.WzProvider;
import kinoko.provider.quest.act.*;
import kinoko.provider.quest.check.*;
import kinoko.provider.wz.property.WzListProperty;
import kinoko.util.Locked;
import kinoko.util.Tuple;
import kinoko.util.Util;
import kinoko.world.quest.QuestManager;
import kinoko.world.quest.QuestRecord;
import kinoko.world.quest.QuestState;
import kinoko.world.user.User;

import java.util.*;
import java.util.stream.Collectors;

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

    public Optional<QuestRecord> startQuest(Locked<User> locked) {
        // Check that the quest can be started
        for (QuestCheck startCheck : getStartChecks()) {
            if (!startCheck.check(locked)) {
                return Optional.empty();
            }
        }
        for (QuestAct startAct : getStartActs()) {
            if (!startAct.canAct(locked)) {
                return Optional.empty();
            }
        }
        // Perform start acts
        for (QuestAct startAct : getStartActs()) {
            if (!startAct.doAct(locked)) {
                throw new IllegalStateException("Failed to perform quest start act");
            }
        }
        // Add quest record and return
        return Optional.of(locked.get().getQuestManager().forceStartQuest(questId));
    }

    public Optional<Tuple<QuestRecord, Integer>> completeQuest(Locked<User> locked) {
        // Check that the quest has been started
        final QuestManager qm = locked.get().getQuestManager();
        if (!qm.hasQuestStarted(questId)) {
            return Optional.empty();
        }
        // Check that the quest can be completed
        for (QuestCheck completeCheck : getCompleteChecks()) {
            if (!completeCheck.check(locked)) {
                return Optional.empty();
            }
        }
        for (QuestAct completeAct : getCompleteActs()) {
            if (!completeAct.canAct(locked)) {
                return Optional.empty();
            }
        }
        // Perform complete acts
        for (QuestAct completeAct : getCompleteActs()) {
            if (!completeAct.doAct(locked)) {
                throw new IllegalStateException("Failed to perform quest complete act");
            }
        }
        // Mark as completed and return
        final QuestRecord qr = qm.forceCompleteQuest(questId);
        return Optional.of(new Tuple<>(qr, getNextQuest()));
    }

    public Optional<QuestRecord> resignQuest(Locked<User> locked) {
        final QuestManager qm = locked.get().getQuestManager();
        final Optional<QuestRecord> questRecordResult = qm.getQuestRecord(questId);
        if (questRecordResult.isEmpty() || questRecordResult.get().getState() != QuestState.PERFORM) {
            return Optional.empty();
        }
        final Optional<QuestRecord> removeQuestRecordResult = qm.removeQuestRecord(questId);
        if (removeQuestRecordResult.isEmpty()) {
            return Optional.empty();
        }
        final QuestRecord qr = removeQuestRecordResult.get();
        qr.setState(QuestState.NONE);
        // TODO: resign remove
        return Optional.of(qr);
    }

    public Optional<QuestRecord> progressQuest(QuestRecord questRecord, int mobId) {
        // Check that the quest has been started
        if (questRecord.getState() != QuestState.PERFORM) {
            return Optional.empty();
        }
        // Check if the quest is relevant for the mob
        final Optional<QuestCheck> mobCheckResult = getCompleteChecks().stream()
                .filter((check) -> check instanceof QuestMobCheck)
                .findFirst();
        if (mobCheckResult.isEmpty()) {
            return Optional.empty();
        }
        final QuestMobCheck mobCheck = (QuestMobCheck) mobCheckResult.get();
        if (mobCheck.getMobs().stream().noneMatch((mobData) -> mobData.getMobId() == mobId)) {
            return Optional.empty();
        }
        // Get current progress
        final int[] progress = new int[mobCheck.getMobs().size()];
        final String qrValue = questRecord.getValue();
        if (qrValue != null && !qrValue.isEmpty()) {
            // Split qrValue string every 3 characters to get current mob count
            for (int c = 0; c < qrValue.length(); c += 3) {
                final int countIndex = c / 3;
                if (countIndex >= progress.length) {
                    break;
                }
                final String countValue = qrValue.substring(c, Math.min(c + 3, qrValue.length()));
                if (!Util.isInteger(countValue)) {
                    continue;
                }
                progress[countIndex] = Integer.parseInt(countValue);
            }
        }
        // Increment progress
        for (int i = 0; i < mobCheck.getMobs().size(); i++) {
            final QuestMobData mobData = mobCheck.getMobs().get(i);
            if (mobData.getMobId() == mobId) {
                progress[i] = Math.min(progress[i] + 1, mobData.getCount());
            }
        }
        // Check if quest record needs to be updated
        final String newQrValue = Arrays.stream(progress)
                .mapToObj((count) -> String.format("%03d", Math.min(count, 999)))
                .collect(Collectors.joining());
        if (newQrValue.equals(questRecord.getValue())) {
            return Optional.empty();
        }
        // Update quest record and return
        questRecord.setValue(newQrValue);
        return Optional.of(questRecord);
    }

    public boolean hasRequiredItem(User user, int itemId) {
        for (QuestCheck check : getCompleteChecks()) {
            if (!(check instanceof QuestItemCheck itemCheck)) {
                continue;
            }
            for (QuestItemData itemData : itemCheck.getItems()) {
                if (itemData.getItemId() == itemId && user.getInventoryManager().hasItem(itemId, itemData.getCount())) {
                    return true;
                }
            }
        }
        return false;
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
                Collections.unmodifiableSet(resolveQuestActs(questId, questAct.get("0"))),
                Collections.unmodifiableSet(resolveQuestActs(questId, questAct.get("1"))),
                Collections.unmodifiableSet(resolveQuestChecks(questId, questCheck.get("0"))),
                Collections.unmodifiableSet(resolveQuestChecks(questId, questCheck.get("1")))
        );
    }

    private static Set<QuestAct> resolveQuestActs(int questId, WzListProperty actProps) {
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
                    if (!(entry.getValue() instanceof WzListProperty skillList)) {
                        throw new ProviderError("Failed to resolve quest act skill list");
                    }
                    if (questId == 6034) {
                        // What Moren Dropped
                        continue;
                    }
                    questActs.add(QuestSkillAct.from(skillList));
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
                    questChecks.add(QuestMobCheck.from(questId, mobList));
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
                case "infoex" -> {
                    final int infoQuestId = WzProvider.getInteger(checkProps.get("infoNumber"), questId);
                    if (!(entry.getValue() instanceof WzListProperty exList)) {
                        throw new ProviderError("Failed to resolve quest check ex list");
                    }
                    questChecks.add(QuestExCheck.from(infoQuestId, exList));
                }
            }
        }
        return questChecks;
    }
}
