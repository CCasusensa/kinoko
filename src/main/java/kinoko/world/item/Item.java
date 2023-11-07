package kinoko.world.item;

import kinoko.server.packet.OutPacket;
import kinoko.util.FileTime;
import kinoko.world.Encodable;
import lombok.Data;

@Data
public class Item implements Encodable {
    private final long itemSn;
    private final int itemId;
    private final int itemType;
    private final boolean cash;
    private short quantity;
    private short attribute;
    private String title;

    protected void encodeBase(OutPacket outPacket) {
        // GW_ItemSlotBase::RawDecode
        outPacket.encodeInt(getItemId()); // nItemID
        outPacket.encodeByte(isCash());
        if (isCash()) {
            outPacket.encodeLong(getItemSn()); // liCashItemSN
        }
        outPacket.encodeFT(FileTime.MAX_TIME); // dateExpire
    }

    @Override
    public void encode(OutPacket outPacket) {
        assert getItemType() == 2;
        outPacket.encodeByte(getItemType()); // nType
        encodeBase(outPacket);

        // GW_ItemSlotBundle::RawDecode
        outPacket.encodeShort(getQuantity()); // nNumber
        outPacket.encodeString(getTitle()); // sTitle
        outPacket.encodeShort(getAttribute()); // nAttribute

        final int itemPrefix = getItemId() / 10000;
        if (itemPrefix == 207 || itemPrefix == 233) {
            outPacket.encodeLong(getItemSn());
        }
    }
}
