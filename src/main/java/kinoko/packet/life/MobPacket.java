package kinoko.packet.life;

import kinoko.server.header.OutHeader;
import kinoko.server.packet.OutPacket;
import kinoko.world.life.MovePath;
import kinoko.world.life.mob.Mob;

public final class MobPacket {
    public static OutPacket mobEnterField(Mob mob) {
        final OutPacket outPacket = OutPacket.of(OutHeader.MOB_ENTER_FIELD);
        outPacket.encodeInt(mob.getObjectId()); // dwMobId
        outPacket.encodeByte(1); // nCalcDamageIndex
        outPacket.encodeInt(mob.getTemplateId()); // dwTemplateID
        mob.getMobStatManager().encode(outPacket);
        mob.encodeInit(outPacket);
        return outPacket;
    }

    public static OutPacket mobLeaveField(Mob mob) {
        final OutPacket outPacket = OutPacket.of(OutHeader.MOB_LEAVE_FIELD);
        outPacket.encodeInt(mob.getObjectId()); // dwMobID
        outPacket.encodeByte(1); // nDeadType
        // if nDeadType == 4, encodeInt(dwSwallowCharacterID);
        return outPacket;
    }

    public static OutPacket mobChangeController(Mob mob, boolean forController) {
        final OutPacket outPacket = OutPacket.of(OutHeader.MOB_CHANGE_CONTROLLER);
        outPacket.encodeByte(forController);
        outPacket.encodeInt(mob.getObjectId()); // dwMobId
        if (forController) {
            outPacket.encodeByte(1); // nCalcDamageIndex
            // CMobPool::SetLocalMob
            outPacket.encodeInt(mob.getTemplateId()); // dwTemplateID
            mob.getMobStatManager().encode(outPacket);
            mob.encodeInit(outPacket);
        }
        return outPacket;
    }

    public static OutPacket mobMove(Mob mob, MovePath movePath) {
        final OutPacket outPacket = OutPacket.of(OutHeader.MOB_MOVE);
        outPacket.encodeInt(mob.getObjectId()); // dwMobId
        outPacket.encodeByte(false); // bNotForceLandingWhenDiscard
        outPacket.encodeByte(false); // bNotChangeActrion
        outPacket.encodeByte(false); // bNextAttackPossible
        outPacket.encodeByte(false); // bLeft
        outPacket.encodeInt(0); // aMultiTargetForBall
        outPacket.encodeInt(0); // aRandTimeforAreaAttack
        movePath.encode(outPacket);
        return outPacket;
    }

    public static OutPacket mobCtrlAck(Mob mob, short mobCtrlSn, boolean isNextAttackPossible) {
        final OutPacket outPacket = OutPacket.of(OutHeader.MOB_CTRL_ACK);
        outPacket.encodeInt(mob.getObjectId()); // dwMobId
        outPacket.encodeShort(mobCtrlSn); // nMobCtrlSN
        outPacket.encodeByte(isNextAttackPossible); // bNextAttackPossible
        outPacket.encodeShort(0); // nMP
        outPacket.encodeByte(0); // nSkillCommand
        outPacket.encodeByte(0); // nSLV
        return outPacket;
    }
}
