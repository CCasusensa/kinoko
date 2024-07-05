# NLC Ticket Gate (9201068)
#   New Leaf City : NLC Subway Station (600010001)

WAITING_ROOM_FROM_NLC_TO_KC = 600010002
SUBWAY_TICKET_TO_KERNING_CITY = 4031713


if sm.hasItem(SUBWAY_TICKET_TO_KERNING_CITY):
    eventState = sm.getEventState("CM_SUBWAY")
    if eventState == "SUBWAY_BOARDING":
        if sm.askYesNo("It looks like there's plenty of room for this ride. Please have your ticket ready so I can let you in. The ride will be long, but you'll get to your destination just fine. What do you think? Do you want to get on this ride?"):
            if sm.removeItem(SUBWAY_TICKET_TO_KERNING_CITY, 1):
                sm.warp(WAITING_ROOM_FROM_NLC_TO_KC, "st00")
        else:
            sm.sayNext("You must have some business to take care of here, right?")
    elif eventState == "SUBWAY_WAITING":
        sm.sayNext("This subway is getting ready for takeoff. I'm sorry, but you'll have to get on the next ride. The ride schedule is available through the usher at the ticketing booth.")
    else:
        sm.sayNext("We will begin boarding 5 minutes before the takeoff. Please be patient and wait for a few minutes. Be aware that the subway will take off right on time, and we stop receiving tickets 1 minute before that, so please make sure to be here on time.")
else:
    sm.sayOk("Here's the ticket reader. You are not allowed in without the ticket.")
