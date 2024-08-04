package kinoko.world.user;

import kinoko.server.guild.Guild;
import kinoko.server.guild.GuildRank;
import kinoko.server.packet.InPacket;
import kinoko.server.packet.OutPacket;
import kinoko.util.Encodable;

public final class GuildInfo implements Encodable {
    public static final GuildInfo EMPTY = new GuildInfo(0, "", false, (short) 0, (byte) 0, (short) 0, (byte) 0, 0, "");
    private final int guildId;
    private final String guildName;
    private final boolean master;
    private final short markBg;
    private final byte markBgColor;
    private final short mark;
    private final byte markColor;
    private final int allianceId;
    private final String allianceName;

    public GuildInfo(int guildId, String guildName, boolean master, short markBg, byte markBgColor, short mark, byte markColor, int allianceId, String allianceName) {
        this.guildId = guildId;
        this.guildName = guildName;
        this.master = master;
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

    public boolean isMaster() {
        return master;
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
        outPacket.encodeByte(master);
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
        final boolean master = inPacket.decodeBoolean();
        final short guildMarkBg = inPacket.decodeShort();
        final byte guildMarkBgColor = inPacket.decodeByte();
        final short guildMark = inPacket.decodeShort();
        final byte guildMarkColor = inPacket.decodeByte();
        final int allianceId = inPacket.decodeInt();
        final String allianceName = inPacket.decodeString();
        return new GuildInfo(guildId, guildName, master, guildMarkBg, guildMarkBgColor, guildMark, guildMarkColor, allianceId, allianceName);
    }

    public static GuildInfo from(Guild guild, int characterId) {
        return new GuildInfo(
                guild.getGuildId(),
                guild.getGuildName(),
                guild.getMember(characterId).getGuildRank() == GuildRank.MASTER,
                guild.getMarkBg(),
                guild.getMarkBgColor(),
                guild.getMark(),
                guild.getMarkColor(),
                guild.getAllianceId(),
                guild.getAllianceName()
        );
    }
}
