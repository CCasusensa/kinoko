# Kiru : Station Guide (1100004)

# move_EreOrb
TO_ORBIS = [
    200090021, 200090023, 200090025, 200090027, 200090029,
    200090041, 200090043, 200090045, 200090047, 200090049
]

sm.sayNext("Hmm... The winds are favorable. Are you thinking of leaving #eEreve#n and going somewhere else? This ferry sails to Orbis on the Ossyria Continent.")

if sm.askYesNo("Have you taken care of everything you needed to in #eEreve#n? If you happen to be headed towards #b#eOrbis#n#k I can take you there. What do you say? Are you going to go to #eOrbis#n?\r\n\r\nYou'll have to pay a fee of #b1000#k Mesos."):
    if sm.canAddMoney(-1000):
        if sm.warpInstance(TO_ORBIS, "sp"):
            sm.addMoney(-1000)
            sm.clock(120)
        else:
            sm.sayNext("Someone else is heading to Orbis at the moment. Please try again later.")
    else:
        sm.sayNext("Hmm... Are you sure you have #b1000#k Mesos? Check your Inventory and make sure you have enough. You must pay the fee or I can't let you get on...")
else:
    sm.sayNext("If you're not interested, then oh well...")