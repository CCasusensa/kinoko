package kinoko.server.node;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import kinoko.packet.CentralPacket;
import kinoko.packet.stage.LoginPacket;
import kinoko.server.ServerConfig;
import kinoko.server.ServerConstants;
import kinoko.server.event.EventManager;
import kinoko.server.event.EventState;
import kinoko.server.event.EventType;
import kinoko.server.field.ChannelFieldStorage;
import kinoko.server.field.Instance;
import kinoko.server.field.InstanceStorage;
import kinoko.server.messenger.MessengerRequest;
import kinoko.server.migration.MigrationInfo;
import kinoko.server.migration.TransferInfo;
import kinoko.server.netty.*;
import kinoko.server.packet.OutPacket;
import kinoko.server.party.PartyRequest;
import kinoko.server.user.RemoteUser;
import kinoko.world.field.Field;
import kinoko.world.user.Account;
import kinoko.world.user.User;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class ChannelServerNode extends ServerNode {
    private static final Logger log = LogManager.getLogger(ChannelServerNode.class);
    private final AtomicInteger requestIdCounter = new AtomicInteger(1);
    private final ConcurrentHashMap<Integer, CompletableFuture<?>> requestFutures = new ConcurrentHashMap<>();
    private final ChannelFieldStorage fieldStorage = new ChannelFieldStorage();
    private final InstanceStorage instanceStorage = new InstanceStorage();
    private final EventManager eventManager = new EventManager();
    private final int channelId;
    private final int channelPort;
    private ChannelFuture centralClientFuture;
    private ChannelFuture channelServerFuture;

    public ChannelServerNode(int channelId, int channelPort) {
        this.channelId = channelId;
        this.channelPort = channelPort;
    }

    public int getChannelId() {
        return channelId;
    }

    public int getChannelPort() {
        return channelPort;
    }

    public int getNewRequestId() {
        return requestIdCounter.getAndIncrement();
    }


    // MIGRATION METHODS -----------------------------------------------------------------------------------------------

    public CompletableFuture<Optional<MigrationInfo>> submitMigrationRequest(int accountId, int characterId, byte[] machineId, byte[] clientKey) {
        final int requestId = getNewRequestId();
        final CompletableFuture<Optional<MigrationInfo>> migrationRequestFuture = new CompletableFuture<>();
        requestFutures.put(requestId, migrationRequestFuture);
        centralClientFuture.channel().writeAndFlush(CentralPacket.migrateRequest(requestId, accountId, characterId, machineId, clientKey));
        return migrationRequestFuture;
    }

    @SuppressWarnings("unchecked")
    public void completeMigrationRequest(int requestId, MigrationInfo migrationInfo) {
        final CompletableFuture<Optional<MigrationInfo>> migrationRequestFuture = (CompletableFuture<Optional<MigrationInfo>>) requestFutures.remove(requestId);
        if (migrationRequestFuture != null) {
            migrationRequestFuture.complete(Optional.ofNullable(migrationInfo));
        }
    }

    public CompletableFuture<Optional<TransferInfo>> submitTransferRequest(MigrationInfo migrationInfo) {
        final int requestId = getNewRequestId();
        final CompletableFuture<Optional<TransferInfo>> transferRequestFuture = new CompletableFuture<>();
        requestFutures.put(requestId, transferRequestFuture);
        centralClientFuture.channel().writeAndFlush(CentralPacket.transferRequest(requestId, migrationInfo));
        return transferRequestFuture;
    }

    @SuppressWarnings("unchecked")
    public void completeTransferRequest(int requestId, TransferInfo transferInfo) {
        final CompletableFuture<Optional<TransferInfo>> transferRequestFuture = (CompletableFuture<Optional<TransferInfo>>) requestFutures.remove(requestId);
        if (transferRequestFuture != null) {
            transferRequestFuture.complete(Optional.ofNullable(transferInfo));
        }
    }


    // USER METHODS ----------------------------------------------------------------------------------------------------

    public boolean isConnected(User user) {
        return clientStorage.isConnected(user);
    }

    public Set<User> getConnectedUsers() {
        return clientStorage.getConnectedUsers();
    }

    public Optional<User> getUserByCharacterId(int characterId) {
        return clientStorage.getUserByCharacterId(characterId);
    }

    public void notifyUserConnect(User user) {
        centralClientFuture.channel().writeAndFlush(CentralPacket.userConnect(RemoteUser.from(user)));
    }

    public void notifyUserUpdate(User user) {
        centralClientFuture.channel().writeAndFlush(CentralPacket.userUpdate(RemoteUser.from(user)));
    }

    public void notifyUserDisconnect(User user) {
        centralClientFuture.channel().writeAndFlush(CentralPacket.userDisconnect(RemoteUser.from(user)));
    }

    public void submitUserPacketRequest(String characterName, OutPacket remotePacket) {
        centralClientFuture.channel().writeAndFlush(CentralPacket.userPacketRequest(characterName, remotePacket));
    }

    public void submitUserPacketReceive(int characterId, OutPacket remotePacket) {
        centralClientFuture.channel().writeAndFlush(CentralPacket.userPacketReceive(characterId, remotePacket));
    }

    public void submitUserPacketBroadcast(Set<Integer> characterIds, OutPacket remotePacket) {
        centralClientFuture.channel().writeAndFlush(CentralPacket.userPacketBroadcast(characterIds, remotePacket));
    }

    public CompletableFuture<Set<RemoteUser>> submitUserQueryRequest(Set<String> characterNames) {
        final int requestId = getNewRequestId();
        final CompletableFuture<Set<RemoteUser>> userRequestFuture = new CompletableFuture<>();
        requestFutures.put(requestId, userRequestFuture);
        centralClientFuture.channel().writeAndFlush(CentralPacket.userQueryRequest(requestId, characterNames));
        return userRequestFuture;
    }

    @SuppressWarnings("unchecked")
    public void completeUserQueryRequest(int requestId, Set<RemoteUser> remoteUsers) {
        final CompletableFuture<Set<RemoteUser>> userRequestFuture = (CompletableFuture<Set<RemoteUser>>) requestFutures.remove(requestId);
        if (userRequestFuture != null) {
            userRequestFuture.complete(remoteUsers);
        }
    }

    public void submitChannelPacketBroadcast(OutPacket remotePacket) {
        for (User user : getConnectedUsers()) {
            user.write(remotePacket);
        }
    }

    public void submitServerPacketBroadcast(OutPacket remotePacket) {
        centralClientFuture.channel().writeAndFlush(CentralPacket.serverPacketBroadcast(remotePacket));
    }


    // OTHER CENTRAL REQUESTS ------------------------------------------------------------------------------------------

    public void submitPartyRequest(User user, PartyRequest partyRequest) {
        centralClientFuture.channel().writeAndFlush(CentralPacket.partyRequest(user.getCharacterId(), partyRequest));
    }

    public void submitMessengerRequest(User user, MessengerRequest messengerRequest) {
        centralClientFuture.channel().writeAndFlush(CentralPacket.messengerRequest(user.getCharacterId(), messengerRequest));
    }


    // FIELD METHODS ---------------------------------------------------------------------------------------------------

    public Optional<Field> getFieldById(int mapId) {
        return fieldStorage.getFieldById(mapId);
    }


    // INSTANCE METHODS ------------------------------------------------------------------------------------------------

    public Optional<Instance> createInstance(List<Integer> mapIds, int returnMap, int timeLimit) {
        return instanceStorage.createInstance(this, mapIds, returnMap, timeLimit);
    }

    public boolean removeInstance(Instance instance) {
        return instanceStorage.removeInstance(instance);
    }


    // EVENT METHODS ---------------------------------------------------------------------------------------------------

    public Optional<EventState> getEventState(EventType eventType) {
        return eventManager.getEventState(eventType);
    }


    // OVERRIDES -------------------------------------------------------------------------------------------------------

    @Override
    public boolean isConnected(Account account) {
        return clientStorage.isConnected(account);
    }

    @Override
    public void initialize() throws InterruptedException, UnknownHostException {
        // Initialize event manager
        eventManager.initialize(fieldStorage);

        // Start channel server
        final ChannelServerNode self = this;
        channelServerFuture = startServer(new ChannelInitializer<>() {
            @Override
            protected void initChannel(SocketChannel ch) {
                ch.pipeline().addLast(new PacketDecoder(), new ChannelPacketHandler(), new PacketEncoder());
                final Client c = new Client(self, ch);
                c.setSendIv(getNewIv());
                c.setRecvIv(getNewIv());
                c.setClientKey(getNewClientKey());
                c.write(LoginPacket.connect(c.getRecvIv(), c.getSendIv()));
                ch.attr(NettyClient.CLIENT_KEY).set(c);
            }
        }, channelPort);
        channelServerFuture.sync();
        log.info("Channel {} listening on port {}", channelId + 1, channelPort);

        // Start central client
        centralClientFuture = startClient(new ChannelInitializer<>() {
            @Override
            protected void initChannel(SocketChannel ch) {
                ch.pipeline().addLast(new CentralPacketDecoder(), new CentralClientHandler(self), new CentralPacketEncoder());
                ch.attr(NettyContext.CONTEXT_KEY).set(new NettyContext());
            }
        }, InetAddress.getByAddress(ServerConstants.CENTRAL_HOST), ServerConstants.CENTRAL_PORT);
        centralClientFuture.sync();
    }

    @Override
    public void shutdown() throws InterruptedException {
        // Close client channels
        startShutdown();
        for (Client client : clientStorage.getConnectedClients()) {
            client.close();
        }

        // Clean up
        eventManager.shutdown();
        fieldStorage.clear();
        instanceStorage.clear();

        // Close channel server
        channelServerFuture.channel().close().sync();
        getShutdownFuture().orTimeout(ServerConfig.SHUTDOWN_TIMEOUT, TimeUnit.SECONDS);
        log.info("Channel {} closed", channelId + 1);

        // Close central client
        centralClientFuture.channel().writeAndFlush(CentralPacket.shutdownResult(channelId, true));
        centralClientFuture.channel().close().sync();
        log.info("Central client {} closed", channelId + 1);
    }
}
