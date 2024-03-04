package kinoko.database.cassandra;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.type.codec.registry.CodecRegistry;
import kinoko.database.CharacterAccessor;
import kinoko.database.cassandra.table.CharacterTable;
import kinoko.database.cassandra.table.IdTable;
import kinoko.world.item.Inventory;
import kinoko.world.item.InventoryManager;
import kinoko.world.quest.QuestManager;
import kinoko.world.quest.QuestRecord;
import kinoko.world.skill.SkillManager;
import kinoko.world.skill.SkillRecord;
import kinoko.world.user.AvatarData;
import kinoko.world.user.CharacterData;
import kinoko.world.user.funckey.FuncKeyManager;
import kinoko.world.user.stat.CharacterStat;
import kinoko.world.user.stat.SecondaryStat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.*;

public final class CassandraCharacterAccessor extends CassandraAccessor implements CharacterAccessor {

    public CassandraCharacterAccessor(CqlSession session, String keyspace) {
        super(session, keyspace);
    }

    private CharacterData loadCharacterData(Row row) {
        final int accountId = row.getInt(CharacterTable.ACCOUNT_ID);

        final CharacterData cd = new CharacterData(accountId);

        final CharacterStat cs = row.get(CharacterTable.CHARACTER_STAT, CharacterStat.class);
        cs.setId(row.getInt(CharacterTable.CHARACTER_ID));
        cs.setName(row.getString(CharacterTable.CHARACTER_NAME));
        cd.setCharacterStat(cs);

        final InventoryManager im = new InventoryManager();
        im.setEquipped(row.get(CharacterTable.CHARACTER_EQUIPPED, Inventory.class));
        im.setEquipInventory(row.get(CharacterTable.EQUIP_INVENTORY, Inventory.class));
        im.setConsumeInventory(row.get(CharacterTable.CONSUME_INVENTORY, Inventory.class));
        im.setInstallInventory(row.get(CharacterTable.INSTALL_INVENTORY, Inventory.class));
        im.setEtcInventory(row.get(CharacterTable.ETC_INVENTORY, Inventory.class));
        im.setCashInventory(row.get(CharacterTable.CASH_INVENTORY, Inventory.class));
        im.setMoney(row.getInt(CharacterTable.MONEY));
        cd.setInventoryManager(im);

        final SkillManager sm = new SkillManager();
        final Set<SkillRecord> skillRecords = row.getSet(CharacterTable.SKILL_RECORDS, SkillRecord.class);
        if (skillRecords != null) {
            for (SkillRecord sr : skillRecords) {
                sm.addSkill(sr);
            }
        }
        cd.setSkillManager(sm);

        final QuestManager qm = new QuestManager();
        final Set<QuestRecord> questRecords = row.getSet(CharacterTable.QUEST_RECORDS, QuestRecord.class);
        if (questRecords != null) {
            for (QuestRecord qr : questRecords) {
                qm.addQuestRecord(qr);
            }
        }
        cd.setQuestManager(qm);

        final FuncKeyManager fkm = row.get(CharacterTable.FUNC_KEY_MAN, FuncKeyManager.class);
        cd.setFuncKeyManager(fkm);

        cd.setItemSnCounter(new AtomicInteger(row.getInt(CharacterTable.ITEM_SN_COUNTER)));
        cd.setFriendMax(row.getInt(CharacterTable.FRIEND_MAX));
        return cd;
    }

    private String lowerName(String name) {
        return name.toLowerCase();
    }

    @Override
    public Optional<Integer> nextCharacterId() {
        return getNextId(IdTable.CHARACTER_TABLE);
    }

