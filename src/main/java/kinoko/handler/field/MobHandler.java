package kinoko.handler.field;

import kinoko.handler.Handler;
import kinoko.packet.field.MobPacket;
import kinoko.provider.SkillProvider;
import kinoko.provider.mob.MobAttack;
import kinoko.provider.mob.MobSkill;
import kinoko.provider.mob.MobSkillType;
import kinoko.provider.skill.SkillInfo;
import kinoko.provider.skill.SkillStat;
import kinoko.server.header.InHeader;
import kinoko.server.packet.InPacket;
import kinoko.util.Locked;
import kinoko.util.Tuple;
import kinoko.util.Util;
import kinoko.world.GameConstants;
import kinoko.world.field.Field;
import kinoko.world.field.life.MovePath;
import kinoko.world.field.mob.*;
import kinoko.world.user.User;
import kinoko.world.user.stat.CharacterTemporaryStat;
import kinoko.world.user.stat.TemporaryStatOption;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

public final class MobHandler {
    private static final Logger log = LogManager.getLogger(MobHandler.class);

    @Handler(InHeader.MobMove)
    public static void handleMobMove(User user, InPacket inPacket) {
        // CMob::GenerateMovePath
        final int objectId = inPacket.decodeInt(); // dwMobID

        final Field field = user.getField();
        final Optional<Mob> mobResult = field.getMobPool().getById(objectId);
        if (mobResult.isEmpty()) {
            log.error("Received MobMove for invalid object with ID : {}", objectId);
            return;
        }
        final Mob mob = mobResult.get();

        final short mobCtrlSn = inPacket.decodeShort(); // nMobCtrlSN
        final byte actionMask = inPacket.decodeByte(); // bDirLeft | (4 * (bRushMove | (2 * bRiseByToss | 2 * nMobCtrlState)))
        final byte actionAndDir = inPacket.decodeByte(); // nActionAndDir
        final int targetInfo = inPacket.decodeInt(); // CMob::TARGETINFO { short x, short y } || { short nSkillIDandLev, short nDelay }

        final List<Tuple<Integer, Integer>> multiTargetForBall = new ArrayList<>();
        final int multiTargetForBallCount = inPacket.decodeInt();
        for (int i = 0; i < multiTargetForBallCount; i++) {
            multiTargetForBall.add(new Tuple<>(
                    inPacket.decodeInt(), // aMultiTargetForBall[i].x
                    inPacket.decodeInt() // aMultiTargetForBall[i].y
            ));
        }
        final List<Integer> randTimeForAreaAttack = new ArrayList<>();
        final int randTimeForAreaAttackCount = inPacket.decodeInt();
        for (int i = 0; i < randTimeForAreaAttackCount; i++) {
            randTimeForAreaAttack.add(inPacket.decodeInt()); // aRandTimeforAreaAttack[i]
        }

        inPacket.decodeByte(); // (bActive == 0) | (16 * !(CVecCtrlMob::IsCheatMobMoveRand(pvcActive) == 0))
        inPacket.decodeInt(); // HackedCode
        inPacket.decodeInt(); // moveCtx.fc.ptTarget->x
        inPacket.decodeInt(); // moveCtx.fc.ptTarget->y
        inPacket.decodeInt(); // dwHackedCodeCRC

        final MovePath movePath = MovePath.decode(inPacket);
        movePath.applyTo(mob);

        inPacket.decodeByte(); // this->bChasing
        inPacket.decodeByte(); // pTarget != 0
        inPacket.decodeByte(); // pvcActive->bChasing
        inPacket.decodeByte(); // pvcActive->bChasingHack
        inPacket.decodeInt(); // pvcActive->tChaseDuration

        try (var lockedMob = mob.acquire()) {
            // handle mob attack / skill
            final MobAttackInfo mai = new MobAttackInfo();
            mai.actionMask = actionMask;
            mai.actionAndDir = actionAndDir;
            mai.targetInfo = targetInfo;
            mai.multiTargetForBall = multiTargetForBall;
            mai.randTimeForAreaAttack = randTimeForAreaAttack;
            handleMobAttack(lockedMob, mai);

            // update mob position and write response
            final boolean nextAttackPossible = mob.getAndDecrementAttackCounter() <= 0 && Util.succeedProp(GameConstants.MOB_ATTACK_CHANCE);
            user.write(MobPacket.mobCtrlAck(mob, mobCtrlSn, nextAttackPossible, mai));
            field.broadcastPacket(MobPacket.mobMove(mob, mai, movePath), user);
        }
    }

