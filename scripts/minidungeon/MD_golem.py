# Golem's Temple : Golem's Temple 4 (100040400)
#   MD00 (978, -359)
# Golem's Temple : Golem's Castle Ruins (100040500)
#   out00 (-232, 1142)
# Dungeon : Sleepy Dungeon IV (105040304)
#   MD00 (717, 674)

GOLEMS_TEMPLE_4 = 100040400
GOLEMS_CASTLE_RUINS = 100040500

SLEEPY_DUNGEON_IV = 105040304 # dungeon map doesn't exist

if sm.getFieldId() == SLEEPY_DUNGEON_IV:
    sm.message("You cannot go to that place")
    sm.dispose()
elif sm.getFieldId() == GOLEMS_TEMPLE_4:
    if sm.hasParty() and not sm.isPartyBoss():
        sm.message("You are not the leader of the party.")
        sm.dispose()
    else:
        sm.playPortalSE()
        sm.partyWarpInstance(GOLEMS_CASTLE_RUINS, "out00", GOLEMS_TEMPLE_4, 7200)
else:
    sm.playPortalSE()
    sm.warp(GOLEMS_TEMPLE_4, "MD00")
