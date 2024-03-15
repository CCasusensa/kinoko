package kinoko.provider.quest.act;

import kinoko.packet.world.WvsContext;
import kinoko.provider.SkillProvider;
import kinoko.provider.quest.QuestSkillData;
import kinoko.provider.skill.SkillInfo;
import kinoko.provider.wz.property.WzListProperty;
import kinoko.util.Locked;
import kinoko.world.skill.SkillRecord;
import kinoko.world.user.User;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;

public final class QuestSkillAct implements QuestAct {
    private final Set<QuestSkillData> skills;

    public QuestSkillAct(Set<QuestSkillData> skills) {
        this.skills = skills;
    }

    @Override
    public boolean canAct(Locked<User> locked) {
        return true;
    }

    @Override
    public boolean doAct(Locked<User> locked) {
        final User user = locked.get();
        for (QuestSkillData qsd : skills) {
            if (!qsd.getJobs().contains(user.getJob())) {
                continue;
            }
            final Optional<SkillInfo> skillInfoResult = SkillProvider.getSkillInfoById(qsd.getSkillId());
            if (skillInfoResult.isEmpty()) {
                return false;
            }
            final SkillRecord skillRecord = skillInfoResult.get().createRecord();
            skillRecord.setSkillLevel(qsd.getSkillLevel());
            skillRecord.setMasterLevel(qsd.getMasterLevel());
            user.getSkillManager().addSkill(skillRecord);
            user.updatePassiveSkillData();
            user.validateStat();
            user.write(WvsContext.changeSkillRecordResult(skillRecord, true));
        }
        return true;
    }

    public static QuestSkillAct from(WzListProperty skillList) {
        final Set<QuestSkillData> skills = QuestSkillData.resolveSkillData(skillList);
        return new QuestSkillAct(
                Collections.unmodifiableSet(skills)
        );
    }
}
