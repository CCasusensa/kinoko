package kinoko.world.field;

import kinoko.provider.MobProvider;
import kinoko.provider.NpcProvider;
import kinoko.provider.map.LifeInfo;
import kinoko.provider.map.MapInfo;
import kinoko.provider.map.PortalInfo;
import kinoko.provider.mob.MobInfo;
import kinoko.provider.npc.NpcInfo;
import kinoko.server.packet.OutPacket;
import kinoko.util.Util;
import kinoko.world.life.Life;
import kinoko.world.life.mob.Mob;
import kinoko.world.life.npc.Npc;
import kinoko.world.user.User;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class Field {
    private final MapInfo mapInfo;
    private final AtomicInteger objectIdCounter = new AtomicInteger(1);
    private final Map<Integer, Life> lifes = new ConcurrentHashMap<>(); // objectId -> Life
    private final Map<Integer, User> users = new ConcurrentHashMap<>(); // characterId -> User

    public Field(MapInfo mapInfo) {
        this.mapInfo = mapInfo;
    }

    public int getFieldId() {
        return mapInfo.getMapId();
    }

    public Optional<PortalInfo> getPortalById(int portalId) {
        return mapInfo.getPortalById(portalId);
    }

    public Optional<PortalInfo> getPortalByName(String name) {
        return mapInfo.getPortalByName(name);
    }

    public int getNewObjectId() {
        return objectIdCounter.getAndIncrement();
    }

    public Optional<Life> getLifeById(int objectId) {
        if (!lifes.containsKey(objectId)) {
            return Optional.empty();
        }
        return Optional.of(lifes.get(objectId));
    }

    public void addLife(Life life) {
        life.setObjectId(getNewObjectId());
        lifes.put(life.getObjectId(), life);
        broadcastPacket(life.enterFieldPacket());
        // Handle controller
        if (!(life instanceof ControlledObject controlledLife)) {
            return;
        }
        final Optional<User> controllerResult = Util.getRandomFromCollection(users.values());
        if (controllerResult.isEmpty()) {
            return;
        }
        final User controller = controllerResult.get();
        controlledLife.setController(controller);
        controller.getClient().write(controlledLife.changeControllerPacket(true));
        broadcastPacket(controlledLife.changeControllerPacket(false), controller);
    }

    public void removeLife(int objectId) {
        final Life removed = lifes.remove(objectId);
        if (removed == null) {
            return;
        }
        broadcastPacket(removed.leaveFieldPacket());
    }

    public void addUser(User user) {
        users.put(user.getCharacterId(), user);
        // Set controller
        for (Life life : lifes.values()) {
            user.write(life.enterFieldPacket());
            if (!(life instanceof ControlledObject controlledLife)) {
                continue;
            }
            if (controlledLife.getController() == null) {
                controlledLife.setController(user);
                user.write(controlledLife.changeControllerPacket(true));
            } else {
                user.write(controlledLife.changeControllerPacket(false));
            }
        }
    }

    public void removeUser(int characterId) {
        final User removed = users.remove(characterId);
        if (removed == null) {
            return;
        }
        broadcastPacket(removed.leaveFieldPacket());
        // Handle controller change
        final Optional<User> nextControllerResult = Util.getRandomFromCollection(users.values());
        for (Life life : lifes.values()) {
            if (!(life instanceof ControlledObject controlledLife)) {
                continue;
            }
            if (controlledLife.getController() != removed) {
                continue;
            }
            if (nextControllerResult.isEmpty()) {
                controlledLife.setController(null);
                continue;
            }
            final User nextController = nextControllerResult.get();
            controlledLife.setController(nextController);
            nextController.getClient().write(controlledLife.changeControllerPacket(true));
            broadcastPacket(controlledLife.changeControllerPacket(false), nextController);
        }
    }

    public void broadcastPacket(OutPacket outPacket) {
        broadcastPacket(outPacket, null);
    }

    public void broadcastPacket(OutPacket outPacket, User except) {
        for (User user : users.values()) {
            if (except != null && user.getCharacterId() == except.getCharacterId()) {
                continue;
            }
            user.write(outPacket);
        }
    }

    public static Field from(MapInfo mapInfo) {
        final Field field = new Field(mapInfo);
        for (LifeInfo lifeInfo : mapInfo.getLifeInfos()) {
            switch (lifeInfo.getLifeType()) {
                case NPC -> {
                    final Optional<NpcInfo> npcInfoResult = NpcProvider.getNpcInfo(lifeInfo.getTemplateId());
                    final Npc npc = Npc.from(lifeInfo, npcInfoResult.orElseThrow());
                    npc.setField(field);
                    field.addLife(npc);
                }
                case MOB -> {
                    final Optional<MobInfo> mobInfoResult = MobProvider.getMobInfo(lifeInfo.getTemplateId());
                    final Mob mob = Mob.from(lifeInfo, mobInfoResult.orElseThrow());
                    mob.setField(field);
                    field.addLife(mob);
                }
            }
        }
        return field;
    }
}
