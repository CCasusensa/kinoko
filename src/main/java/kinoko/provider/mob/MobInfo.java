package kinoko.provider.mob;

import kinoko.provider.ProviderError;
import kinoko.provider.WzProvider;
import kinoko.provider.wz.property.WzListProperty;

import java.util.HashMap;
import java.util.Map;

public final class MobInfo {
    private final int templateId;
    private final int level;
    private final int maxHp;
    private final int maxMp;
    private final int hpRecovery;
    private final int mpRecovery;
    private final boolean boss;
    private final Map<Integer, MobAttack> attacks;
    private final Map<Integer, MobSkill> skills;

    public MobInfo(int templateId, int level, int maxHp, int maxMp, int hpRecovery, int mpRecovery, boolean boss, Map<Integer, MobAttack> attacks, Map<Integer, MobSkill> skills) {
        // TODO linked mobs
        this.templateId = templateId;
        this.level = level;
        this.maxHp = maxHp;
        this.maxMp = maxMp;
        this.hpRecovery = hpRecovery;
        this.mpRecovery = mpRecovery;
        this.boss = boss;
        this.attacks = attacks;
        this.skills = skills;
    }

    public int getTemplateId() {
        return templateId;
    }

    public int getLevel() {
        return level;
    }

    public int getMaxHp() {
        return maxHp;
    }

    public int getMaxMp() {
        return maxMp;
    }

    public int getHpRecovery() {
        return hpRecovery;
    }

    public int getMpRecovery() {
        return mpRecovery;
    }

    public boolean isBoss() {
        return boss;
    }

    public Map<Integer, MobAttack> getAttacks() {
        return attacks;
    }

    public Map<Integer, MobSkill> getSkills() {
        return skills;
    }

    @Override
    public String toString() {
        return "MobInfo[" +
                "id=" + templateId + ", " +
                "level=" + level + ", " +
                "maxHp=" + maxHp + ", " +
                "maxMp=" + maxMp + ", " +
                "hpRecovery=" + hpRecovery + ", " +
                "mpRecovery=" + mpRecovery + ", " +
                "boss=" + boss + ']';
    }

    public static MobInfo from(int mobId, WzListProperty mobProperty) throws ProviderError {
        int level = 0;
        int maxHP = 0;
        int maxMP = 0;
        int hpRecovery = 0;
        int mpRecovery = 0;
        boolean boss = false;
        final Map<Integer, MobAttack> attacks = new HashMap<>();
        final Map<Integer, MobSkill> skills = new HashMap<>();
        for (var entry : mobProperty.getItems().entrySet()) {
            if (entry.getKey().equals("info")) {
                if (!(entry.getValue() instanceof WzListProperty infoProp)) {
                    throw new ProviderError("Failed to resolve info for mob : %d", mobId);
                }
                for (var infoEntry : infoProp.getItems().entrySet()) {
                    switch (infoEntry.getKey()) {
                        case "level" -> {
                            level = WzProvider.getInteger(infoEntry.getValue());
                        }
                        case "maxHP" -> {
                            maxHP = WzProvider.getInteger(infoEntry.getValue());
                        }
                        case "maxMP" -> {
                            maxMP = WzProvider.getInteger(infoEntry.getValue());
                        }
                        case "hpRecovery" -> {
                            hpRecovery = WzProvider.getInteger(infoEntry.getValue());
                        }
                        case "mpRecovery" -> {
                            mpRecovery = WzProvider.getInteger(infoEntry.getValue());
                        }
                        case "boss" -> {
                            boss = WzProvider.getInteger(infoEntry.getValue()) != 0;
                        }
                        case "skill" -> {
                            if (!(infoEntry.getValue() instanceof WzListProperty skillEntries)) {
                                throw new ProviderError("Failed to resolve mob skills for mob : %d", mobId);
                            }
                            for (var skillEntry : skillEntries.getItems().entrySet()) {
                                final int skillIndex = Integer.parseInt(skillEntry.getKey());
                                if (!(skillEntry.getValue() instanceof WzListProperty skillProp)) {
                                    throw new ProviderError("Failed to resolve mob skills for mob : %d", mobId);
                                }
                                final int skillId = WzProvider.getInteger(skillProp.get("skill"));
                                final MobSkillType type = MobSkillType.getByValue(skillId);
                                if (type == null) {
                                    throw new ProviderError("Failed to resolve mob skill : %d", skillId);
                                }
                                skills.put(skillIndex, new MobSkill(
                                        skillId,
                                        WzProvider.getInteger(skillProp.get("level"))
                                ));
                            }
                        }
                        default -> {
                            // System.err.printf("Unhandled info %s in mob %d%n", infoEntry.getKey(), mobId);
                        }
                    }
                }
            } else if (entry.getKey().startsWith("attack")) {
                final int attackIndex = Integer.parseInt(entry.getKey().replace("attack", "")) - 1;
                if (!(entry.getValue() instanceof WzListProperty attackProp) ||
                        !(attackProp.get("info") instanceof WzListProperty infoProp)) {
                    throw new ProviderError("Failed to resolve attack info for mob : %d", mobId);
                }
                int skillId = 0;
                int skillLevel = 0;
                int conMp = 0;
                for (var infoEntry : infoProp.getItems().entrySet()) {
                    switch (infoEntry.getKey()) {
                        case "disease" -> {
                            skillId = WzProvider.getInteger(infoEntry.getValue());
                        }
                        case "level" -> {
                            skillLevel = WzProvider.getInteger(infoEntry.getValue());
                        }
                        case "conMP" -> {
                            conMp = WzProvider.getInteger(infoEntry.getValue());
                        }
                        default -> {
                            // System.err.printf("Unhandled mob attack info %s in mob %d%n", infoEntry.getKey(), mobId);
                        }
                    }
                }
                attacks.put(attackIndex, new MobAttack(
                        skillId,
                        skillLevel,
                        conMp
                ));
            }
        }
        return new MobInfo(mobId, level, maxHP, maxMP, hpRecovery, mpRecovery, boss, attacks, skills);
    }
}
