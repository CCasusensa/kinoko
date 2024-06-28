# Singing Mushroom Forest : Ghost Mushroom Forest (100020400)

GHOST_MUSHROOM_FOREST = 100020400
WARM_SHADE = 100020500
MINI_DUNGEON_COUNT = 100

if fieldId == GHOST_MUSHROOM_FOREST:
    if sm.hasParty() and not sm.isPartyBoss():
        sm.message("You are not the leader of the party.")
        sm.dispose()
    if not sm.warpInstance(WARM_SHADE, MINI_DUNGEON_COUNT, "out00"):
        sm.message("All of the Mini-Dungeons are in use right now, please try again later.")
        sm.dispose()
else:
    sm.warp(GHOST_MUSHROOM_FOREST, "MD00")