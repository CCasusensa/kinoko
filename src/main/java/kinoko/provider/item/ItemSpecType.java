package kinoko.provider.item;

import java.util.HashMap;
import java.util.Map;

public enum ItemSpecType {
    // HEAL ------------------------------------------------------------------------------------------------------------
    hp,
    mp,
    hpR,
    mpR,
    inc, // Pet Food
    incFatigue, // Revitalizer (TamedMob)
    seal,
    curse,
    weakness,
    poison,
    darkness,

    // BUFF ------------------------------------------------------------------------------------------------------------
    time,
    str,
    dex,
    int_,
    luk,
    mhpR,
    mmpR,
    pad,
    mad,
    pdd,
    mdd,
    acc,
    eva,
    speed,
    jump,
    booster,
    exp,
    expinc,
    expBuff,
    itemupbyitem,
    mesoupbyitem,

    mhpRRate,
    mmpRRate,
    padRate,
    madRate,
    pddRate,
    mddRate,
    accRate,
    evaRate,
    speedRate,
    eventRate,
    eventPoint,
    barrier,
    berserk,

    // SCRIPT ----------------------------------------------------------------------------------------------------------
    npc,
    script,
    moveTo,
    ignoreContinent,
    returnMapQR,
    onlyPickup,
    runOnPickup,
    consumeOnPickup,
    repeatEffect,
    uiNumber,
    morph,
    morphRandom,
    ghost,
    screenMsg,
    randomMoveInFieldSet,

    cp,
    party,
    otherParty,
    nuffSkill, // One of the members in the opposing party will have their buffs nullified
    respectFS,
    respectPimmune,
    respectMimmune,
    defenseAtt,
    defenseState,
    con,
    thaw,
    prob,
    itemCode,
    itemRange,
    mob,
    mobID,
    mobHp,
    attackMobID,
    attackIndex,
    dojangshield,
    BFSkill;

    private static final Map<String, ItemSpecType> nameMap;

    static {
        nameMap = new HashMap<>();
        for (ItemSpecType type : values()) {
            nameMap.put(type == int_ ? "int" : type.name(), type);
        }
    }

    public static ItemSpecType fromName(String name) {
        return nameMap.get(name);
    }
}