    @Handler(InHeader.MobApplyCtrl)
    public static void handleMobApplyCtrl(User user, InPacket inPacket) {
        // CMob::ApplyControl
        inPacket.decodeInt(); // dwMobID
        inPacket.decodeInt(); // unk
        // do nothing, since the controller logic is handled elsewhere
    }

    private static void handleMobAttack(Locked<Mob> lockedMob, MobAttackInfo mai) {
        final Mob mob = lockedMob.get();
        final int action = mai.actionAndDir >> 1;
        mai.isAttack = action >= MobActionType.ATTACK1.getValue() && action <= MobActionType.ATTACKF.getValue();
        mai.isSkill = action >= MobActionType.SKILL1.getValue() && action <= MobActionType.SKILLF.getValue();
        if (mai.isAttack) {
            final int attackIndex = action - MobActionType.ATTACK1.getValue();
            final Optional<MobAttack> mobAttackResult = mob.getAttack(attackIndex);
            if (mobAttackResult.isEmpty()) {
                log.error("{} : Could not resolve mob attack for index : {}", mob, attackIndex);
                return;
            }
            final MobAttack mobAttack = mobAttackResult.get();
            log.debug("{} : Using mob attack index {}", mob, attackIndex);
            mob.setMp(Math.max(mob.getMp() - mobAttack.getConMp(), 0));
            mob.setAttackCounter(Util.getRandom(
                    GameConstants.MOB_ATTACK_COOLTIME_MIN,
                    mob.isBoss() ? GameConstants.MOB_ATTACK_COOLTIME_MAX_BOSS : GameConstants.MOB_ATTACK_COOLTIME_MAX
            ));
        } else if (mai.isSkill) {
            final int skillIndex = action - MobActionType.SKILL1.getValue();
            final Optional<MobSkill> mobSkillResult = mob.getSkill(skillIndex);
            if (mobSkillResult.isEmpty()) {
                log.error("{} : Could not resolve mob skill for index : {}", mob, skillIndex);
                return;
            }
            final MobSkill mobSkill = mobSkillResult.get();

            mai.skillId = mai.targetInfo & 0xFF;
            mai.slv = (mai.targetInfo >> 8) & 0xFF;
            mai.option = (mai.targetInfo >> 16) & 0xFFFF;
            if (mai.skillId != mobSkill.getSkillId() || mai.slv != mobSkill.getSkillLevel()) {
                log.error("{} : Mismatching skill ID or level for mob skill index : {} ({}, {})", mob, skillIndex, mai.skillId, mai.slv);
                return;
            }

            final Optional<SkillInfo> skillInfoResult = SkillProvider.getMobSkillInfoById(mai.skillId);
            if (skillInfoResult.isEmpty()) {
                log.error("{} : Could not resolve skill info for mob skill : {}", mob, mai.skillId);
                return;
            }
            final SkillInfo si = skillInfoResult.get();
            if (mob.isSkillAvailable(mobSkill)) {
                log.debug("{} : Using mob skill index {} ({}, {})", mob, skillIndex, mai.skillId, mai.slv);
                mob.setMp(Math.max(mob.getMp() - si.getValue(SkillStat.mpCon, mai.slv), 0));
                mob.setSkillOnCooltime(mobSkill, Instant.now().plus(si.getValue(SkillStat.interval, mai.slv), ChronoUnit.SECONDS));
                if (!applyMobSkill(lockedMob, mobSkill, si)) {
                    log.error("{} : Could not apply mob skill effect for skill {}", mob, mobSkill.getSkillType().name());
                }
            } else {
                log.error("{} : Mob skill ({}, {}) not available", mob, mai.skillId, mai.slv);
                mai.skillId = 0;
                mai.slv = 0;
                mai.option = 0;
            }
        }
    }

