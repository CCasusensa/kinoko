package kinoko.server.netty;

import kinoko.server.Client;
import kinoko.server.ServerConstants;
import kinoko.util.Tuple;
import kinoko.world.Account;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class LoginServer extends NettyServer {
    private final Map<Integer, Tuple<Client, Account>> connectedAccounts = new ConcurrentHashMap<>();

    @Override
    public int getPort() {
        return ServerConstants.LOGIN_PORT;
    }

    public boolean isConnected(Account account) {
        return connectedAccounts.containsKey(account.getId());
    }

    public void removeAccount(Account account) {
        connectedAccounts.remove(account.getId());
    }

    public boolean isAuthenticated(Client c, Account account) {
        final Tuple<Client, Account> tuple = connectedAccounts.get(account.getId());
        if (tuple == null) {
            return false;
        }
        return c.getMachineId() != null &&
                Arrays.equals(c.getMachineId(), tuple.getLeft().getMachineId());
    }

    public void setAuthenticated(Client c, Account account) {
        connectedAccounts.put(account.getId(), new Tuple<>(c, account));
    }
}
