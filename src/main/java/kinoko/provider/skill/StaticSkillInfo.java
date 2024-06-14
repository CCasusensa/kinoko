package kinoko.provider.skill;

import kinoko.provider.ProviderError;
import kinoko.provider.WzProvider;
import kinoko.provider.wz.property.WzListProperty;
import kinoko.util.Rect;

import java.util.*;

public final class StaticSkillInfo implements SkillInfo {
    private final int id;
    private final int maxLevel;
    private final boolean invisible;
    private final boolean combatOrders;
    private final boolean psd;
    private final List<Integer> psdSkills;
    private final Map<SkillStat, List<Integer>> stats;
    private final Rect rect;
    private final ElementAttribute elemAttr;

    public StaticSkillInfo(int id, int maxLevel, boolean invisible, boolean combatOrders, boolean psd, List<Integer> psdSkills, Map<SkillStat, List<Integer>> stats, Rect rect, ElementAttribute elemAttr) {
        this.id = id;
        this.maxLevel = maxLevel;
        this.invisible = invisible;
        this.combatOrders = combatOrders;
        this.psd = psd;
        this.psdSkills = psdSkills;
        this.stats = stats;
        this.rect = rect;
        this.elemAttr = elemAttr;
    }

    public Map<SkillStat, List<Integer>> getStats() {
        return stats;
    }

    @Override
    public int getSkillId() {
        return id;
    }

    @Override
    public int getMaxLevel() {
        return maxLevel;
    }

    @Override
    public boolean isInvisible() {
        return invisible;
    }

    @Override
    public boolean isCombatOrders() {
        return combatOrders;
    }

    @Override
    public boolean isPsd() {
        return psd;
    }

    @Override
    public List<Integer> getPsdSkills() {
        return psdSkills;
    }

    @Override
    public int getValue(SkillStat stat, int slv) {
        if (!stats.containsKey(stat)) {
            return 0;
        }
        return stats.get(stat).get(slv - 1);
    }

    @Override
    public Rect getRect() {
        return rect;
    }

    @Override
    public ElementAttribute getElemAttr() {
        return elemAttr;
    }

    @Override
    public String toString() {
        return "StaticSkillInfo{" +
                "id=" + id +
                ", maxLevel=" + maxLevel +
                ", invisible=" + invisible +
                ", combatOrders=" + combatOrders +
                ", psd=" + psd +
                ", psdSkills=" + psdSkills +
                ", stats=" + stats +
                ", rect=" + rect +
                ", elemAttr=" + elemAttr +
                '}';
    }

    public static StaticSkillInfo from(int skillId, WzListProperty skillProp) throws ProviderError {
        final Map<SkillStat, List<Integer>> stats = new EnumMap<>(SkillStat.class);
        int maxLevel = 0;
        Rect rect = null;
        if (skillProp.get("level") instanceof WzListProperty levelProps) {
            for (int slv = 1; slv < Integer.MAX_VALUE; slv++) {
                if (!(levelProps.get(String.valueOf(slv)) instanceof WzListProperty statProp)) {
                    maxLevel = slv - 1;
                    break;
                }
                for (var entry : statProp.getItems().entrySet()) {
                    final SkillStat stat = SkillStat.fromName(entry.getKey());
                    if (stat == null) {
                        // unhandled SkillStats in MobSkill.img
                        continue;
                    }
                    switch (stat) {
                        case maxLevel -> {
                            maxLevel = WzProvider.getInteger(entry.getValue());
                        }
                        case lt -> {
                            rect = WzProvider.getRect(statProp);
                        }
                        case rb, hs, hit, ball, action, dateExpire -> {
                            // skip; rb is handled by lt
                        }
                        default -> {
                            if (!stats.containsKey(stat)) {
                                stats.put(stat, new ArrayList<>());
                            }
                            stats.get(stat).add(WzProvider.getInteger(entry.getValue()));
                        }
                    }
                }
            }
        }
        if (maxLevel == 0) {
            throw new ProviderError("Could not resolve static skill info");
        }
        final List<Integer> psdSkills = new ArrayList<>();
        if (skillProp.get("psdSkill") instanceof WzListProperty psdProp) {
            for (var entry : psdProp.getItems().entrySet()) {
                psdSkills.add(Integer.parseInt(entry.getKey()));
            }
        }
        final ElementAttribute elemAttr;
        final String elemAttrString = skillProp.get("elemAttr");
        if (elemAttrString != null) {
            if (elemAttrString.length() != 1) {
                throw new ProviderError("Failed to resolve skill element attribute");
            }
            elemAttr = ElementAttribute.getByValue(elemAttrString.charAt(0));
        } else {
            elemAttr = ElementAttribute.PHYSICAL;
        }
        return new StaticSkillInfo(
                skillId,
                maxLevel,
                WzProvider.getInteger(skillProp.get("invisible"), 0) != 0,
                WzProvider.getInteger(skillProp.get("combatOrders"), 0) != 0,
                WzProvider.getInteger(skillProp.get("psd"), 0) != 0,
                Collections.unmodifiableList(psdSkills),
                stats,
                rect,
                elemAttr
        );
    }
}
