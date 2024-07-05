# Beach : Wave Beach (120020100)
#   MD00 (1077, -56)
# Beach : The Pig Beach (120020200)
#   out00 (612, 212)

WAVE_BEACH = 120020100
THE_PIG_BEACH = 120020200

if sm.getFieldId() == WAVE_BEACH:
    if sm.hasParty() and not sm.isPartyBoss():
        sm.message("You are not the leader of the party.")
        sm.dispose()
    else:
        sm.playPortalSE()
        sm.partyWarpInstance(THE_PIG_BEACH, "out00", WAVE_BEACH, 7200)
else:
    sm.playPortalSE()
    sm.warp(WAVE_BEACH, "MD00")
