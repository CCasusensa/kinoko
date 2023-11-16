package kinoko.world.field;

import kinoko.server.packet.OutPacket;
import kinoko.world.user.User;

public interface ControlledObject {
    User getController();

    void setController(User user);

    OutPacket changeControllerPacket(boolean forController);
}
