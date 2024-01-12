package kinoko.server.command;

import kinoko.packet.field.FieldPacket;
import kinoko.packet.world.WvsContext;
import kinoko.packet.world.message.Message;
import kinoko.util.Util;
import kinoko.world.field.Field;
import kinoko.world.user.User;

import java.util.Optional;
import java.util.Set;

public final class AdminCommands {
    @Command("dispose")
    public static void dispose(User user, String[] args) {
        user.dispose();
        user.write(WvsContext.message(Message.system("You have been disposed.")));
    }

    @Command("info")
    public static void info(User user, String[] args) {
        user.write(WvsContext.message(Message.system("Field ID  : " + user.getField().getFieldId())));
        user.write(WvsContext.message(Message.system(String.format("x : %d, y : %d, fh : %d", user.getX(), user.getY(), user.getFh()))));
    }

    @Command("map")
    public static void map(User user, String[] args) {
        if (args.length != 2 || !Util.isInteger(args[1])) {
            user.write(WvsContext.message(Message.system("Syntax : !map <field ID to warp to>")));
            return;
        }
        final Optional<Field> fieldResult = user.getConnectedServer().getFieldById(Integer.parseInt(args[1]));
        if (fieldResult.isEmpty()) {
            user.write(WvsContext.message(Message.system("Unknown field ID : " + args[1])));
            return;
        }
        user.write(WvsContext.message(Message.system("Warping to field ID  : " + args[1])));
        user.warp(fieldResult.get(), 0, false, false);
    }
}
