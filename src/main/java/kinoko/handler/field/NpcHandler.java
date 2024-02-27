package kinoko.handler.field;

import kinoko.handler.Handler;
import kinoko.packet.field.NpcPacket;
import kinoko.server.header.InHeader;
import kinoko.server.packet.InPacket;
import kinoko.world.field.Field;
import kinoko.world.life.MovePath;
import kinoko.world.life.npc.Npc;
import kinoko.world.user.User;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Optional;

public final class NpcHandler {
    private static final Logger log = LogManager.getLogger(NpcHandler.class);

    @Handler(InHeader.NPC_MOVE)
    public static void handleNpcMove(User user, InPacket inPacket) {
        final int objectId = inPacket.decodeInt(); // dwNpcId
        final byte oneTimeAction = inPacket.decodeByte(); // nOneTimeAction
        final byte chatIndex = inPacket.decodeByte(); // nChatIdx

        final Field field = user.getField();
        final Optional<Npc> npcResult = field.getNpcPool().getById(objectId);
        if (npcResult.isEmpty()) {
            log.error("Received NPC_MOVE for invalid object with ID : {}", objectId);
            return;
        }
        final Npc npc = npcResult.get();

        final MovePath movePath = npc.isMove() ? MovePath.decode(inPacket) : null;
        if (movePath != null) {
            movePath.applyTo(npc);
        }
        field.broadcastPacket(NpcPacket.move(npc, oneTimeAction, chatIndex, movePath));
    }
}
