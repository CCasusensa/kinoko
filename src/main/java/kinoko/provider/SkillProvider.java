package kinoko.provider;

import kinoko.provider.skill.SkillInfo;
import kinoko.provider.skill.SummonedAttackInfo;
import kinoko.provider.wz.WzConstants;
import kinoko.provider.wz.WzPackage;
import kinoko.provider.wz.WzReader;
import kinoko.provider.wz.WzReaderConfig;
import kinoko.provider.wz.property.WzListProperty;
import kinoko.server.ServerConfig;
import kinoko.server.ServerConstants;
import kinoko.world.job.Job;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

public final class SkillProvider implements WzProvider {
    public static final Path SKILL_WZ = Path.of(ServerConfig.WZ_DIRECTORY, "Skill.wz");
    private static final Map<Job, Set<SkillInfo>> jobSkills = new EnumMap<>(Job.class);
    private static final Map<Integer, SkillInfo> mobSkills = new HashMap<>();
    private static final Map<Integer, SkillInfo> skillInfos = new HashMap<>();
    private static final Map<Integer, SummonedAttackInfo> summonedAttackInfos = new HashMap<>();

    public static void initialize() {
        try (final WzReader reader = WzReader.build(SKILL_WZ, new WzReaderConfig(WzConstants.WZ_GMS_IV, ServerConstants.GAME_VERSION))) {
            final WzPackage wzPackage = reader.readPackage();
            loadSkillInfos(wzPackage);
            loadMobSkills(wzPackage);
        } catch (IOException | ProviderError e) {
            throw new IllegalArgumentException("Exception caught while loading Skill.wz", e);
        }
    }

    public static Set<SkillInfo> getSkillsForJob(Job job) {
        return jobSkills.getOrDefault(job, Set.of());
    }

    public static Optional<SkillInfo> getMobSkillInfoById(int skillId) {
        return Optional.ofNullable(mobSkills.get(skillId));
    }

    public static Optional<SkillInfo> getSkillInfoById(int skillId) {
        return Optional.ofNullable(skillInfos.get(skillId));
    }

    public static Optional<SummonedAttackInfo> getSummonedAttackInfo(int skillId) {
        return Optional.ofNullable(summonedAttackInfos.get(skillId));
    }

    private static void loadSkillInfos(WzPackage source) throws ProviderError {
        for (var imageEntry : source.getDirectory().getImages().entrySet()) {
            final String imageName = imageEntry.getKey().replace(".img", "");
            if (!imageName.matches("[0-9]+")) {
                continue;
            }
            final short jobId = Short.parseShort(imageName);
            final Job job = Job.getById(jobId);
            if (!(imageEntry.getValue().getProperty().get("skill") instanceof WzListProperty skillList)) {
                throw new ProviderError("Failed to resolve skills for job ID : %d", jobId);
            }
            for (var skillEntry : skillList.getItems().entrySet()) {
                final int skillId = Integer.parseInt(skillEntry.getKey());
                if (!(skillEntry.getValue() instanceof WzListProperty skillProp)) {
                    throw new ProviderError("Failed to resolve item property");
                }
                final SkillInfo skillInfo = SkillInfo.from(skillId, skillProp);

                if (!jobSkills.containsKey(job)) {
                    jobSkills.put(job, new HashSet<>());
                }
                jobSkills.get(job).add(skillInfo);
                skillInfos.put(skillId, skillInfo);

                // Handle summon attack info
                if (!(skillProp.get("summon") instanceof WzListProperty summonProp)) {
                    continue;
                }
                for (var summonEntry : summonProp.getItems().entrySet()) {
                    if (!summonEntry.getKey().startsWith("attack") ||
                            !(summonEntry.getValue() instanceof WzListProperty attackProp)) {
                        continue;
                    }
                    summonedAttackInfos.put(skillId, SummonedAttackInfo.from(skillId, attackProp));
                }
            }
        }
    }

    private static void loadMobSkills(WzPackage source) throws ProviderError {
        if (!source.getDirectory().getImages().containsKey("MobSkill.img")) {
            throw new ProviderError("Failed to resolve MobSkill.img");
        }
        for (var entry : source.getDirectory().getImages().get("MobSkill.img").getProperty().getItems().entrySet()) {
            if (!(entry.getValue() instanceof WzListProperty skillProp)) {
                throw new ProviderError("Failed to resolve mob skill property");
            }
            final int skillId = Integer.parseInt(entry.getKey());
            mobSkills.put(skillId, SkillInfo.from(skillId, skillProp));
        }
    }
}
