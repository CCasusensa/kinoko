# Herb Town : Red-Nose Pirate Den 2 (251010402)

RED_NOSE_PIRATE_DEN_2 = 251010402
PILLAGE_OF_TREASURE_ISLAND = 251010410
MINI_DUNGEON_COUNT = 20

if fieldId == RED_NOSE_PIRATE_DEN_2:
    if sm.hasParty() and not sm.isPartyBoss():
        sm.message("You are not the leader of the party.")
        sm.dispose()
    if not sm.warpInstance(PILLAGE_OF_TREASURE_ISLAND, MINI_DUNGEON_COUNT, "out00"):
        sm.message("All of the Mini-Dungeons are in use right now, please try again later.")
        sm.dispose()
else:
    sm.warp(RED_NOSE_PIRATE_DEN_2, "MD00")