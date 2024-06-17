package kinoko.server.command;

import kinoko.packet.user.DragonPacket;
import kinoko.packet.user.UserLocal;
import kinoko.packet.world.MessagePacket;
import kinoko.packet.world.WvsContext;
import kinoko.provider.*;
import kinoko.provider.item.ItemInfo;
import kinoko.provider.map.Foothold;
import kinoko.provider.map.MapInfo;
import kinoko.provider.map.PortalInfo;
import kinoko.provider.mob.MobSkillType;
import kinoko.provider.mob.MobTemplate;
import kinoko.provider.npc.NpcTemplate;
import kinoko.provider.quest.QuestInfo;
import kinoko.provider.skill.SkillInfo;
import kinoko.provider.skill.SkillStat;
import kinoko.provider.skill.SkillStringInfo;
import kinoko.server.ServerConfig;
import kinoko.server.script.ScriptDispatcher;
import kinoko.util.Rect;
import kinoko.util.Util;
import kinoko.world.GameConstants;
import kinoko.world.cashshop.CashShop;
import kinoko.world.cashshop.Commodity;
import kinoko.world.field.Field;
import kinoko.world.field.mob.Mob;
import kinoko.world.field.mob.MobAppearType;
import kinoko.world.item.InventoryManager;
import kinoko.world.item.InventoryOperation;
import kinoko.world.item.Item;
import kinoko.world.job.Job;
import kinoko.world.job.JobConstants;
import kinoko.world.job.legend.Aran;
import kinoko.world.quest.QuestRecord;
import kinoko.world.skill.SkillManager;
import kinoko.world.skill.SkillRecord;
import kinoko.world.user.Account;
import kinoko.world.user.CalcDamage;
import kinoko.world.user.Dragon;
import kinoko.world.user.User;
import kinoko.world.user.effect.Effect;
import kinoko.world.user.stat.CharacterStat;
import kinoko.world.user.stat.CharacterTemporaryStat;
import kinoko.world.user.stat.Stat;
import kinoko.world.user.stat.TemporaryStatOption;

import java.lang.reflect.Method;
import java.util.*;

public final class AdminCommands {
    @Command("test")
    public static void test(User user, String[] args) {
        user.dispose();
    }

    @Command("dispose")
    public static void dispose(User user, String[] args) {
        ScriptDispatcher.removeScriptManager(user);
        user.closeDialog();
        user.dispose();
        user.write(MessagePacket.system("You have been disposed."));
    }

