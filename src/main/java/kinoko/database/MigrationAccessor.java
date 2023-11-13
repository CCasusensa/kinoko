package kinoko.database;

import kinoko.server.MigrationRequest;

import java.util.Optional;

public interface MigrationAccessor {

    boolean hasMigrationRequest(int accountId);

    boolean submitMigrationRequest(MigrationRequest migrationRequest);

    Optional<MigrationRequest> fetchMigrationRequest(int characterId);
}
