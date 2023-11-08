package kinoko.provider;

import kinoko.provider.map.*;
import kinoko.provider.wz.*;
import kinoko.provider.wz.property.WzListProperty;
import kinoko.provider.wz.property.WzProperty;
import kinoko.server.ServerConfig;
import kinoko.server.ServerConstants;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class MapProvider {
    private static final Map<Integer, MapInfo> mapInfos = new HashMap<>();

    public static void initialize() {
        final File wzFile = Path.of(ServerConfig.WZ_DIRECTORY, "Map.wz").toFile();
        try (final WzReader reader = WzReader.build(wzFile, new WzReaderConfig(WzConstants.WZ_GMS_IV, ServerConstants.GAME_VERSION))) {
            final WzPackage wzPackage = reader.readPackage();
            loadMapInfos(wzPackage);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static MapInfo getMapInfo(int id) {
        return mapInfos.get(id);
    }

    private static void loadMapInfos(WzPackage source) throws ProviderError {
        final WzDirectory mapDirectory = source.getDirectory().getDirectories().get("Map");
        if (mapDirectory == null) {
            throw new ProviderError("Could not resolve Map.wz/Map");
        }
        for (var dirEntry : mapDirectory.getDirectories().entrySet()) {
            final String directoryName = dirEntry.getKey();
            if (!directoryName.matches("Map[0-9]")) {
                continue;
            }
            for (var mapEntry : dirEntry.getValue().getImages().entrySet()) {
                final String imageName = mapEntry.getKey();
                final int mapId = Integer.parseInt(imageName.replace(".img", ""));
                final MapInfo mapInfo = resolveMapInfo(mapId, mapEntry.getValue());
                mapInfos.put(mapId, mapInfo);
            }
        }
    }

    private static MapInfo resolveMapInfo(int mapId, WzImage image) throws ProviderError {
        final Map<String, Object> props = image.getProperty().getItems();

        final List<Foothold> foothold = resolveFoothold((WzProperty) props.get("foothold"));
        final List<LifeInfo> life = resolveLife((WzProperty) props.get("life"));
        final List<PortalInfo> portal = resolvePortal((WzProperty) props.get("portal"));
        final List<ReactorInfo> reactor = resolveReactor((WzProperty) props.get("reactor"));

        if (!(props.get("info") instanceof WzListProperty infoProp)) {
            throw new ProviderError("Failed to resolve info property");
        }
        return MapInfo.from(mapId, foothold, life, portal, reactor, infoProp);
    }

    private static List<Foothold> resolveFoothold(WzProperty property) throws ProviderError {
        if (property == null) {
            return List.of();
        }
        if (!(property instanceof WzListProperty layerList)) {
            throw new ProviderError("Failed to resolve foothold property");
        }
        final List<Foothold> foothold = new ArrayList<>();
        for (var layerEntry : layerList.getItems().entrySet()) {
            final int layerId = Integer.parseInt(layerEntry.getKey());
            if (!(layerEntry.getValue() instanceof WzListProperty groupList)) {
                throw new ProviderError("Failed to resolve foothold property");
            }
            for (var groupEntry : groupList.getItems().entrySet()) {
                final int groupId = Integer.parseInt(groupEntry.getKey());
                if (!(groupEntry.getValue() instanceof WzListProperty footholdList)) {
                    throw new ProviderError("Failed to resolve foothold property");
                }
                for (var footholdEntry : footholdList.getItems().entrySet()) {
                    final int footholdId = Integer.parseInt(footholdEntry.getKey());
                    if (!(footholdEntry.getValue() instanceof WzListProperty footholdProp)) {
                        throw new ProviderError("Failed to resolve foothold property");
                    }
                    foothold.add(Foothold.from(layerId, groupId, footholdId, footholdProp));
                }
            }
        }
        return foothold;
    }

    private static List<LifeInfo> resolveLife(WzProperty property) throws ProviderError {
        if (property == null) {
            return List.of();
        }
        if (!(property instanceof WzListProperty layerList)) {
            throw new ProviderError("Failed to resolve life property");
        }
        final List<LifeInfo> life = new ArrayList<>();
        for (var lifeEntry : layerList.getItems().entrySet()) {
            if (!(lifeEntry.getValue() instanceof WzListProperty lifeProp)) {
                throw new ProviderError("Failed to resolve life property");
            }
            final LifeType lifeType = LifeType.fromString(lifeProp.get("type"));
            if (lifeType == null) {
                throw new ProviderError("Unknown life type : %s", lifeProp.get("type"));
            }
            life.add(LifeInfo.from(lifeType, lifeProp));
        }
        return life;
    }

    private static List<PortalInfo> resolvePortal(WzProperty property) throws ProviderError {
        if (property == null) {
            return List.of();
        }
        if (!(property instanceof WzListProperty layerList)) {
            throw new ProviderError("Failed to resolve portal property");
        }
        final List<PortalInfo> portal = new ArrayList<>();
        for (var portalEntry : layerList.getItems().entrySet()) {
            final int portalId = Integer.parseInt(portalEntry.getKey());
            if (!(portalEntry.getValue() instanceof WzListProperty portalProp)) {
                throw new ProviderError("Failed to resolve portal property");
            }
            final PortalType portalType = PortalType.fromInt(portalProp.get("pt"));
            if (portalType == null) {
                throw new ProviderError("Unknown portal type : %d", portalProp.get("pt"));
            }
            portal.add(PortalInfo.from(portalType, portalId, portalProp));
        }
        return portal;
    }

    private static List<ReactorInfo> resolveReactor(WzProperty property) throws ProviderError {
        if (property == null) {
            return List.of();
        }
        if (!(property instanceof WzListProperty layerList)) {
            throw new ProviderError("Failed to resolve reactor property");
        }
        final List<ReactorInfo> reactor = new ArrayList<>();
        for (var reactorEntry : layerList.getItems().entrySet()) {
            if (!(reactorEntry.getValue() instanceof WzListProperty reactorProp)) {
                throw new ProviderError("Failed to resolve reactor property");
            }
            reactor.add(ReactorInfo.from(reactorProp));
        }
        return reactor;
    }
}
