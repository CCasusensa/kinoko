package kinoko.provider;

import kinoko.provider.map.*;
import kinoko.provider.wz.*;
import kinoko.provider.wz.property.WzListProperty;
import kinoko.server.ServerConfig;
import kinoko.server.ServerConstants;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

public final class MapProvider {
    public static final Path MAP_WZ = Path.of(ServerConfig.WZ_DIRECTORY, "Map.wz");
    private static final Logger log = LogManager.getLogger(MapProvider.class);
    private static final Map<Integer, MapInfo> mapInfos = new HashMap<>();

    public static void initialize(boolean reset) {
        try (final WzReader reader = WzReader.build(MAP_WZ, new WzReaderConfig(WzConstants.WZ_GMS_IV, ServerConstants.GAME_VERSION))) {
            final WzPackage wzPackage = reader.readPackage();
            loadMapInfos(wzPackage);
        } catch (IOException | ProviderError e) {
            log.error("Exception caught while loading Map.wz", e);
        }
    }

    public static Optional<MapInfo> getMapInfo(int mapId) {
        if (!mapInfos.containsKey(mapId)) {
            return Optional.empty();
        }
        return Optional.of(mapInfos.get(mapId));
    }

    private static void loadMapInfos(WzPackage source) throws ProviderError, IOException {
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
                mapInfos.put(mapId, resolveMapInfo(mapId, mapEntry.getValue()));
            }
        }
    }

    private static MapInfo resolveMapInfo(int mapId, WzImage image) throws ProviderError {
        final Map<String, Object> props = image.getProperty().getItems();

        final List<Foothold> foothold = resolveFoothold((WzListProperty) props.get("foothold"));
        final List<LifeInfo> life = resolveLife((WzListProperty) props.get("life"));
        final List<PortalInfo> portal = resolvePortal((WzListProperty) props.get("portal"));
        final List<ReactorInfo> reactor = resolveReactor((WzListProperty) props.get("reactor"));

        if (!(props.get("info") instanceof WzListProperty infoProp)) {
            throw new ProviderError("Failed to resolve info property");
        }
        return MapInfo.from(mapId, foothold, life, portal, reactor, infoProp);
    }

    private static List<Foothold> resolveFoothold(WzListProperty listProp) throws ProviderError {
        if (listProp == null) {
            return List.of();
        }
        final List<Foothold> foothold = new ArrayList<>();
        for (var layerEntry : listProp.getItems().entrySet()) {
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

    private static List<LifeInfo> resolveLife(WzListProperty listProp) throws ProviderError {
        if (listProp == null) {
            return List.of();
        }
        final List<LifeInfo> life = new ArrayList<>();
        for (var lifeEntry : listProp.getItems().entrySet()) {
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

    private static List<PortalInfo> resolvePortal(WzListProperty listProp) throws ProviderError {
        if (listProp == null) {
            return List.of();
        }
        final List<PortalInfo> portal = new ArrayList<>();
        for (var portalEntry : listProp.getItems().entrySet()) {
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

    private static List<ReactorInfo> resolveReactor(WzListProperty listProp) throws ProviderError {
        if (listProp == null) {
            return List.of();
        }
        final List<ReactorInfo> reactor = new ArrayList<>();
        for (var reactorEntry : listProp.getItems().entrySet()) {
            if (!(reactorEntry.getValue() instanceof WzListProperty reactorProp)) {
                throw new ProviderError("Failed to resolve reactor property");
            }
            reactor.add(ReactorInfo.from(reactorProp));
        }
        return reactor;
    }
}
