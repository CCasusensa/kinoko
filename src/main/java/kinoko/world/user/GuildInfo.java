package kinoko.world.user;

import kinoko.server.guild.Guild;
import kinoko.server.guild.GuildRank;
import kinoko.server.packet.InPacket;
import kinoko.server.packet.OutPacket;
import kinoko.util.Encodable;

public final class GuildInfo implements Encodable {
    public static final GuildInfo EMPTY = new GuildInfo(0, "", GuildRank.NONE, (short) 0, (byte) 0, (short) 0, (byte) 0, 0, "");
    private final int guildId;
    private final String guildName;
    private final GuildRank guildRank;
    private final short markBg;
    private final byte markBgColor;
    private final short mark;
    private final byte markColor;
    private final int allianceId;
    private final String allianceName;

    public GuildInfo(int guildId, String guildName, GuildRank guildRank, short markBg, byte markBgColor, short mark, byte markColor, int allianceId, String allianceName) {
        this.guildId = guildId;
        this.guildName = guildName;
        this.guildRank = guildRank;
        this.markBg = markBg;
        this.markBgColor = markBgColor;
        this.mark = mark;
        this.markColor = markColor;
        this.allianceId = allianceId;
        this.allianceName = allianceName;
    }

    public int getGuildId() {
        return guildId;
    }

    public String getGuildName() {
        return guildName;
    }

    public GuildRank getGuildRank() {
        return guildRank;
    }

    public short getMarkBg() {
        return markBg;
    }

    public byte getMarkBgColor() {
        return markBgColor;
    }

    public short getMark() {
        return mark;
    }

    public byte getMarkColor() {
        return markColor;
    }

    public int getAllianceId() {
        return allianceId;
    }

    public String getAllianceName() {
        return allianceName;
    }

    @Override
    public void encode(OutPacket outPacket) {
        outPacket.encodeInt(guildId);
        outPacket.encodeString(guildName);
        outPacket.encodeByte(guildRank.getValue());
        outPacket.encodeShort(markBg);
        outPacket.encodeByte(markBgColor);
        outPacket.encodeShort(mark);
        outPacket.encodeByte(markColor);
        outPacket.encodeInt(allianceId);
        outPacket.encodeString(allianceName);
    }

    public static GuildInfo decode(InPacket inPacket) {
        final int guildId = inPacket.decodeInt();
        final String guildName = inPacket.decodeString();
        final GuildRank guildRank = GuildRank.getByValue(inPacket.decodeByte());
        final short markBg = inPacket.decodeShort();
        final byte markBgColor = inPacket.decodeByte();
        final short mark = inPacket.decodeShort();
        final byte markColor = inPacket.decodeByte();
        final int allianceId = inPacket.decodeInt();
        final String allianceName = inPacket.decodeString();
        return new GuildInfo(guildId, guildName, guildRank, markBg, markBgColor, mark, markColor, allianceId, allianceName);
    }

    public static GuildInfo from(Guild guild, int characterId) {
        return new GuildInfo(
                guild.getGuildId(),
                guild.getGuildName(),
                guild.getMember(characterId).getGuildRank(),
                guild.getMarkBg(),
                guild.getMarkBgColor(),
                guild.getMark(),
                guild.getMarkColor(),
                guild.getAllianceId(),
                guild.getAllianceName()
        );
    }
}