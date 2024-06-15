package kinoko.world.user.stat;

import kinoko.server.packet.OutPacket;

import java.time.Instant;

public class TwoStateTemporaryStat extends TemporaryStatOption {
    private final TwoStateType twoStateType;
    private final Instant currentTime;

    public TwoStateTemporaryStat(TwoStateType twoStateType, int nOption, int rOption, int tOption) {
        super(nOption, rOption, tOption);
        this.twoStateType = twoStateType;
        this.currentTime = Instant.now();
    }

    public TwoStateTemporaryStat(TwoStateType twoStateType, int nOption, int rOption, int tOption, Instant expireTime) {
        super(nOption, rOption, tOption, expireTime);
        this.twoStateType = twoStateType;
        this.currentTime = Instant.now();
    }

    public final TwoStateType getType() {
        return twoStateType;
    }

    @Override
    public void encode(OutPacket outPacket) {
        // TemporaryStatBase<long>::DecodeForClient
        outPacket.encodeInt(nOption); // m_value
        outPacket.encodeInt(rOption); // m_reason
        encodeTime(outPacket, expireTime); // tLastUpdated

        if (twoStateType == TwoStateType.EXPIRE_BASED_ON_CURRENT_TIME) {
            encodeTime(outPacket, currentTime); // tCurrentTime
        }

        if (twoStateType != TwoStateType.NO_EXPIRE) {
            outPacket.encodeShort(tOption / 1000); // usExpireTerm
        }
    }

    private void encodeTime(OutPacket outPacket, Instant time) {
        // tLastUpdated = `anonymous namespace'::DecodeTime
        final Instant now = Instant.now();
        if (time.isAfter(now)) {
            outPacket.encodeByte(true);
            outPacket.encodeInt(time.toEpochMilli() - now.toEpochMilli());
        } else {
            outPacket.encodeByte(false);
            outPacket.encodeInt(now.toEpochMilli() - time.toEpochMilli());
        }
    }

    public static TwoStateTemporaryStat ofTwoState(CharacterTemporaryStat cts, int nOption, int rOption, int tOption) {
        switch (cts) {
            case RideVehicle -> {
                return new TwoStateTemporaryStat(TwoStateType.NO_EXPIRE, nOption, rOption, tOption);
            }
            case PartyBooster -> {
                return new TwoStateTemporaryStat(TwoStateType.EXPIRE_BASED_ON_CURRENT_TIME, nOption, rOption, tOption);
            }
            case GuidedBullet -> {
                return new GuidedBullet(nOption, rOption, 0, tOption);
            }
            default -> {
                return new TwoStateTemporaryStat(TwoStateType.EXPIRE_BASED_ON_LAST_UPDATED_TIME, nOption, rOption, tOption);
            }
        }
    }
}
