package kinoko.packet.world;

import kinoko.packet.world.message.Message;
import kinoko.server.header.OutHeader;
import kinoko.server.packet.OutPacket;
import kinoko.world.item.InventoryOperation;
import kinoko.world.user.stat.ExtendSP;
import kinoko.world.user.stat.Stat;
import kinoko.world.user.temp.TemporaryStatManager;

import java.util.List;
import java.util.Map;

public final class WvsContext {
    public static OutPacket statChanged(Stat stat, Object value) {
        return statChanged(Map.of(stat, value));
    }

    public static OutPacket statChanged(Map<Stat, Object> statMap) {
        final OutPacket outPacket = OutPacket.of(OutHeader.STAT_CHANGED);
        outPacket.encodeByte(true); // bool -> bExclRequestSent = 0

        outPacket.encodeInt(Stat.from(statMap.keySet()));
        for (Stat stat : Stat.ENCODE_ORDER) {
            if (statMap.containsKey(stat)) {
                switch (stat) {
                    case SKIN, LEVEL -> {
                        outPacket.encodeByte((byte) statMap.get(stat));
                    }
                    case JOB, STR, DEX, INT, LUK, AP, POP -> {
                        outPacket.encodeShort((short) statMap.get(stat));
                    }
                    case FACE, HAIR, HP, MAX_HP, MP, MAX_MP, EXP, MONEY, TEMP_EXP -> {
                        outPacket.encodeInt((int) statMap.get(stat));
                    }
                    case PET_1, PET_2, PET_3 -> {
                        outPacket.encodeLong((long) statMap.get(stat));
                    }
                    case SP -> {
                        if (statMap.get(stat) instanceof ExtendSP sp) {
                            sp.encode(outPacket);
                        } else {
                            outPacket.encodeShort((short) statMap.get(stat));
                        }
                    }
                }
            }
        }

        outPacket.encodeByte(false); // bool -> byte (CUserLocal::SetSecondaryStatChangedPoint)
        outPacket.encodeByte(false); // bool -> int, int (CBattleRecordMan::SetBattleRecoveryInfo)
        return outPacket;
    }

    public static OutPacket temporaryStatSet(TemporaryStatManager tsm, boolean complete) {
        final OutPacket outPacket = OutPacket.of(OutHeader.TEMPORARY_STAT_SET);
        tsm.encodeForLocal(outPacket, complete);
        outPacket.encodeShort(0); // tDelay
        outPacket.encodeByte(0); // SecondaryStat::IsMovementAffectingStat -> bSN
        return outPacket;
    }

    public static OutPacket temporaryStatReset(TemporaryStatManager tsm) {
        final OutPacket outPacket = OutPacket.of(OutHeader.TEMPORARY_STAT_SET);
        tsm.encodeReset(outPacket);
        outPacket.encodeByte(0); // SecondaryStat::IsMovementAffectingStat -> bSN
        return outPacket;
    }

    public static OutPacket message(Message message) {
        final OutPacket outPacket = OutPacket.of(OutHeader.MESSAGE);
        message.encode(outPacket);
        return outPacket;
    }

    public static OutPacket inventoryOperation(InventoryOperation op, boolean exclRequestSent) {
        return inventoryOperation(List.of(op), exclRequestSent);
    }

    public static OutPacket inventoryOperation(List<InventoryOperation> inventoryOperations, boolean exclRequestSent) {
        final OutPacket outPacket = OutPacket.of(OutHeader.INVENTORY_OPERATION);
        outPacket.encodeByte(exclRequestSent); // bool -> bExclRequestSent = 0
        outPacket.encodeByte(inventoryOperations.size());
        for (InventoryOperation op : inventoryOperations) {
            op.encode(outPacket);
        }
        return outPacket;
    }
}
