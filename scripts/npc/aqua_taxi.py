# Dophin (2060009)

AQUARIUM = 230000000
THE_SHARP_UNKNOWN = 230030200
PIER_ON_THE_BEACH = 251000100

DOLPHIN_TAXI_COUPON = 4031242

if sm.getFieldId() == AQUARIUM:
    answer = sm.askMenu("Ocean are all connected to each other. Place you can't reach by foot can easily reached oversea. How about taking #bDolphin Taxi#k with us today?\r\n" + \
            "#L0##bGo to the Sharp Unknown.#k#l\r\n" + \
            "#L1##bGo to Herb Town.#k#l" # Go to the Sea of Fog?
    )
    if answer == 0:
        if sm.askYesNo("There is a fee of 1000 mesos. Would you like to go there now?"):
            if sm.addMoney(-1000):
                sm.warp(THE_SHARP_UNKNOWN)
            else:
                sm.sayNext("I don't think you have enough money...")
        else:
            sm.sayOk("OK. If you ever change your mind, please let me know.")
    elif answer == 1:
        if sm.askYesNo("There is a fee of 10000 mesos. Would you like to go there now?"):
            if sm.addMoney(-10000):
                sm.warp(PIER_ON_THE_BEACH)
            else:
                sm.sayNext("I don't think you have enough money...")
        else:
            sm.sayOk("OK. If you ever change your mind, please let me know.")
elif sm.getFieldId() == PIER_ON_THE_BEACH:
    answer = sm.askMenu("Ocean are all connected to each other. Place you can't reach by foot can easily reached oversea. How about taking #bDolphin Taxi#k with us today?\r\n" + \
            "#L0##bGo to Aquarium.#k#l"
    )
    if answer == 0:
        if sm.askYesNo("There is a fee of 10000 mesos. Would you like to go there now?"):
            if sm.addMoney(-10000):
                sm.warp(AQUARIUM)
            else:
                sm.sayNext("I don't think you have enough money...")
        else:
            sm.sayOk("OK. If you ever change your mind, please let me know.")
