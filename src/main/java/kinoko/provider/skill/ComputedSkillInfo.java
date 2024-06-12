package kinoko.provider.skill;

import kinoko.provider.ProviderError;
import kinoko.provider.WzProvider;
import kinoko.provider.wz.property.WzListProperty;
import kinoko.util.Rect;
import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;
import net.objecthunter.exp4j.function.Function;

import java.util.*;

public final class ComputedSkillInfo implements SkillInfo {
    private static final Function ceil = new Function("u", 1) {
        @Override
        public double apply(double... doubles) {
            return Math.ceil(doubles[0]);
        }
    };
    private static final Function floor = new Function("d", 1) {
        @Override
        public double apply(double... doubles) {
            return Math.floor(doubles[0]);
        }
    };

    private final int id;
    private final int maxLevel;
    private final boolean invisible;
    private final boolean psd;
    private final List<Integer> psdSkills;
    private final Map<SkillStat, Expression> stats;
    private final Map<SkillStat, String> strings;
    private final Rect rect;
    private final ElementAttribute elemAttr;

    public ComputedSkillInfo(int id, int maxLevel, boolean invisible, boolean psd, List<Integer> psdSkills, Map<SkillStat, Expression> stats, Map<SkillStat, String> strings, Rect rect, ElementAttribute elemAttr) {
        this.id = id;
        this.maxLevel = maxLevel;
        this.invisible = invisible;
        this.psd = psd;
        this.psdSkills = psdSkills;
        this.stats = stats;
        this.strings = strings;
        this.rect = rect;
        this.elemAttr = elemAttr;
    }

    public Map<SkillStat, Expression> getStats() {
        return stats;
    }

    public Map<SkillStat, String> getStrings() {
        return strings;
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
        return (int) stats.get(stat)
                .setVariable("x", slv)
                .evaluate();
    }

    @Override
    public Rect getRect() {
        return rect;
    }

    @Override
    public ElementAttribute getElementAttribute() {
        return elemAttr;
    }

    @Override
    public String toString() {
        return "ComputedSkillInfo{" +
                "id=" + id +
                ", maxLevel=" + maxLevel +
                ", invisible=" + invisible +
                ", psd=" + psd +
                ", psdSkills=" + psdSkills +
                ", stats=" + stats +
                ", strings=" + strings +
                ", rect=" + rect +
                ", elemAttr=" + elemAttr +
                '}';
    }

    public static ComputedSkillInfo from(int skillId, WzListProperty skillProp) throws ProviderError {
        final Map<SkillStat, Expression> stats = new EnumMap<>(SkillStat.class);
        final Map<SkillStat, String> strings = new EnumMap<>(SkillStat.class);
        Rect rect = null;
        int maxLevel = 0;
        if (skillProp.get("common") instanceof WzListProperty commonProps) {
            for (var entry : commonProps.getItems().entrySet()) {
                final SkillStat stat = SkillStat.fromName(entry.getKey());
                switch (stat) {
                    case maxLevel -> {
                        maxLevel = WzProvider.getInteger(entry.getValue());
                    }
                    case lt -> {
                        rect = WzProvider.getRect(commonProps);
                    }
                    case rb, hs, hit, ball, action, dateExpire -> {
                        // skip; rb is handled by lt
                    }
                    default -> {
                        final String expressionString = WzProvider.getString(entry.getValue());
                        final Expression expression = new ExpressionBuilder(expressionString)
                                .functions(ceil, floor)
                                .variables("x")
                                .build();
                        stats.put(stat, expression);
                        strings.put(stat, expressionString);
                    }
                }
            }
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
        return new ComputedSkillInfo(
                skillId,
                maxLevel,
                WzProvider.getInteger(skillProp.get("invisible"), 0) != 0,
                WzProvider.getInteger(skillProp.get("psd"), 0) != 0,
                Collections.unmodifiableList(psdSkills),
                Collections.unmodifiableMap(stats),
                Collections.unmodifiableMap(strings),
                rect,
                elemAttr
        );
    }
}
