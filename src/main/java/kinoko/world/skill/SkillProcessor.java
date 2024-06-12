package kinoko.world.skill;

import kinoko.packet.user.SummonedPacket;
import kinoko.packet.user.UserLocal;
import kinoko.packet.user.UserRemote;
import kinoko.packet.world.WvsContext;
import kinoko.provider.SkillProvider;
import kinoko.provider.skill.SkillInfo;
import kinoko.provider.skill.SkillStat;
import kinoko.server.header.OutHeader;
import kinoko.util.Locked;
import kinoko.util.Util;
import kinoko.world.GameConstants;
import kinoko.world.field.Field;
import kinoko.world.field.mob.Mob;
import kinoko.world.field.mob.MobActionType;
import kinoko.world.field.mob.MobStatOption;
import kinoko.world.field.mob.MobTemporaryStat;
import kinoko.world.field.summoned.Summoned;
import kinoko.world.field.summoned.SummonedActionType;
import kinoko.world.item.*;
import kinoko.world.job.explorer.Thief;
import kinoko.world.job.explorer.Warrior;
import kinoko.world.job.legend.Aran;
import kinoko.world.user.CalcDamage;
import kinoko.world.user.User;
import kinoko.world.user.effect.Effect;
import kinoko.world.user.stat.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class SkillProcessor {
    private static final Logger log = LogManager.getLogger(SkillProcessor.class);


    // PROCESS ATTACK --------------------------------------------------------------------------------------------------

    public static void processAttack(Locked<User> locked, Attack attack) {
        final User user = locked.get();

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
                log.error("Could not to resolve skill info for attack skill ID : {}", attack.skillId);
                return;
            }
            final SkillInfo si = skillInfoResult.get();
            // Check skill cooltime and cost
            if (user.getSkillManager().hasSkillCooltime(attack.skillId)) {
                log.error("Tried to use skill {} that is still on cooltime", attack.skillId);
                return;
            }
            final int hpCon = getHpCon(user, attack.skillId, si.getValue(SkillStat.hpCon, attack.slv));
            if (user.getHp() <= hpCon) {
                log.error("Tried to use skill {} without enough hp, current : {}, required : {}", attack.skillId, user.getHp(), hpCon);
                return;
            }
            final int mpCon = getMpCon(user, attack.skillId, si.getValue(SkillStat.mpCon, attack.slv));
            if (user.getMp() < mpCon) {
                log.error("Tried to use skill {} without enough mp, current : {}, required : {}", attack.skillId, user.getMp(), mpCon);
                return;
            }
            // Item / Bullet consume are mutually exclusive
            final int itemCon = si.getValue(SkillStat.itemCon, attack.slv);
            if (itemCon > 0) {
                final int itemConNo = si.getValue(SkillStat.itemConNo, attack.slv); // should always be > 0
                final Optional<List<InventoryOperation>> removeResult = user.getInventoryManager().removeItem(itemCon, itemConNo);
                if (removeResult.isEmpty()) {
                    log.error("Tried to use skill {} without required item", itemCon);
                    return;
                }
                user.write(WvsContext.inventoryOperation(removeResult.get(), true));
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
            final int cooltime = si.getValue(SkillStat.cooltime, attack.slv);
            if (cooltime > 0) {
                user.getSkillManager().setSkillCooltime(attack.skillId, Instant.now().plus(cooltime, ChronoUnit.SECONDS));
                user.write(UserLocal.skillCooltimeSet(attack.skillId, cooltime));
            }
        }

        // CTS updates on attack
        if (attack.getMobCount() > 0) {
            handleComboAttack(user);
            handleEnergyCharge(user);
        }

        // Skill specific handling
        SkillDispatcher.handleAttack(locked, attack);

        // Process attack
        int totalMpDamage = 0;
        for (AttackInfo ai : attack.getAttackInfo()) {
            final Optional<Mob> mobResult = user.getField().getMobPool().getById(ai.mobId);
            if (mobResult.isEmpty()) {
                continue;
            }
            // Acquire and damage mob
            try (var lockedMob = mobResult.get().acquire()) {
                final Mob mob = lockedMob.get();
                int totalDamage = Arrays.stream(ai.damage).sum();
                int mpDamage = 0;

                // Handle Heaven's Hammer
                if (attack.skillId == Warrior.HEAVENS_HAMMER) {
                    totalDamage = calculateHeavensHammer(user, mob);
                }
                // Handle MP Eater
                if (attack.skillId != 0) {
                    mpDamage = calculateMpEater(user, mob);
                }
                // Process damage
                mob.damage(user, totalDamage);
                mob.setMp(mob.getMp() - mpDamage);
                totalMpDamage += mpDamage;
            }
        }
        user.getField().broadcastPacket(UserRemote.attack(user, attack), user);

        // Process mp eater
        if (totalMpDamage > 0) {
            final int skillId = SkillConstants.getMpEaterSkill(user.getJob());
            final int slv = user.getSkillManager().getSkillLevel(skillId);
            user.addMp(totalMpDamage);
            user.write(UserLocal.effect(Effect.skillUse(skillId, slv, user.getLevel())));
            user.getField().broadcastPacket(UserRemote.effect(user, Effect.skillUse(skillId, slv, user.getLevel())), user);
        }
    }

    private static void handleComboAttack(User user) {
        final TemporaryStatOption option = user.getSecondaryStat().getOption(CharacterTemporaryStat.ComboCounter);
        if (option.nOption == 0) {
            return;
        }
        final SkillManager sm = user.getSkillManager();
        final int maxCombo = 1 + Math.max(
                sm.getSkillStatValue(Warrior.COMBO_ATTACK, SkillStat.x),
                sm.getSkillStatValue(Warrior.ADVANCED_COMBO_ATTACK, SkillStat.x)
        );
        final int doubleProp = sm.getSkillStatValue(Warrior.ADVANCED_COMBO_ATTACK, SkillStat.prop);
        final int newCombo = Math.min(option.nOption + (Util.succeedProp(doubleProp) ? 2 : 1), maxCombo);
        if (newCombo > option.nOption) {
            user.setTemporaryStat(CharacterTemporaryStat.ComboCounter, option.update(newCombo));
        }
    }

    private static void handleEnergyCharge(User user) {
        final int skillId = SkillConstants.getEnergyChargeSkill(user.getJob());
        final int slv = user.getSkillManager().getSkillLevel(skillId);
        if (slv == 0) {
            return;
        }
        final Optional<SkillInfo> skillInfoResult = SkillProvider.getSkillInfoById(skillId);
        if (skillInfoResult.isEmpty()) {
            log.error("Could not resolve skill info for energy charge skill ID : {}", skillId);
            return;
        }
        final SkillInfo si = skillInfoResult.get();
        final SecondaryStat ss = user.getSecondaryStat();
        final int energyCharge = ss.getOption(CharacterTemporaryStat.EnergyCharged).nOption;
        if (energyCharge < SkillConstants.ENERGY_CHARGE_MAX) {
            final TwoStateTemporaryStat option = TwoStateTemporaryStat.ofTwoState(
                    CharacterTemporaryStat.EnergyCharged,
                    Math.min(energyCharge + si.getValue(SkillStat.x, slv), SkillConstants.ENERGY_CHARGE_MAX),
                    skillId,
                    si.getDuration(slv)
            );
            user.setTemporaryStat(CharacterTemporaryStat.EnergyCharged, option);
        }
    }

    private static int calculateHeavensHammer(User user, Mob mob) {
        final int damage = user.getSkillManager().getSkillStatValue(Warrior.HEAVENS_HAMMER, SkillStat.damage);
        final int totalDamage = Math.min(
                CalcDamage.getRandomDamage(user) * damage / 100, // TODO: use PDamage
                GameConstants.DAMAGE_MAX
        );
        return Math.min(totalDamage, mob.getHp() - 1);
    }

    private static int calculateMpEater(User user, Mob mob) {
        final int skillId = SkillConstants.getMpEaterSkill(user.getJob());
        final int slv = user.getSkillManager().getSkillLevel(skillId);
        if (slv == 0) {
            return 0;
        }
        final Optional<SkillInfo> skillInfoResult = SkillProvider.getSkillInfoById(skillId);
        if (skillInfoResult.isEmpty()) {
            log.error("Could not resolve skill info for mp eater skill ID : {}", skillId);
            return 0;
        }
        final SkillInfo si = skillInfoResult.get();
        if (!Util.succeedProp(si.getValue(SkillStat.prop, slv))) {
            return 0;
        }
        final int delta = mob.getMaxMp() * si.getValue(SkillStat.x, slv) / 100;
        return Math.clamp(delta, 0, mob.getMp());
    }


    // PROCESS SKILL ---------------------------------------------------------------------------------------------------

    public static void processSkill(Locked<User> locked, Skill skill) {
        final User user = locked.get();

        // Resolve skill info
        final Optional<SkillInfo> skillInfoResult = SkillProvider.getSkillInfoById(skill.skillId);
        if (skillInfoResult.isEmpty()) {
            log.error("Could not resolve skill info for skill ID : {}", skill.skillId);
            return;
        }
        final SkillInfo si = skillInfoResult.get();

        // Check skill cooltime and cost
        if (user.getSkillManager().hasSkillCooltime(skill.skillId)) {
            log.error("Tried to use skill {} that is still on cooltime", skill.skillId);
            return;
        }
        final int hpCon = getHpCon(user, skill.skillId, si.getValue(SkillStat.hpCon, skill.slv));
        if (user.getHp() <= hpCon) {
            log.error("Tried to use skill {} without enough hp, current : {}, required : {}", skill.skillId, user.getHp(), hpCon);
            return;
        }
        final int mpCon = getMpCon(user, skill.skillId, si.getValue(SkillStat.mpCon, skill.slv));
        if (user.getMp() < mpCon) {
            log.error("Tried to use skill {} without enough mp, current : {}, required : {}", skill.skillId, user.getMp(), mpCon);
            return;
        }
        // Item / Bullet consume are mutually exclusive
        final int itemCon = si.getValue(SkillStat.itemCon, skill.slv);
        if (itemCon > 0) {
            final int itemConNo = si.getValue(SkillStat.itemConNo, skill.slv); // should always be > 0
            final Optional<List<InventoryOperation>> removeResult = user.getInventoryManager().removeItem(itemCon, itemConNo);
            if (removeResult.isEmpty()) {
                log.error("Tried to use skill {} without required item", itemCon);
                return;
            }
            user.write(WvsContext.inventoryOperation(removeResult.get(), true));
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
        final int cooltime = si.getValue(SkillStat.cooltime, skill.slv);
        if (cooltime > 0) {
            user.getSkillManager().setSkillCooltime(skill.skillId, Instant.now().plus(cooltime, ChronoUnit.SECONDS));
            user.write(UserLocal.skillCooltimeSet(skill.skillId, cooltime));
        }

        // Skill-specific handling
        SkillDispatcher.handleSkill(locked, skill);
        user.write(WvsContext.skillUseResult());

        // Skill effects and party handling
        final Field field = user.getField();
        field.broadcastPacket(UserRemote.effect(user, Effect.skillUse(skill.skillId, skill.slv, user.getLevel())), user);
        skill.forEachAffectedMember(user, user.getField(), (member) -> {
            try (var lockedMember = member.acquire()) {
                SkillDispatcher.handleSkill(lockedMember, skill);
                member.write(UserLocal.effect(Effect.skillAffected(skill.skillId, skill.slv)));
                field.broadcastPacket(UserRemote.effect(member, Effect.skillAffected(skill.skillId, skill.slv)), member);
            }
        });
    }


    // PROCESS HIT -----------------------------------------------------------------------------------------------------

    public static void processHit(Locked<User> locked, HitInfo hitInfo) {
        final User user = locked.get();
        final int damage = hitInfo.damage;

        // Compute damage reductions
        final int powerGuardReduce = handleReflect(user, hitInfo);
        final int mesoGuardReduce = handleMesoGuard(user, hitInfo);

        final int achillesReduce = getAchillesReduce(user, damage);
        final int comboBarrierReduce = getComboBarrierReduce(user, damage, achillesReduce);

        final int magicGuardReduce = getMagicGuardReduce(user, damage);
        final int magicShieldReduce = getMagicShieldReduce(user, damage);
        final int blueAuraReduce = getBlueAuraReduce(user, damage); // TODO distribute damage

        // Final damage
        hitInfo.finalDamage = damage - powerGuardReduce - mesoGuardReduce - achillesReduce - comboBarrierReduce - magicGuardReduce - magicShieldReduce - blueAuraReduce;
        log.debug("Hit delta : {} = {} - {} - {} - {} - {} - {} - {} - {}", hitInfo.finalDamage, damage, powerGuardReduce, mesoGuardReduce, achillesReduce, comboBarrierReduce, magicGuardReduce, magicShieldReduce, blueAuraReduce);

        // Process hit damage
        if (hitInfo.finalDamage > 0) {
            user.addHp(-hitInfo.finalDamage);
        }
        user.getField().broadcastPacket(UserRemote.hit(user, hitInfo), user);

        // Process on hit effects
        handleGuardian(user, hitInfo);
        handleDivineShield(user, hitInfo);
        handleBeholderCounter(user, hitInfo);
    }

    private static int handleReflect(User user, HitInfo hitInfo) {
        // Resolve target
        final Optional<Mob> mobResult = user.getField().getMobPool().getById(hitInfo.reflectMobId);
        if (mobResult.isEmpty()) {
            return 0;
        }
        if (user.getSecondaryStat().hasOption(CharacterTemporaryStat.PowerGuard)) {
            // Power Guard - reflect nPowerGuard %, subtract reflected amount
            final int percentage = user.getSecondaryStat().getOption(CharacterTemporaryStat.PowerGuard).nOption;
            try (var lockedMob = mobResult.get().acquire()) {
                final Mob mob = lockedMob.get();
                final int damage = Math.min(hitInfo.damage * percentage / 100, mob.getMaxHp() * percentage / 100);
                final int finalDamage = mob.getFixedDamage() > 0 ? mob.getFixedDamage() : damage;
                // Process reflect damage and return amount to subtract from hit damage
                mob.damage(user, finalDamage);
                return finalDamage;
            }
        } else if (user.getSecondaryStat().hasOption(CharacterTemporaryStat.ManaReflection)) {
            // Mana Reflection - reflect si.getValue(SkillStat.x, nManaReflection) %
            final TemporaryStatOption option = user.getSecondaryStat().getOption(CharacterTemporaryStat.ManaReflection);
            final Optional<SkillInfo> skillInfoResult = SkillProvider.getSkillInfoById(option.rOption);
            if (skillInfoResult.isPresent()) {
                final int percentage = skillInfoResult.get().getValue(SkillStat.x, option.nOption);
                try (var lockedMob = mobResult.get().acquire()) {
                    final Mob mob = lockedMob.get();
                    final int damage = Math.min(hitInfo.damage * percentage / 100, mob.getMaxHp() / 20); // skill description says 20% of max hp, but coded incorrectly in client
                    final int finalDamage = mob.getFixedDamage() > 0 ? mob.getFixedDamage() : damage;
                    mob.damage(user, finalDamage);
                    // no amount is subtracted from hit damage
                }
            } else {
                log.error("Could not resolve skill info for mana reflection skill ID : {}", option.rOption);
            }
        } else {
            log.error("Reflect on mob ID {} without PowerGuard or ManaReflection CTS", hitInfo.reflectMobId);
        }
        return 0;
    }

    private static int handleMesoGuard(User user, HitInfo hitInfo) {
        // CalcDamage::GetMesoGuardReduce
        if (!user.getSecondaryStat().hasOption(CharacterTemporaryStat.MesoGuard)) {
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
        if (!user.getSecondaryStat().hasOption(CharacterTemporaryStat.ComboBarrier)) {
            return 0;
        }
        final TemporaryStatOption option = user.getSecondaryStat().getOption(CharacterTemporaryStat.ComboBarrier);
        return (damage - achillesReduce) * (1000 - option.nOption) / 1000;
    }

    private static int getMagicGuardReduce(User user, int damage) {
        if (!user.getSecondaryStat().hasOption(CharacterTemporaryStat.MagicGuard)) {
            return 0;
        }
        final TemporaryStatOption option = user.getSecondaryStat().getOption(CharacterTemporaryStat.MagicGuard);
        final int mpDamage = damage * option.nOption / 100;
        if (user.getSecondaryStat().hasOption(CharacterTemporaryStat.Infinity)) {
            return mpDamage;
        } else {
            return Math.min(mpDamage, user.getMp());
        }
    }

    private static int getMagicShieldReduce(User user, int damage) {
        if (!user.getSecondaryStat().hasOption(CharacterTemporaryStat.MagicShield)) {
            return 0;
        }
        final TemporaryStatOption option = user.getSecondaryStat().getOption(CharacterTemporaryStat.MagicShield);
        return damage * option.nOption / 100;
    }

    private static int getBlueAuraReduce(User user, int damage) {
        if (!user.getSecondaryStat().hasOption(CharacterTemporaryStat.BlueAura)) {
            return 0;
        }
        final TemporaryStatOption option = user.getSecondaryStat().getOption(CharacterTemporaryStat.BlueAura);
        final Optional<SkillInfo> skillInfoResult = SkillProvider.getSkillInfoById(option.rOption);
        if (skillInfoResult.isEmpty()) {
            log.error("Could not resolve skill info for blue aura cts reason : {}", option.rOption);
            return 0;
        }
        final int x = skillInfoResult.get().getValue(SkillStat.x, option.nOption);
        return damage * x / 100;
    }

    private static void handleGuardian(User user, HitInfo hitInfo) {
        if (hitInfo.knockback <= 1) {
            return;
        }
        final int stunDuration = user.getSkillManager().getSkillStatValue(Warrior.GUARDIAN, SkillStat.time);
        if (stunDuration == 0) {
            return;
        }
        final Optional<Mob> knockbackMobResult = user.getField().getMobPool().getById(hitInfo.reflectMobId);
        if (knockbackMobResult.isPresent()) {
            // Acquire and stun mob
            try (var lockedMob = knockbackMobResult.get().acquire()) {
                lockedMob.get().setTemporaryStat(MobTemporaryStat.Stun, MobStatOption.of(1, Warrior.GUARDIAN, stunDuration * 1000));
            }
        }
    }

    private static void handleDivineShield(User user, HitInfo hitInfo) {
        if (hitInfo.attackIndex.getValue() <= AttackIndex.Counter.getValue()) {
            return;
        }
        // Resolve skill
        final int skillId = Warrior.DIVINE_SHIELD;
        final Optional<SkillInfo> skillInfoResult = SkillProvider.getSkillInfoById(skillId);
        if (skillInfoResult.isEmpty()) {
            log.error("Could not resolve skill info for divine shield skill ID : {}", skillId);
            return;
        }
        final SkillInfo si = skillInfoResult.get();
        final SkillManager sm = user.getSkillManager();
        // Check current stack count
        final SecondaryStat ss = user.getSecondaryStat();
        if (ss.hasOption(CharacterTemporaryStat.BlessingArmor) && ss.hasOption(CharacterTemporaryStat.BlessingArmor)) {
            // Decrement divine shield count
            final TemporaryStatOption option = ss.getOption(CharacterTemporaryStat.BlessingArmor);
            final int newCount = option.nOption - 1;
            if (newCount > 0) {
                user.setTemporaryStat(Map.of(
                        CharacterTemporaryStat.BlessingArmor, option.update(newCount),
                        CharacterTemporaryStat.BlessingArmorIncPAD, ss.getOption(CharacterTemporaryStat.BlessingArmorIncPAD) // required for TemporaryStatSet
                ));
            } else {
                user.resetTemporaryStat(skillId);
            }
        } else {
            // Try giving divine shield buff
            final int slv = sm.getSkillLevel(skillId);
            if (slv == 0 || sm.hasSkillCooltime(skillId) || !Util.succeedProp(si.getValue(SkillStat.prop, slv))) {
                return;
            }
            user.setTemporaryStat(Map.of(
                    CharacterTemporaryStat.BlessingArmor, TemporaryStatOption.of(si.getValue(SkillStat.x, slv), skillId, si.getDuration(slv)),
                    CharacterTemporaryStat.BlessingArmorIncPAD, TemporaryStatOption.of(si.getValue(SkillStat.epad, slv), skillId, si.getDuration(slv))
            ));
            sm.setSkillCooltime(skillId, Instant.now().plus(si.getValue(SkillStat.cooltime, slv), ChronoUnit.SECONDS));
        }
    }

    private static void handleBeholderCounter(User user, HitInfo hitInfo) {
        if (hitInfo.attackIndex.getValue() <= AttackIndex.Counter.getValue()) {
            return;
        }
        // Resolve summoned
        final Optional<Summoned> summonedResult = user.getSummonedById(Warrior.BEHOLDER);
        if (summonedResult.isEmpty()) {
            return;
        }
        final Summoned summoned = summonedResult.get();
        // Resolve skill info
        final SkillManager sm = user.getSkillManager();
        final int skillId = Warrior.HEX_OF_THE_BEHOLDER_COUNTER;
        final int slv = sm.getSkillLevel(skillId);
        if (slv == 0) {
            return;
        }
        final Optional<SkillInfo> skillInfoResult = SkillProvider.getSkillInfoById(skillId);
        if (skillInfoResult.isEmpty()) {
            log.error("Could not resolve hex of the beholder counter skill ID : {}", skillId);
            return;
        }
        final SkillInfo si = skillInfoResult.get();
        // Resolve target mob
        final Optional<Mob> mobResult = user.getField().getMobPool().getById(hitInfo.mobId);
        if (mobResult.isEmpty()) {
            return;
        }
        try (var lockedMob = mobResult.get().acquire()) {
            final Mob mob = lockedMob.get();
            if (mob.getHp() <= 0) {
                return;
            }
            // Compute damage
            final int totalDamage;
            if (mob.getFixedDamage() > 0) {
                totalDamage = mob.getFixedDamage();
            } else {
                totalDamage = Math.min(
                        CalcDamage.getRandomDamage(user) * si.getValue(SkillStat.damage, slv) / 100,
                        GameConstants.DAMAGE_MAX
                );
            }
            // Create attack
            final Attack attack = new Attack(OutHeader.SummonedAttack);
            attack.actionAndDir = (byte) ((summoned.getMoveAction() & 1) << 7 | SummonedActionType.ATTACK1.getValue() & 0x7F);
            final AttackInfo attackInfo = new AttackInfo();
            attackInfo.mobId = mob.getId();
            attackInfo.hitAction = MobActionType.HIT1.getValue();
            attackInfo.damage[0] = totalDamage;
            attack.getAttackInfo().add(attackInfo);
            // Process damage and broadcast
            mob.damage(user, totalDamage);
            user.getField().broadcastPacket(SummonedPacket.summonedAttack(user, summoned, attack));
        }
    }


    // PROCESS UPDATE --------------------------------------------------------------------------------------------------

    public static void processUpdate(Locked<User> locked, Instant now) {
        final User user = locked.get();
        if (user.getHp() <= 0) {
            return;
        }

        handleRecovery(user, now);
        handleDragonBlood(user, now);
        handleInfinity(user, now);
    }

    private static void handleRecovery(User user, Instant now) {
        if (!user.getSecondaryStat().hasOption(CharacterTemporaryStat.Regen)) {
            return;
        }
        final TemporaryStatOption option = user.getSecondaryStat().getOption(CharacterTemporaryStat.Regen);
        final int skillId = option.rOption;
        if (now.isAfter(user.getSkillManager().getSkillSchedule(skillId))) {
            final int hpRecovery = option.nOption;
            user.addHp(hpRecovery);
            user.write(UserLocal.effect(Effect.incDecHpEffect(hpRecovery)));
            user.getField().broadcastPacket(UserRemote.effect(user, Effect.incDecHpEffect(hpRecovery)), user);
            user.getSkillManager().setSkillSchedule(skillId, now.plus(5, ChronoUnit.SECONDS));
        }
    }

    private static void handleDragonBlood(User user, Instant now) {
        if (!user.getSecondaryStat().hasOption(CharacterTemporaryStat.DragonBlood)) {
            return;
        }
        final TemporaryStatOption option = user.getSecondaryStat().getOption(CharacterTemporaryStat.DragonBlood);
        final int skillId = option.rOption;
        if (now.isAfter(user.getSkillManager().getSkillSchedule(skillId))) {
            final int hpConsume = option.nOption;
            if (user.getHp() < hpConsume * 4) {
                // Skill is canceled when you don't have enough HP to be consumed in the next 4 seconds
                user.resetTemporaryStat(skillId);
                return;
            }
            user.addHp(-hpConsume);
            user.getSkillManager().setSkillSchedule(skillId, now.plus(1, ChronoUnit.SECONDS));
        }
    }

    private static void handleInfinity(User user, Instant now) {
        if (!user.getSecondaryStat().hasOption(CharacterTemporaryStat.Infinity)) {
            return;
        }
        final TemporaryStatOption option = user.getSecondaryStat().getOption(CharacterTemporaryStat.Infinity);
        final int skillId = option.rOption;
        if (now.isAfter(user.getSkillManager().getSkillSchedule(option.rOption))) {
            final Optional<SkillInfo> skillInfoResult = SkillProvider.getSkillInfoById(skillId);
            if (skillInfoResult.isEmpty()) {
                log.error("Could not resolve skill info for infinity skill ID : {}", skillId);
                return;
            }
            final SkillInfo si = skillInfoResult.get();
            final int slv = user.getSkillManager().getSkillLevel(skillId);
            final int percentage = si.getValue(SkillStat.y, slv);
            // Recover hp and mp
            user.addHp(user.getMaxHp() * percentage / 100);
            user.addMp(user.getMaxMp() * percentage / 100);
            // Increase magic att %
            final int damage = si.getValue(SkillStat.damage, slv);
            user.setTemporaryStat(CharacterTemporaryStat.Infinity, option.update(option.nOption + damage));
            user.getSkillManager().setSkillSchedule(option.rOption, now.plus(4, ChronoUnit.SECONDS));
        }
    }


    // COMMON ----------------------------------------------------------------------------------------------------------

    public static int getHpCon(User user, int skillId, int hpCon) {
        if (skillId == Warrior.SACRIFICE || skillId == Warrior.DRAGON_ROAR) {
            return user.getMaxHp() * user.getSkillManager().getSkillStatValue(skillId, SkillStat.x) / 100;
        }
        return hpCon;
    }

    public static int getMpCon(User user, int skillId, int mpCon) {
        // CSkillInfo::CheckConsumeForActiveSkill
        final int incMpCon = 100 + user.getSkillManager().getSkillStatValue(SkillConstants.getAmplificationSkill(user.getJob()), SkillStat.x);
        mpCon = mpCon * incMpCon / 100;
        // Check CTS affecting mpCon
        final SecondaryStat ss = user.getSecondaryStat();
        if (ss.hasOption(CharacterTemporaryStat.Infinity)) {
            mpCon = 0;
        }
        if (ss.hasOption(CharacterTemporaryStat.Concentration)) {
            final int percentage = 100 - ss.getOption(CharacterTemporaryStat.Concentration).nOption;
            mpCon = (int) (percentage * mpCon / 100.0 + 0.99);
        }
        if (SkillConstants.isTeleportSkill(skillId) && ss.hasOption(CharacterTemporaryStat.TeleportMasteryOn)) {
            mpCon += ss.getOption(CharacterTemporaryStat.TeleportMasteryOn).nOption;
        }
        return mpCon;
    }
}
