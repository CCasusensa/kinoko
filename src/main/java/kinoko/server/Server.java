package kinoko.server;

import kinoko.database.DatabaseManager;
import kinoko.provider.*;
import kinoko.server.command.CommandProcessor;
import kinoko.server.crypto.MapleCrypto;
import kinoko.server.event.EventScheduler;
import kinoko.server.node.CentralServerNode;
import kinoko.server.node.ChannelServerNode;
import kinoko.server.script.ScriptDispatcher;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Duration;
import java.time.Instant;

public final class Server {
    private static final Logger log = LogManager.getLogger(Server.class);
    private static CentralServerNode centralServerNode;

    public static void main(String[] args) throws Exception {
        Server.initialize();
    }

    private static void initialize() throws Exception {
        // Initialize providers
        Instant start = Instant.now();
        ItemProvider.initialize();      // Character.wz + Item.wz
        SkillProvider.initialize();     // Skill.wz + Morph.wz
        MapProvider.initialize();       // Map.wz
        MobProvider.initialize();       // Mob.wz
        NpcProvider.initialize();       // Npc.wz
        ReactorProvider.initialize();   // Reactor.wz
        QuestProvider.initialize();     // Quest.wz
        StringProvider.initialize();    // String.wz
        EtcProvider.initialize();       // Etc.wz
        ShopProvider.initialize();      // data/shop
        RewardProvider.initialize();    // data/reward
        System.gc();
        log.info("Loaded providers in {} milliseconds", Duration.between(start, Instant.now()).toMillis());

        // Initialize database
        start = Instant.now();
        DatabaseManager.initialize();
        log.info("Loaded database connection in {} milliseconds", Duration.between(start, Instant.now()).toMillis());

        // Initialize server classes
        start = Instant.now();
        MapleCrypto.initialize();
        EventScheduler.initialize();
        ScriptDispatcher.initialize();
        CommandProcessor.initialize();
        log.info("Loaded server classes in {} milliseconds", Duration.between(start, Instant.now()).toMillis());

        // Initialize nodes
        centralServerNode = new CentralServerNode();
        EventScheduler.submit(() -> {
            try {
                centralServerNode.initialize();
            } catch (Exception e) {
                log.error("Failed to initialize central server node", e);
                System.exit(1);
            }
        });
        for (int channelId = 0; channelId < ServerConfig.CHANNELS_PER_WORLD; channelId++) {
            final ChannelServerNode channelServerNode = new ChannelServerNode(channelId, ServerConstants.CHANNEL_PORT + channelId);
            EventScheduler.submit(() -> {
                try {
                    channelServerNode.initialize();
                } catch (Exception e) {
                    log.error("Failed to initialize channel server node {}", channelServerNode.getChannelId() + 1, e);
                    System.exit(1);
                }
            });
        }

        // Setup shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                Server.shutdown();
            } catch (Exception e) {
                log.error("Exception caught while shutting down Server", e);
                throw new RuntimeException(e);
            }
        }));
    }

    private static void shutdown() throws Exception {
        log.info("Shutting down Server");
        centralServerNode.shutdown();
        ScriptDispatcher.shutdown();
        EventScheduler.shutdown();
        DatabaseManager.shutdown();
        LogManager.shutdown();
    }
}