    @Command("info")
    public static void info(User user, String[] args) {
        // User stats
        final Field field = user.getField();
        user.write(MessagePacket.system("HP : %d / %d, MP : %d / %d", user.getHp(), user.getMaxHp(), user.getMp(), user.getMaxMp()));
        user.write(MessagePacket.system("Damage : %d ~ %d", (int) CalcDamage.calcDamageMin(user), (int) CalcDamage.calcDamageMax(user)));
        user.write(MessagePacket.system("Field ID : %d", field.getFieldId()));
        // Compute foothold below
        final Optional<Foothold> footholdBelowResult = field.getFootholdBelow(user.getX(), user.getY());
        final String footholdBelow = footholdBelowResult.map(foothold -> String.valueOf(foothold.getFootholdId())).orElse("unk");
        user.write(MessagePacket.system("  x : %d, y : %d, fh : %d (%s)", user.getX(), user.getY(), user.getFoothold(), footholdBelow));
        // Compute nearest portal
        double nearestDistance = Double.MAX_VALUE;
        PortalInfo nearestPortal = null;
        for (PortalInfo pi : field.getMapInfo().getPortalInfos()) {
            final double distance = Util.distance(user.getX(), user.getY(), pi.getX(), pi.getY());
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearestPortal = pi;
            }
        }
        if (nearestPortal != null && nearestDistance < 100) {
            user.write(MessagePacket.system("Portal name : %s (%d)", nearestPortal.getPortalName(), nearestPortal.getPortalId()));
            user.write(MessagePacket.system("  x : %d, y : %d, script : %s",
                    nearestPortal.getX(), nearestPortal.getY(), nearestPortal.getScript()));
        }
        // Compute nearest mob
        final Optional<Mob> nearestMobResult = user.getNearestObject(field.getMobPool().getInsideRect(user.getRelativeRect(new Rect(-100, -100, 100, 100))));
        if (nearestMobResult.isPresent()) {
            final Mob nearestMob = nearestMobResult.get();
            user.write(MessagePacket.system("%s", nearestMob.toString()));
            user.write(MessagePacket.system("  Controller : %s", nearestMob.getController().getCharacterName()));
        }
    }

    @Command({ "find", "lookup" })
    @Arguments({ "item/map/mob/npc/skill/commodity", "id or query" })
    public static void find(User user, String[] args) {
        final String type = args[1];
        final String query = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        final boolean isNumber = Util.isInteger(query);
        if (type.equalsIgnoreCase("item")) {
            int itemId = -1;
            if (isNumber) {
                itemId = Integer.parseInt(query);
            } else {
                final List<Map.Entry<Integer, String>> searchResult = StringProvider.getItemNames().entrySet().stream()
                        .filter((entry) -> entry.getValue().toLowerCase().contains(query.toLowerCase()))
                        .sorted(Comparator.comparingInt(Map.Entry::getKey))
                        .toList();
                if (!searchResult.isEmpty()) {
                    if (searchResult.size() == 1) {
                        itemId = searchResult.get(0).getKey();
                    } else {
                        user.write(MessagePacket.system("Results for item name : \"%s\"", query));
                        for (var entry : searchResult) {
                            user.write(MessagePacket.system("  %d : %s", entry.getKey(), entry.getValue()));
                        }
                        return;
                    }
                }
            }
            final Optional<ItemInfo> itemInfoResult = ItemProvider.getItemInfo(itemId);
            if (itemInfoResult.isEmpty()) {
                user.write(MessagePacket.system("Could not find item with %s : %s", isNumber ? "ID" : "name", query));
                return;
            }
            final ItemInfo ii = itemInfoResult.get();
            user.write(MessagePacket.system("Item : %s (%d)", StringProvider.getItemName(itemId), itemId));
            if (!ii.getItemInfos().isEmpty()) {
                user.write(MessagePacket.system("  info"));
                for (var entry : ii.getItemInfos().entrySet()) {
                    user.write(MessagePacket.system("    %s : %s", entry.getKey().name(), entry.getValue().toString()));
                }
            }
            if (!ii.getItemSpecs().isEmpty()) {
                user.write(MessagePacket.system("  spec"));
                for (var entry : ii.getItemSpecs().entrySet()) {
                    user.write(MessagePacket.system("    %s : %s", entry.getKey().name(), entry.getValue().toString()));
                }
            }
        } else if (type.equalsIgnoreCase("map")) {
            int mapId = -1;
            if (isNumber) {
                mapId = Integer.parseInt(query);
            } else {
                final List<Map.Entry<Integer, String>> searchResult = StringProvider.getMapNames().entrySet().stream()
                        .filter((entry) -> entry.getValue().toLowerCase().contains(query.toLowerCase()))
                        .sorted(Comparator.comparingInt(Map.Entry::getKey))
                        .toList();
                if (!searchResult.isEmpty()) {
                    if (searchResult.size() == 1) {
                        mapId = searchResult.get(0).getKey();
                    } else {
                        user.write(MessagePacket.system("Results for map name : \"%s\"", query));
                        for (var entry : searchResult) {
                            user.write(MessagePacket.system("  %d : %s", entry.getKey(), entry.getValue()));
                        }
                        return;
                    }
                }
            }
            final Optional<MapInfo> mapInfoResult = MapProvider.getMapInfo(mapId);
            if (mapInfoResult.isEmpty()) {
                user.write(MessagePacket.system("Could not find map with %s : %s", isNumber ? "ID" : "name", query));
                return;
            }
            final MapInfo mapInfo = mapInfoResult.get();
            user.write(MessagePacket.system("Map : %s (%d)", StringProvider.getMapName(mapId), mapId));
            user.write(MessagePacket.system("  type : %s", mapInfo.getFieldType().name()));
            user.write(MessagePacket.system("  returnMap : %d", mapInfo.getReturnMap()));
            user.write(MessagePacket.system("  onFirstUserEnter : %s", mapInfo.getOnFirstUserEnter()));
            user.write(MessagePacket.system("  onUserEnter : %s", mapInfo.getOnUserEnter()));
        } else if (type.equalsIgnoreCase("mob")) {
            int mobId = -1;
            if (isNumber) {
                mobId = Integer.parseInt(query);
            } else {
                final List<Map.Entry<Integer, String>> searchResult = StringProvider.getMobNames().entrySet().stream()
                        .filter((entry) -> entry.getValue().toLowerCase().contains(query.toLowerCase()))
                        .sorted(Comparator.comparingInt(Map.Entry::getKey))
                        .toList();
                if (!searchResult.isEmpty()) {
                    if (searchResult.size() == 1) {
                        mobId = searchResult.get(0).getKey();
                    } else {
                        user.write(MessagePacket.system("Results for mob name : \"%s\"", query));
                        for (var entry : searchResult) {
                            user.write(MessagePacket.system("  %d : %s", entry.getKey(), entry.getValue()));
                        }
                        return;
                    }
                }
            }
            final Optional<MobTemplate> mobTemplateResult = MobProvider.getMobTemplate(mobId);
            if (mobTemplateResult.isEmpty()) {
                user.write(MessagePacket.system("Could not find mob with %s : %s", isNumber ? "ID" : "name", query));
                return;
            }
            final MobTemplate mobTemplate = mobTemplateResult.get();
            user.write(MessagePacket.system("Mob : %s (%d)", StringProvider.getMobName(mobId), mobId));
            user.write(MessagePacket.system("  level : %d", mobTemplate.getLevel()));
        } else if (type.equalsIgnoreCase("npc")) {
            int npcId = -1;
            if (isNumber) {
                npcId = Integer.parseInt(query);
            } else {
                final List<Map.Entry<Integer, String>> searchResult = StringProvider.getNpcNames().entrySet().stream()
                        .filter((entry) -> entry.getValue().toLowerCase().contains(query.toLowerCase()))
                        .sorted(Comparator.comparingInt(Map.Entry::getKey))
                        .toList();
                if (!searchResult.isEmpty()) {
                    if (searchResult.size() == 1) {
                        npcId = searchResult.get(0).getKey();
                    } else {
                        user.write(MessagePacket.system("Results for npc name : \"%s\"", query));
                        for (var entry : searchResult) {
                            user.write(MessagePacket.system("  %d : %s", entry.getKey(), entry.getValue()));
                        }
                        return;
                    }
                }
            }
            final Optional<NpcTemplate> npcTemplateResult = NpcProvider.getNpcTemplate(npcId);
            if (npcTemplateResult.isEmpty()) {
                user.write(MessagePacket.system("Could not find npc with %s : %s", isNumber ? "ID" : "name", query));
                return;
            }
            final NpcTemplate npcTemplate = npcTemplateResult.get();
            user.write(MessagePacket.system("Npc : %s (%d)", StringProvider.getNpcName(npcId), npcId));
            user.write(MessagePacket.system("  script : %s", npcTemplate.getScript()));
        } else if (type.equalsIgnoreCase("skill")) {
            int skillId = -1;
            if (isNumber) {
                skillId = Integer.parseInt(query);
            } else {
                final List<Map.Entry<Integer, SkillStringInfo>> searchResult = StringProvider.getSkillStrings().entrySet().stream()
                        .filter((entry) -> entry.getValue().getName().toLowerCase().contains(query.toLowerCase()))
                        .sorted(Comparator.comparingInt(Map.Entry::getKey))
                        .toList();
                if (!searchResult.isEmpty()) {
                    if (searchResult.size() == 1) {
                        skillId = searchResult.get(0).getKey();
                    } else {
                        user.write(MessagePacket.system("Results for skill name : \"%s\"", query));
                        for (var entry : searchResult) {
                            user.write(MessagePacket.system("  %d : %s", entry.getKey(), entry.getValue().getName()));
                        }
                        return;
                    }
                }
            }
            final Optional<SkillInfo> skillInfoResult = SkillProvider.getSkillInfoById(skillId);
            if (skillInfoResult.isEmpty()) {
                user.write(MessagePacket.system("Could not find skill with %s : %s", isNumber ? "ID" : "name", query));
                return;
            }
            final SkillInfo si = skillInfoResult.get();
            user.write(MessagePacket.system("Skill : %s (%d)", StringProvider.getSkillName(skillId), skillId));
        } else if (type.equalsIgnoreCase("commodity")) {
            if (!isNumber) {
                user.write(MessagePacket.system("Can only lookup commodity by ID"));
                return;
            }
            final int commodityId = Integer.parseInt(query);
            final Optional<Commodity> commodityResult = CashShop.getCommodity(commodityId);
            if (commodityResult.isEmpty()) {
                user.write(MessagePacket.system("Could not find commodity with ID : %d", commodityId));
                return;
            }
            final Commodity commodity = commodityResult.get();
            user.write(MessagePacket.system("Commodity : %d", commodityId));
            user.write(MessagePacket.system("  itemId : %d (%s)", commodity.getItemId(), StringProvider.getItemName(commodity.getItemId())));
            user.write(MessagePacket.system("  count : %d", commodity.getCount()));
            user.write(MessagePacket.system("  price : %d", commodity.getPrice()));
            user.write(MessagePacket.system("  period : %d", commodity.getPeriod()));
            user.write(MessagePacket.system("  gender : %d", commodity.getGender()));
        }
    }

    @Command("npc")
    @Arguments("npc template ID")
    public static void npc(User user, String[] args) {
        final int templateId = Integer.parseInt(args[1]);
        final Optional<NpcTemplate> npcTemplateResult = NpcProvider.getNpcTemplate(templateId);
        if (npcTemplateResult.isEmpty()) {
            user.write(MessagePacket.system("Could not resolve npc ID : %d", templateId));
            return;
        }
        final String scriptName = npcTemplateResult.get().getScript();
        if (scriptName == null || scriptName.isEmpty()) {
            user.write(MessagePacket.system("Could not find script for npc ID : %d", templateId));
            return;
        }
        user.write(MessagePacket.system("Starting script for npc ID : %d, script : %s", templateId, scriptName));
        ScriptDispatcher.startNpcScript(user, templateId, scriptName);
    }

    @Command("map")
    @Arguments("field ID to warp to")
    public static void map(User user, String[] args) {
        final int fieldId = Integer.parseInt(args[1]);
        final Optional<Field> fieldResult = user.getConnectedServer().getFieldById(fieldId);
        if (fieldResult.isEmpty()) {
            user.write(MessagePacket.system("Could not resolve field ID : %d", fieldId));
            return;
        }
        final Field targetField = fieldResult.get();
        final Optional<PortalInfo> portalResult = targetField.getPortalById(0);
        if (portalResult.isEmpty()) {
            user.write(MessagePacket.system("Could not resolve portal for field ID : %d", fieldId));
            return;
        }
        try (var locked = user.acquire()) {
            user.warp(targetField, portalResult.get(), false, false);
        }
    }

    @Command({ "mob", "spawn" })
    @Arguments("mob template ID to spawn")
    public static void mob(User user, String[] args) {
        final int mobId = Integer.parseInt(args[1]);
        final Optional<MobTemplate> mobTemplateResult = MobProvider.getMobTemplate(mobId);
        if (mobTemplateResult.isEmpty()) {
            user.write(MessagePacket.system("Could not resolve mob ID : %d", mobId));
            return;
        }
        final Field field = user.getField();
        final Optional<Foothold> footholdResult = field.getFootholdBelow(user.getX(), user.getY());
        final Mob mob = new Mob(
                mobTemplateResult.get(),
                null,
                user.getX(),
                user.getY(),
                footholdResult.map(Foothold::getFootholdId).orElse(user.getFoothold())
        );
        field.getMobPool().addMob(mob);
        mob.setAppearType(MobAppearType.NORMAL);
    }

    @Command("item")
    @Arguments({ "item ID", "item quantity" })
    public static void item(User user, String[] args) {
        final int itemId = Integer.parseInt(args[1]);
        final int quantity = Integer.parseInt(args[2]);
        final Optional<ItemInfo> itemInfoResult = ItemProvider.getItemInfo(itemId);
        if (itemInfoResult.isEmpty()) {
            user.write(MessagePacket.system("Could not resolve item ID : %d", itemId));
            return;
        }
        final ItemInfo ii = itemInfoResult.get();
        final Item item = ii.createItem(user.getNextItemSn(), Math.min(quantity, ii.getSlotMax()));

        // Add item
        try (var locked = user.acquire()) {
            final InventoryManager im = locked.get().getInventoryManager();
            final Optional<List<InventoryOperation>> addItemResult = im.addItem(item);
            if (addItemResult.isPresent()) {
                user.write(WvsContext.inventoryOperation(addItemResult.get(), true));
                user.write(UserLocal.effect(Effect.gainItem(item)));
            } else {
                user.write(MessagePacket.system("Failed to add item ID %d (%d) to inventory", itemId, quantity));
            }
        }
    }

    @Command({ "meso", "money" })
    @Arguments("amount")
    public static void meso(User user, String[] args) {
        final int money = Integer.parseInt(args[1]);
        try (var locked = user.acquire()) {
            final InventoryManager im = locked.get().getInventoryManager();
            im.setMoney(money);
            user.write(WvsContext.statChanged(Stat.MONEY, im.getMoney(), true));
        }
    }

    @Command("nx")
    @Arguments("amount")
    public static void nx(User user, String[] args) {
        final int nx = Integer.parseInt(args[1]);
        try (var lockedAccount = user.getAccount().acquire()) {
            final Account account = lockedAccount.get();
            account.setNxPrepaid(nx);
            user.write(MessagePacket.system("Set NX prepaid to %d", nx));
        }
    }

    @Command("hp")
    @Arguments("new hp")
    public static void hp(User user, String[] args) {
        final int newHp = Integer.parseInt(args[1]);
        try (var locked = user.acquire()) {
            user.setHp(newHp);
        }
    }

    @Command("mp")
    @Arguments("new mp")
    public static void mp(User user, String[] args) {
        final int newMp = Integer.parseInt(args[1]);
        try (var locked = user.acquire()) {
            user.setMp(newMp);
        }
    }

    @Command("stat")
    @Arguments({ "hp/mp/str/dex/int/luk/ap/sp", "new value" })
    public static void stat(User user, String[] args) {
        final String stat = args[1].toLowerCase();
        final int value = Integer.parseInt(args[2]);
        try (var locked = user.acquire()) {
            final CharacterStat cs = locked.get().getCharacterStat();
            final Map<Stat, Object> statMap = new EnumMap<>(Stat.class);
            switch (stat) {
                case "hp" -> {
                    cs.setMaxHp(value);
                    statMap.put(Stat.HP, cs.getMaxHp());
                }
                case "mp" -> {
                    cs.setMaxMp(value);
                    statMap.put(Stat.MP, cs.getMaxMp());
                }
                case "str" -> {
                    cs.setBaseStr((short) value);
                    statMap.put(Stat.STR, cs.getBaseStr());
                }
                case "dex" -> {
                    cs.setBaseDex((short) value);
                    statMap.put(Stat.DEX, cs.getBaseDex());
                }
                case "int" -> {
                    cs.setBaseInt((short) value);
                    statMap.put(Stat.INT, cs.getBaseInt());
                }
                case "luk" -> {
                    cs.setBaseLuk((short) value);
                    statMap.put(Stat.LUK, cs.getBaseLuk());
                }
                case "ap" -> {
                    cs.setAp((short) value);
                    statMap.put(Stat.AP, cs.getAp());
                }
                case "sp" -> {
                    if (JobConstants.isExtendSpJob(cs.getJob())) {
                        cs.getSp().setSp(JobConstants.getJobLevel(cs.getJob()), value);
                        statMap.put(Stat.SP, cs.getSp());
                    } else {
                        cs.getSp().setNonExtendSp(value);
                        statMap.put(Stat.SP, cs.getSp().getNonExtendSp());
                    }
                }
                default -> {
                    user.write(MessagePacket.system("Syntax : %sstat hp/mp/str/dex/int/luk/ap/sp <new value>", ServerConfig.COMMAND_PREFIX));
                    return;
                }
            }
            user.validateStat();
            user.write(WvsContext.statChanged(statMap, true));
            user.write(MessagePacket.system("Set %s to %d", stat, value));
        }
    }

    @Command("level")
    @Arguments("new level")
    public static void level(User user, String[] args) {
        final int level = Integer.parseInt(args[1]);
        if (level < 1 || level > GameConstants.LEVEL_MAX) {
            user.write(MessagePacket.system("Could not change level to : {}", level));
            return;
        }
        try (var locked = user.acquire()) {
            final CharacterStat cs = user.getCharacterStat();
            cs.setLevel((short) level);
            user.validateStat();
            user.write(WvsContext.statChanged(Stat.LEVEL, (byte) cs.getLevel(), true));
        }
    }

    @Command("job")
    @Arguments("job ID")
    public static void job(User user, String[] args) {
        final int jobId = Integer.parseInt(args[1]);
        if (Job.getById(jobId) == null) {
            user.write(MessagePacket.system("Could not change to unknown job : {}", jobId));
            return;
        }
        try (var locked = user.acquire()) {
            if (JobConstants.isDragonJob(jobId)) {
                final Dragon dragon = new Dragon(user);
                user.setDragon(dragon);
                user.getField().broadcastPacket(DragonPacket.dragonEnterField(user, dragon));
            } else {
                user.setDragon(null);
            }
            user.setJob(jobId);
        }
    }

    @Command("skill")
    @Arguments({ "skill ID", "skill level" })
    public static void skill(User user, String[] args) {
        final int skillId = Integer.parseInt(args[1]);
        final int slv = Integer.parseInt(args[2]);
        final Optional<SkillInfo> skillInfoResult = SkillProvider.getSkillInfoById(skillId);
        if (skillInfoResult.isEmpty()) {
            user.write(MessagePacket.system("Could not find skill : {}", skillId));
            return;
        }
        final SkillInfo si = skillInfoResult.get();
        final SkillRecord skillRecord = si.createRecord();
        skillRecord.setSkillLevel(Math.min(slv, si.getMaxLevel()));
        skillRecord.setMasterLevel(si.getMaxLevel());
        try (var locked = user.acquire()) {
            final SkillManager sm = user.getSkillManager();
            sm.addSkill(skillRecord);
            user.updatePassiveSkillData();
            user.validateStat();
            user.write(WvsContext.changeSkillRecordResult(skillRecord, true));
        }
    }

    @Command("startquest")
    @Arguments("quest ID")
    public static void startQuest(User user, String[] args) {
        final int questId = Integer.parseInt(args[1]);
        final Optional<QuestInfo> questInfoResult = QuestProvider.getQuestInfo(questId);
        if (questInfoResult.isEmpty()) {
            user.write(MessagePacket.system("Could not find quest : %d", questId));
            return;
        }
        try (var locked = user.acquire()) {
            final QuestRecord qr = user.getQuestManager().forceStartQuest(questId);
            user.write(MessagePacket.questRecord(qr));
            user.validateStat();
        }
    }

    @Command("questex")
    @Arguments({ "quest ID", "QR value" })
    public static void questex(User user, String[] args) {
        final int questId = Integer.parseInt(args[1]);
        final String infoValue = args[2];
        try (var locked = user.acquire()) {
            final QuestRecord qr = user.getQuestManager().setQuestInfoEx(questId, infoValue);
            user.write(MessagePacket.questRecord(qr));
            user.validateStat();
        }
    }

    @Command("killmobs")
    public static void killMobs(User user, String[] args) {
        user.getField().getMobPool().forEach((mob) -> {
            if (mob.getHp() > 0) {
                mob.damage(user, mob.getMaxHp());
            }
        });
    }

    @Command("mobskill")
    @Arguments({ "skill ID", "skill level" })
    public static void mobskill(User user, String[] args) {
        final int skillId = Integer.parseInt(args[1]);
        final int slv = Integer.parseInt(args[2]);
        final MobSkillType skillType = MobSkillType.getByValue(skillId);
        if (skillType == null) {
            user.write(MessagePacket.system("Could not resolve mob skill %d", skillId));
            return;
        }
        final CharacterTemporaryStat cts = skillType.getCharacterTemporaryStat();
        if (cts == null) {
            user.write(MessagePacket.system("Could not resolve mob skill {} does not apply a CTS", skillType));
            return;
        }
        // Apply mob skill
        final Optional<SkillInfo> skillInfoResult = SkillProvider.getMobSkillInfoById(skillId);
        if (skillInfoResult.isEmpty()) {
            user.write(MessagePacket.system("Could not resolve mob skill info %d", skillId));
            return;
        }
        final SkillInfo si = skillInfoResult.get();
        try (var locked = user.acquire()) {
            locked.get().setTemporaryStat(cts, TemporaryStatOption.ofMobSkill(si.getValue(SkillStat.x, slv), skillId, slv, si.getDuration(slv)));
        }
    }

    @Command("combo")
    @Arguments("value")
    public static void combo(User user, String[] args) {
        final int combo = Integer.parseInt(args[1]);
        try (var locked = user.acquire()) {
            user.setTemporaryStat(CharacterTemporaryStat.ComboAbilityBuff, TemporaryStatOption.of(combo, Aran.COMBO_ABILITY, 0));
            user.write(UserLocal.incCombo(combo));
        }
    }

    @Command("cd")
    public static void cd(User user, String[] args) {
        try (var locked = user.acquire()) {
            final var iter = locked.get().getSkillManager().getSkillCooltimes().keySet().iterator();
            while (iter.hasNext()) {
                final int skillId = iter.next();
                user.write(UserLocal.skillCooltimeSet(skillId, 0));
                iter.remove();
            }
        }
    }

    @Command("max")
    public static void max(User user, String[] args) {
        try (var locked = user.acquire()) {
            // Set stats
            final CharacterStat cs = user.getCharacterStat();
            cs.setLevel((short) 200);
//            cs.setBaseStr((short) 10000);
//            cs.setBaseDex((short) 10000);
//            cs.setBaseInt((short) 10000);
//            cs.setBaseLuk((short) 10000);
            cs.setMaxHp(50000);
            cs.setMaxMp(50000);
            cs.setExp(0);
            user.validateStat();
            user.write(WvsContext.statChanged(Map.of(
                    Stat.LEVEL, (byte) cs.getLevel(),
                    Stat.STR, cs.getBaseStr(),
                    Stat.DEX, cs.getBaseDex(),
                    Stat.INT, cs.getBaseInt(),
                    Stat.LUK, cs.getBaseLuk(),
                    Stat.MHP, cs.getMaxHp(),
                    Stat.MMP, cs.getMaxMp(),
                    Stat.EXP, cs.getExp()
            ), true));

            // Reset skills
            final SkillManager sm = user.getSkillManager();
            final Set<SkillRecord> removedRecords = new HashSet<>();
            for (SkillRecord skillRecord : sm.getSkillRecords()) {
                skillRecord.setSkillLevel(0);
                skillRecord.setMasterLevel(0);
                removedRecords.add(skillRecord);
            }
            user.write(WvsContext.changeSkillRecordResult(removedRecords, true));


            // Add skills
            final Set<SkillRecord> skillRecords = new HashSet<>();
            for (int skillRoot : JobConstants.getSkillRootFromJob(user.getJob())) {
                final Job job = Job.getById(skillRoot);
                for (SkillInfo si : SkillProvider.getSkillsForJob(job)) {
                    final SkillRecord skillRecord = si.createRecord();
                    skillRecord.setSkillLevel(si.getMaxLevel());
                    skillRecord.setMasterLevel(si.getMaxLevel());
                    sm.addSkill(skillRecord);
                    skillRecords.add(skillRecord);
                }
            }
            user.updatePassiveSkillData();
            user.validateStat();
            user.write(WvsContext.changeSkillRecordResult(skillRecords, true));

            // Heal
            user.setHp(user.getMaxHp());
            user.setMp(user.getMaxMp());
        }
    }

    @Command("help")
    public static void help(User user, String[] args) {
        if (args.length == 1) {
            for (Class<?> clazz : new Class[]{ AdminCommands.class }) {
                user.write(MessagePacket.system("Admin Commands :"));
                for (Method method : clazz.getDeclaredMethods()) {
                    if (!method.isAnnotationPresent(Command.class)) {
                        continue;
                    }
                    user.write(MessagePacket.system("%s", CommandProcessor.getHelpString(method)));
                }
            }
        } else {
            final String commandName = args[1].toLowerCase();
            final Optional<Method> commandResult = CommandProcessor.getCommand(commandName);
            if (commandResult.isEmpty()) {
                user.write(MessagePacket.system("Unknown command : %s", commandName));
                return;
            }
            final Method method = commandResult.get();
            user.write(MessagePacket.system("Syntax : %s", CommandProcessor.getHelpString(method)));
        }
    }
}