    @Override
    public boolean checkCharacterNameAvailable(String name) {
        final ResultSet selectResult = getSession().execute(
                selectFrom(getKeyspace(), CharacterTable.getTableName()).all()
                        .whereColumn(CharacterTable.CHARACTER_NAME_INDEX).isEqualTo(literal(lowerName(name)))
                        .build()
        );
        for (Row row : selectResult) {
            final String existingName = row.getString(CharacterTable.CHARACTER_NAME);
            if (existingName != null && existingName.equalsIgnoreCase(name)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public Optional<CharacterData> getCharacterById(int characterId) {
        final ResultSet selectResult = getSession().execute(
                selectFrom(getKeyspace(), CharacterTable.getTableName()).all()
                        .whereColumn(CharacterTable.CHARACTER_ID).isEqualTo(literal(characterId))
                        .build()
        );
        for (Row row : selectResult) {
            return Optional.of(loadCharacterData(row));
        }
        return Optional.empty();
    }

    @Override
    public Optional<CharacterData> getCharacterByName(String name) {
        final ResultSet selectResult = getSession().execute(
                selectFrom(getKeyspace(), CharacterTable.getTableName()).all()
                        .whereColumn(CharacterTable.CHARACTER_NAME_INDEX).isEqualTo(literal(lowerName(name)))
                        .build()
        );
        for (Row row : selectResult) {
            return Optional.of(loadCharacterData(row));
        }
        return Optional.empty();
    }

    @Override
    public Optional<Integer> getAccountIdByName(String name) {
        final ResultSet selectResult = getSession().execute(
                selectFrom(getKeyspace(), CharacterTable.getTableName())
                        .columns(
                                CharacterTable.ACCOUNT_ID
                        )
                        .whereColumn(CharacterTable.CHARACTER_NAME_INDEX).isEqualTo(literal(lowerName(name)))
                        .build()
        );
        for (Row row : selectResult) {
            return Optional.of(row.getInt(CharacterTable.ACCOUNT_ID));
        }
        return Optional.empty();
    }

    @Override
    public List<AvatarData> getAvatarDataByAccount(int accountId) {
        final List<AvatarData> avatarDataList = new ArrayList<>();
        final ResultSet selectResult = getSession().execute(
                selectFrom(getKeyspace(), CharacterTable.getTableName())
                        .columns(
                                CharacterTable.CHARACTER_ID,
                                CharacterTable.CHARACTER_NAME,
                                CharacterTable.CHARACTER_STAT,
                                CharacterTable.CHARACTER_EQUIPPED
                        )
                        .whereColumn(CharacterTable.ACCOUNT_ID).isEqualTo(literal(accountId))
                        .build()
        );
        for (Row row : selectResult) {
            final CharacterStat characterStat = row.get(CharacterTable.CHARACTER_STAT, CharacterStat.class);
            characterStat.setId(row.getInt(CharacterTable.CHARACTER_ID));
            characterStat.setName(row.getString(CharacterTable.CHARACTER_NAME));
            final Inventory equipped = row.get(CharacterTable.CHARACTER_EQUIPPED, Inventory.class);
            avatarDataList.add(AvatarData.from(characterStat, equipped));
        }
        return avatarDataList;
    }

    @Override
    public synchronized boolean newCharacter(CharacterData characterData) {
        if (!checkCharacterNameAvailable(characterData.getCharacterName())) {
            return false;
        }
        return saveCharacter(characterData);
    }

    @Override
    public boolean saveCharacter(CharacterData characterData) {
        final CodecRegistry registry = getSession().getContext().getCodecRegistry();
        final ResultSet updateResult = getSession().execute(
                update(getKeyspace(), CharacterTable.getTableName())
                        .setColumn(CharacterTable.ACCOUNT_ID, literal(characterData.getAccountId()))
                        .setColumn(CharacterTable.CHARACTER_NAME, literal(characterData.getCharacterName()))
                        .setColumn(CharacterTable.CHARACTER_NAME_INDEX, literal(lowerName(characterData.getCharacterName())))
                        .setColumn(CharacterTable.CHARACTER_STAT, literal(characterData.getCharacterStat(), registry))
                        .setColumn(CharacterTable.CHARACTER_EQUIPPED, literal(characterData.getInventoryManager().getEquipped(), registry))
                        .setColumn(CharacterTable.EQUIP_INVENTORY, literal(characterData.getInventoryManager().getEquipInventory(), registry))
                        .setColumn(CharacterTable.CONSUME_INVENTORY, literal(characterData.getInventoryManager().getConsumeInventory(), registry))
                        .setColumn(CharacterTable.INSTALL_INVENTORY, literal(characterData.getInventoryManager().getInstallInventory(), registry))
                        .setColumn(CharacterTable.ETC_INVENTORY, literal(characterData.getInventoryManager().getEtcInventory(), registry))
                        .setColumn(CharacterTable.CASH_INVENTORY, literal(characterData.getInventoryManager().getCashInventory(), registry))
                        .setColumn(CharacterTable.MONEY, literal(characterData.getInventoryManager().getMoney()))
                        .setColumn(CharacterTable.SKILL_RECORDS, literal(characterData.getSkillManager().getSkillRecords(), registry))
                        .setColumn(CharacterTable.QUEST_RECORDS, literal(characterData.getQuestManager().getQuestRecords(), registry))
                        .setColumn(CharacterTable.FUNC_KEY_MAN, literal(characterData.getFuncKeyManager(), registry))
                        .setColumn(CharacterTable.ITEM_SN_COUNTER, literal(characterData.getItemSnCounter().get()))
                        .setColumn(CharacterTable.FRIEND_MAX, literal(characterData.getFriendMax()))
                        .whereColumn(CharacterTable.CHARACTER_ID).isEqualTo(literal(characterData.getCharacterId()))
                        .build()
        );
        return updateResult.wasApplied();
    }

    @Override
    public boolean deleteCharacter(int accountId, int characterId) {
        final ResultSet updateResult = getSession().execute(
                deleteFrom(getKeyspace(), CharacterTable.getTableName())
                        .whereColumn(CharacterTable.CHARACTER_ID).isEqualTo(literal(characterId))
                        .ifColumn(CharacterTable.ACCOUNT_ID).isEqualTo(literal(accountId))
                        .build()
        );
        return updateResult.wasApplied();
    }
}