    private static boolean applyMobSkill(Locked<Mob> lockedMob, MobSkill mobSkill, SkillInfo si) {
        final Mob mob = lockedMob.get();
        final MobSkillType skillType = mobSkill.getSkillType();
        final int skillId = mobSkill.getSkillId();
        final int slv = mobSkill.getSkillLevel();

        // Apply mob temporary stat
        final MobTemporaryStat mts = skillType.getMobTemporaryStat();
        if (mts != null) {
            final Set<Mob> targetMobs = new HashSet<>();
            if (si.getRect() != null) {
                targetMobs.addAll(mob.getField().getMobPool().getInsideRect(mob.getRelativeRect(si.getRect())));
            }
            targetMobs.add(mob);
            for (Mob targetMob : targetMobs) {
                targetMob.setTemporaryStat(mts, MobStatOption.ofMobSkill(si.getValue(SkillStat.x, slv), skillId, slv, si.getDuration(slv)));
            }
            return true;
        }

        // Apply character temporary stat
        final CharacterTemporaryStat cts = skillType.getCharacterTemporaryStat();
        if (cts != null) {
            final Set<User> targetUsers = new HashSet<>();
            if (si.getRect() != null) {
                targetUsers.addAll(mob.getField().getUserPool().getInsideRect(mob.getRelativeRect(si.getRect())));
            }
            for (User targetUser : targetUsers) {
                targetUser.setTemporaryStat(cts, TemporaryStatOption.ofMobSkill(si.getValue(SkillStat.x, slv), skillId, slv, si.getDuration(slv)));
            }
            return true;
        }

        // Special handling
        switch (skillType) {
            case HEAL_M -> {
                final Set<Mob> targetMobs = new HashSet<>();
                if (si.getRect() != null) {
                    targetMobs.addAll(mob.getField().getMobPool().getInsideRect(mob.getRelativeRect(si.getRect())));
                }
                targetMobs.add(mob);
                final int x = si.getValue(SkillStat.x, slv);
                final int y = si.getValue(SkillStat.y, slv);
                for (Mob targetMob : targetMobs) {
                    final int healAmount = x + Util.getRandom(y);
                    targetMob.heal(healAmount);
                }
            }
            case PCOUNTER -> {
                mob.setTemporaryStat(Map.of(
                        MobTemporaryStat.PImmune, MobStatOption.ofMobSkill(1, skillId, slv, si.getDuration(slv)),
                        MobTemporaryStat.PCounter, MobStatOption.ofMobSkill(si.getValue(SkillStat.x, slv), skillId, slv, si.getDuration(slv))
                ));
            }
            case MCOUNTER -> {
                mob.setTemporaryStat(Map.of(
                        MobTemporaryStat.MImmune, MobStatOption.ofMobSkill(1, skillId, slv, si.getDuration(slv)),
                        MobTemporaryStat.MCounter, MobStatOption.ofMobSkill(si.getValue(SkillStat.x, slv), skillId, slv, si.getDuration(slv))
                ));
            }
            case PMCOUNTER -> {
                mob.setTemporaryStat(Map.of(
                        MobTemporaryStat.PImmune, MobStatOption.ofMobSkill(1, skillId, slv, si.getDuration(slv)),
                        MobTemporaryStat.MImmune, MobStatOption.ofMobSkill(1, skillId, slv, si.getDuration(slv)),
                        MobTemporaryStat.PCounter, MobStatOption.ofMobSkill(si.getValue(SkillStat.x, slv), skillId, slv, si.getDuration(slv)),
                        MobTemporaryStat.MCounter, MobStatOption.ofMobSkill(si.getValue(SkillStat.x, slv), skillId, slv, si.getDuration(slv))
                ));
            }
            default -> {
                log.error("Unhandled mob skill type {}", skillType);
                return false;
            }
        }
        return true;
    }
}
