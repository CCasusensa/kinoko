package kinoko.database.cassandra;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import kinoko.database.AccountAccessor;
import kinoko.database.cassandra.table.AccountTable;
import kinoko.database.cassandra.table.IdTable;
import kinoko.server.ServerConfig;
import kinoko.world.Account;
import org.mindrot.jbcrypt.BCrypt;

import java.util.Optional;

import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.*;

public final class CassandraAccountAccessor extends CassandraAccessor implements AccountAccessor {
    public CassandraAccountAccessor(CqlSession session, String keyspace) {
        super(session, keyspace);
    }

    private Account loadAccount(Row row) {
        final int accountId = row.getInt(AccountTable.ACCOUNT_ID);
        final String username = row.getString(AccountTable.USERNAME);
        final String secondaryPassword = row.getString(AccountTable.SECONDARY_PASSWORD);

        final Account account = new Account(accountId, username);
        account.setHasSecondaryPassword(secondaryPassword != null && !secondaryPassword.isEmpty());
        account.setSlotCount(row.getInt(AccountTable.SLOT_COUNT));
        account.setNxCredit(row.getInt(AccountTable.NX_CREDIT));
        account.setNxPrepaid(row.getInt(AccountTable.NX_PREPAID));
        account.setMaplePoint(row.getInt(AccountTable.MAPLE_POINT));
        return account;
    }

    private String lowerUsername(String username) {
        return username.toLowerCase();
    }

    private String hashPassword(String password) {
        return BCrypt.hashpw(password, BCrypt.gensalt());
    }

    private boolean checkHashedPassword(String password, String hashedPassword) {
        return BCrypt.checkpw(password, hashedPassword);
    }

    @Override
    public Optional<Account> getAccountById(int accountId) {
        final ResultSet selectResult = getSession().execute(
                selectFrom(getKeyspace(), AccountTable.getTableName()).all()
                        .whereColumn(AccountTable.ACCOUNT_ID).isEqualTo(literal(accountId))
                        .build()
        );
        for (Row row : selectResult) {
            return Optional.of(loadAccount(row));
        }
        return Optional.empty();
    }

    @Override
    public Optional<Account> getAccountByUsername(String username) {
        final ResultSet selectResult = getSession().execute(
                selectFrom(getKeyspace(), AccountTable.getTableName()).all()
                        .whereColumn(AccountTable.USERNAME).isEqualTo(literal(lowerUsername(username)))
                        .build()
        );
        for (Row row : selectResult) {
            return Optional.of(loadAccount(row));
        }
        return Optional.empty();
    }

    @Override
    public boolean checkPassword(Account account, String password, boolean secondary) {
        final String columnName = secondary ? AccountTable.SECONDARY_PASSWORD : AccountTable.PASSWORD;
        final ResultSet selectResult = getSession().execute(
                selectFrom(getKeyspace(), AccountTable.getTableName()).all()
                        .column(columnName)
                        .whereColumn(AccountTable.ACCOUNT_ID).isEqualTo(literal(account.getId()))
                        .build()
        );
        for (Row row : selectResult) {
            final String hashedPassword = row.getString(columnName);
            if (hashedPassword == null) {
                continue;
            }
            if (checkHashedPassword(password, hashedPassword)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean savePassword(Account account, String oldPassword, String newPassword, boolean secondary) {
        final String columnName = secondary ? AccountTable.SECONDARY_PASSWORD : AccountTable.PASSWORD;
        final ResultSet selectResult = getSession().execute(
                selectFrom(getKeyspace(), AccountTable.getTableName()).all()
                        .column(columnName)
                        .whereColumn(AccountTable.ACCOUNT_ID).isEqualTo(literal(account.getId()))
                        .build()
        );
        for (Row row : selectResult) {
            final String hashedOldPassword = row.getString(columnName);
            if (hashedOldPassword == null || checkHashedPassword(oldPassword, hashedOldPassword)) {
                final ResultSet updateResult = getSession().execute(
                        update(getKeyspace(), AccountTable.getTableName())
                                .setColumn(columnName, literal(hashPassword(newPassword)))
                                .whereColumn(AccountTable.ACCOUNT_ID).isEqualTo(literal(account.getId()))
                                .build()
                );
                return updateResult.wasApplied();
            }
        }
        return false;
    }

    @Override
    public synchronized boolean newAccount(String username, String password) {
        final Optional<Integer> accountId = getNextId(IdTable.ACCOUNT_TABLE);
        if (accountId.isEmpty()) {
            return false;
        }
        if (getAccountByUsername(username).isPresent()) {
            return false;
        }
        final ResultSet insertResult = getSession().execute(
                insertInto(getKeyspace(), AccountTable.getTableName())
                        .value(AccountTable.ACCOUNT_ID, literal(accountId.get()))
                        .value(AccountTable.USERNAME, literal(lowerUsername(username)))
                        .value(AccountTable.PASSWORD, literal(hashPassword(password)))
                        .value(AccountTable.SLOT_COUNT, literal(ServerConfig.CHARACTER_BASE_SLOTS))
                        .value(AccountTable.NX_CREDIT, literal(0))
                        .value(AccountTable.NX_PREPAID, literal(0))
                        .value(AccountTable.MAPLE_POINT, literal(0))
                        .ifNotExists()
                        .build()
        );
        return insertResult.wasApplied();
    }

    @Override
    public boolean saveAccount(Account account) {
        final ResultSet updateResult = getSession().execute(
                update(getKeyspace(), AccountTable.getTableName())
                        .setColumn(AccountTable.SLOT_COUNT, literal(account.getSlotCount()))
                        .setColumn(AccountTable.NX_CREDIT, literal(account.getNxCredit()))
                        .setColumn(AccountTable.NX_PREPAID, literal(account.getNxPrepaid()))
                        .setColumn(AccountTable.MAPLE_POINT, literal(account.getMaplePoint()))
                        .whereColumn(AccountTable.ACCOUNT_ID).isEqualTo(literal(account.getId()))
                        .build()
        );
        return updateResult.wasApplied();
    }
}
