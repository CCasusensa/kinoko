# Alcadno Research Institute : Lab - Area C-1 (261020300)

LAB_AREA_C_1 = 261020300
CRITICAL_ERROR = 261020301

if fieldId == LAB_AREA_C_1:
    if sm.hasParty() and not sm.isPartyBoss():
        sm.message("You are not the leader of the party.")
        sm.dispose()
    if not sm.partyWarpInstance(CRITICAL_ERROR, "out00", LAB_AREA_C_1, 7200):
        sm.message("All of the Mini-Dungeons are in use right now, please try again later.")
        sm.dispose()
else:
    sm.warp(LAB_AREA_C_1, "MD00")