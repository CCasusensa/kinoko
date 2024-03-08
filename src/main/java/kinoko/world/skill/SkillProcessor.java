package kinoko.world.skill;

import kinoko.packet.user.UserLocal;
import kinoko.packet.user.UserRemote;
import kinoko.packet.world.WvsContext;
import kinoko.provider.MobProvider;
import kinoko.provider.SkillProvider;
import kinoko.provider.mob.MobTemplate;
import kinoko.provider.skill.SkillInfo;
import kinoko.provider.skill.SkillStat;
import kinoko.server.packet.InPacket;
import kinoko.util.Util;
import kinoko.world.GameConstants;
import kinoko.world.field.Field;
import kinoko.world.field.mob.Mob;
import kinoko.world.item.*;
import kinoko.world.job.JobHandler;
import kinoko.world.job.explorer.Thief;
import kinoko.world.job.explorer.Warrior;
import kinoko.world.job.legend.Aran;
import kinoko.world.user.User;
import kinoko.world.user.stat.CharacterTemporaryStat;
import kinoko.world.user.stat.SecondaryStat;
import kinoko.world.user.stat.Stat;
import kinoko.world.user.stat.TemporaryStatOption;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class SkillProcessor {
    private static final Logger log = LogManager.getLogger(SkillProcessor.class);

    public static void processAttack(User user, Attack attack) {
        // Resolve bullet id
        if (attack.bulletPosition != 0 && !attack.isSoulArrow() && !attack.isSpiritJavelin()) {
            final Item weaponItem = user.getInventoryManager().getEquipped().getItem(BodyPart.WEAPON.getValue());
            final Item bulletItem = user.getInventoryManager().getConsumeInventory().getItem(attack.bulletPosition);
            if (weaponItem == null || bulletItem == null || !ItemConstants.isCorrectBulletItem(weaponItem.getItemId(), bulletItem.getItemId())) {
                log.error("Tried to attack with incorrect bullet {} using weapon {}", bulletItem != null ? bulletItem.getItemId() : 0, weaponItem != null ? weaponItem.getItemId() : 0);
                return;
            }
            attack.bulletItemId = bulletItem.getItemId();
            // Consume bullet for basic attack
            if (attack.skillId == 0) {
                final int bulletCount = attack.isShadowPartner() ? 2 : 1;
                if (bulletItem.getQuantity() < bulletCount) {
                    log.error("Tried to attack without enough bullets in position {}", attack.bulletPosition);
                    return;
                }
                bulletItem.setQuantity((short) (bulletItem.getQuantity() - bulletCount));
                user.write(WvsContext.inventoryOperation(InventoryOperation.itemNumber(InventoryType.CONSUME, attack.bulletPosition, bulletItem.getQuantity()), true));
            }
        }

        // Process skill
        if (attack.skillId != 0) {
            // Set skill level
            attack.slv = user.getSkillManager().getSkillLevel(attack.skillId);
            if (attack.slv == 0) {
                log.error("Tried to attack with skill {} not learned by user", attack.skillId);
                return;
            }
            // Resolve skill info
            final Optional<SkillInfo> skillInfoResult = SkillProvider.getSkillInfoById(attack.skillId);
            if (skillInfoResult.isEmpty()) {
                log.error("Failed to resolve skill info for skill : {}", attack.skillId);
                return;
            }
            final SkillInfo si = skillInfoResult.get();
            // Check skill cooltime and cost
            final int cooltime = si.getValue(SkillStat.cooltime, attack.slv);
            if (cooltime > 0 && user.getSkillManager().hasSkillCooltime(attack.skillId)) {
                log.error("Tried to use skill {} that is still on cooltime", attack.skillId);
                return;
            }
            final int hpCon = si.getValue(SkillStat.hpCon, attack.slv);
            if (user.getHp() <= hpCon) {
                log.error("Tried to use skill {} without enough hp, current : {}, required : {}", attack.skillId, user.getHp(), hpCon);
                return;
            }
            final int mpCon = si.getValue(SkillStat.mpCon, attack.slv);
            if (user.getMp() < mpCon) {
                log.error("Tried to use skill {} without enough mp, current : {}, required : {}", attack.skillId, user.getMp(), mpCon);
                return;
            }
            final int bulletCon = si.getBulletCon(attack.slv);
            if (bulletCon > 0 && attack.bulletPosition != 0 && !attack.isSoulArrow() && !attack.isSpiritJavelin()) {
                // Resolve bullet item
                final int bulletCount = bulletCon * (attack.isShadowPartner() ? 2 : 1);
                final Item bulletItem = user.getInventoryManager().getConsumeInventory().getItem(attack.bulletPosition);
                if (bulletItem == null || bulletItem.getQuantity() < bulletCount) {
                    log.error("Tried to use skill {} without enough bullets", attack.skillId);
                    return;
                }
                // Consume bullets
                bulletItem.setQuantity((short) (bulletItem.getQuantity() - bulletCount));
                user.write(WvsContext.inventoryOperation(InventoryOperation.itemNumber(InventoryType.CONSUME, attack.bulletPosition, bulletItem.getQuantity()), true));
            }
            // Consume hp/mp
            user.addHp(-hpCon);
            user.addMp(-mpCon);
            // Set cooltime
            if (cooltime > 0) {
                user.getSkillManager().setSkillCooltime(attack.skillId, Instant.now().plus(cooltime, ChronoUnit.SECONDS));
                user.write(UserLocal.skillCooltimeSet(attack.skillId, cooltime));
            }
        }

        // CTS updates on attack
        final SkillManager sm = user.getSkillManager();
        final Map<CharacterTemporaryStat, TemporaryStatOption> updateStats = new HashMap<>();
        for (var entry : user.getSecondaryStat().getTemporaryStats().entrySet()) {
            final CharacterTemporaryStat cts = entry.getKey();
            final TemporaryStatOption option = entry.getValue();
            switch (cts) {
                case ComboCounter -> {
                    if (attack.getMobCount() > 0) {
                        final int maxCombo = 1 + Math.max(
                                sm.getSkillStatValue(Warrior.COMBO_ATTACK, SkillStat.x),
                                sm.getSkillStatValue(Warrior.ADVANCED_COMBO_ATTACK, SkillStat.x)
                        );
                        final int doubleProp = sm.getSkillStatValue(Warrior.ADVANCED_COMBO_ATTACK, SkillStat.prop);
                        final int newCombo = Math.min(option.nOption + (Util.succeedProp(doubleProp) ? 2 : 1), maxCombo);
                        if (newCombo > option.nOption) {
                            updateStats.put(cts, option.update(newCombo));
                        }
                    }
                }
            }
        }
        if (!updateStats.isEmpty()) {
            user.setTemporaryStat(updateStats);
        }

        // Skill specific handling
        JobHandler.handleAttack(user, attack);

        // Process attack damage
        final Field field = user.getField();
        for (AttackInfo ai : attack.getAttackInfo()) {
            final Optional<Mob> mobResult = field.getMobPool().getById(ai.mobId);
            if (mobResult.isEmpty()) {
                continue;
            }
            // Acquire and damage mob
            final int totalDamage = Arrays.stream(ai.damage).sum();
            try (var lockedMob = mobResult.get().acquire()) {
                lockedMob.get().damage(user, totalDamage);
            }
        }

        field.broadcastPacket(UserRemote.attack(user, attack), user);
    }

    public static void processSkill(User user, Skill skill, InPacket inPacket) {
        // Resolve skill info
        final Optional<SkillInfo> skillInfoResult = SkillProvider.getSkillInfoById(skill.skillId);
        if (skillInfoResult.isEmpty()) {
            log.error("Failed to resolve skill info for skill : {}", skill.skillId);
            return;
        }
        final SkillInfo si = skillInfoResult.get();

        // Check skill cooltime and cost
        final int cooltime = si.getValue(SkillStat.cooltime, skill.slv);
        if (cooltime > 0 && user.getSkillManager().hasSkillCooltime(skill.skillId)) {
            log.error("Tried to use skill {} that is still on cooltime", skill.skillId);
            return;
        }
        final int hpCon = si.getValue(SkillStat.hpCon, skill.slv);
        if (user.getHp() <= hpCon) {
            log.error("Tried to use skill {} without enough hp, current : {}, required : {}", skill.skillId, user.getHp(), hpCon);
            return;
        }
        final int mpCon = si.getValue(SkillStat.mpCon, skill.slv);
        if (user.getMp() < mpCon) {
            log.error("Tried to use skill {} without enough mp, current : {}, required : {}", skill.skillId, user.getMp(), mpCon);
            return;
        }
        final int bulletCon = si.getBulletCon(skill.slv);
        if (bulletCon > 0) {
            // Resolve bullet item
            final Item weaponItem = user.getInventoryManager().getEquipped().getItem(BodyPart.WEAPON.getValue());
            if (weaponItem == null) {
                log.error("Tried to use skill {} without a weapon", skill.skillId);
                return;
            }
            final Optional<Map.Entry<Integer, Item>> bulletEntryResult = user.getInventoryManager().getConsumeInventory().getItems().entrySet().stream()
                    .filter((entry) -> ItemConstants.isCorrectBulletItem(weaponItem.getItemId(), entry.getValue().getItemId()) && entry.getValue().getQuantity() >= bulletCon)
                    .findFirst();
            if (bulletEntryResult.isEmpty()) {
                log.error("Tried to use skill {} without enough bullets", skill.skillId);
                return;
            }
            final int position = bulletEntryResult.get().getKey();
            final Item bulletItem = bulletEntryResult.get().getValue();
            // Consume bullets
            bulletItem.setQuantity((short) (bulletItem.getQuantity() - bulletCon));
            user.write(WvsContext.inventoryOperation(InventoryOperation.itemNumber(InventoryType.CONSUME, position, bulletItem.getQuantity()), true));
        }
        // Consume hp/mp
        user.addHp(-hpCon);
        user.addMp(-mpCon);
        // Set cooltime
        if (cooltime > 0) {
            user.getSkillManager().setSkillCooltime(skill.skillId, Instant.now().plus(cooltime, ChronoUnit.SECONDS));
            user.write(UserLocal.skillCooltimeSet(skill.skillId, cooltime));
        }

        // Skill-specific handling
        JobHandler.handleSkill(user, skill, inPacket);
        user.write(WvsContext.skillUseResult());
    }

    public static void processHit(User user, HitInfo hitInfo) {
        final int damage = hitInfo.damage;

        final int powerGuardReduce = handlePowerGuard(user, hitInfo);
        final int mesoGuardReduce = handleMesoGuard(user, hitInfo);

        final int achillesReduce = getAchillesReduce(user, damage);
        final int comboBarrierReduce = getComboBarrierReduce(user, damage, achillesReduce);

        final int magicGuardReduce = getMagicGuardReduce(user, damage);
        final int magicShieldReduce = getMagicShieldReduce(user, damage);
        final int blueAuraReduce = getBlueAuraReduce(user, damage); // TODO distribute damage

        // Final damage
        hitInfo.damage = damage - powerGuardReduce - mesoGuardReduce - achillesReduce - comboBarrierReduce - magicGuardReduce - magicShieldReduce - blueAuraReduce;
        log.debug("Hit delta : {} = {} - {} - {} - {} - {} - {} - {} - {}", hitInfo.damage, damage, powerGuardReduce, mesoGuardReduce, achillesReduce, comboBarrierReduce, magicGuardReduce, magicShieldReduce, blueAuraReduce);

        // Process hit damage
        if (hitInfo.damage > 0) {
            user.addHp(-hitInfo.damage);
        }

        user.getField().broadcastPacket(UserRemote.hit(user, hitInfo), user);
    }

    private static int handlePowerGuard(User user, HitInfo hitInfo) {
        final int fixedDamage = MobProvider.getMobTemplate(hitInfo.templateId).map(MobTemplate::getFixedDamage).orElse(0);
        final int reflectDamage = fixedDamage > 0 ? fixedDamage : hitInfo.damage * hitInfo.reflect / 100;
        if (reflectDamage > 0) {
            final Optional<Mob> reflectMobResult = user.getField().getMobPool().getById(hitInfo.reflectMobId);
            if (reflectMobResult.isPresent()) {
                // Acquire and damage mob
                try (var lockedMob = reflectMobResult.get().acquire()) {
                    lockedMob.get().damage(user, reflectDamage);
                }
            }
        }
        return reflectDamage;
    }

    private static int handleMesoGuard(User user, HitInfo hitInfo) {
        // CalcDamage::GetMesoGuardReduce
        final TemporaryStatOption option = user.getSecondaryStat().getOption(CharacterTemporaryStat.MesoGuard);
        if (option.nOption == 0) {
            return 0;
        }
        // Calculate reduction rate
        final SkillManager sm = user.getSkillManager();
        final int mesoGuardRate = Math.clamp(50 + sm.getSkillStatValue(Thief.MESO_MASTERY, SkillStat.v), 50, 100); // hard coded 50
        final int mesoRequiredRate = Math.max(0, sm.getSkillStatValue(Thief.MESO_GUARD, SkillStat.x) - sm.getSkillStatValue(Thief.MESO_MASTERY, SkillStat.w));
        // Calculate damage reduction
        final int realDamage = Math.clamp(hitInfo.damage, 1, GameConstants.DAMAGE_MAX);
        final int mesoGuardReduce = realDamage * mesoGuardRate / 100;
        final int mesoRequired = mesoGuardReduce * mesoRequiredRate / 100;
        // Check if character has enough money and deduct
        final InventoryManager im = user.getInventoryManager();
        final int mesoRemaining = im.getMoney();
        if (mesoRemaining > mesoRequired) {
            if (!im.addMoney(-mesoRequired)) {
                throw new IllegalStateException("Could not deduct money for meso guard");
            }
            user.write(WvsContext.statChanged(Stat.MONEY, im.getMoney(), false));
            return mesoGuardReduce;
        } else {
            if (!im.addMoney(-mesoRemaining)) {
                throw new IllegalStateException("Could not deduct money for meso guard");
            }
            user.write(WvsContext.statChanged(Stat.MONEY, im.getMoney(), false));
            return 100 * mesoRemaining / mesoRequiredRate;
        }
    }

    private static int getAchillesReduce(User user, int damage) {
        // CUserLocal::GetAchillesReduce
        final int skillId = switch (user.getJob()) {
            case 112 -> Warrior.ACHILLES_HERO;
            case 122 -> Warrior.ACHILLES_PALADIN;
            case 132 -> Warrior.ACHILLES_DRK;
            case 2112 -> Aran.HIGH_DEFENSE;
            default -> 0;
        };
        if (skillId == 0) {
            return 0;
        }
        final int x = user.getSkillManager().getSkillStatValue(skillId, SkillStat.x);
        return damage * (1000 - x) / 1000;
    }

    private static int getComboBarrierReduce(User user, int damage, int achillesReduce) {
        final TemporaryStatOption option = user.getSecondaryStat().getOption(CharacterTemporaryStat.ComboBarrier);
        if (option.nOption == 0) {
            return 0;
        }
        return (damage - achillesReduce) * (1000 - option.nOption) / 1000;
    }

    private static int getMagicGuardReduce(User user, int damage) {
        final SecondaryStat ss = user.getSecondaryStat();
        final TemporaryStatOption option = ss.getOption(CharacterTemporaryStat.MagicGuard);
        if (option.nOption == 0) {
            return 0;
        }
        final int mpDamage = damage * option.nOption / 100;
        if (ss.getOption(CharacterTemporaryStat.Infinity).nOption != 0) {
            return mpDamage;
        } else {
            return Math.min(mpDamage, user.getMp());
        }
    }

    private static int getMagicShieldReduce(User user, int damage) {
        final TemporaryStatOption option = user.getSecondaryStat().getOption(CharacterTemporaryStat.MagicShield);
        if (option.nOption == 0) {
            return 0;
        }
        return damage * option.nOption / 100;
    }

    private static int getBlueAuraReduce(User user, int damage) {
        final TemporaryStatOption option = user.getSecondaryStat().getOption(CharacterTemporaryStat.BlueAura);
        if (option.nOption == 0) {
            return 0;
        }
        final Optional<SkillInfo> skillInfoResult = SkillProvider.getSkillInfoById(option.rOption);
        if (skillInfoResult.isEmpty()) {
            log.error("Could not resolve skill info for blue aura cts reason : {}", option.rOption);
            return 0;
        }
        final int x = skillInfoResult.get().getValue(SkillStat.x, option.nOption);
        return damage * x / 100;
    }
}
