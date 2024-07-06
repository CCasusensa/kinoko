# Noel : Plastic Surgery Assistant (9270023)
#   Singapore : CBD (540000000)

VIP_FACE_M = [
    20020, # Fierce Edge
    20013, # Insomniac Daze
    20021, # Overjoyed Smile
    20026, # Shuteye
    20005, # Alert Face
    20012, # Curious Dog
]
VIP_FACE_F = [
    21021, # Compassion Look
    21011, # Hypnotized Look
    21009, # Look of Death
    21025, # Shuteye
    21006, # Pucker Up Face
    21012, # Soul's Window
]

FACE_COUPON_REG = 5152056

if sm.askYesNo("If you use the regular coupon, then we will not be able to predict the new look of your face... Do you still want to proceed using #b#t5152056##k?"):
    if sm.removeItem(FACE_COUPON_REG, 1):
        choices = VIP_FACE_M if sm.getGender() == 0 else VIP_FACE_F
        face = choices[sm.getRandom(0, len(choices) - 1)] + (sm.getFace() % 1000) - (sm.getFace() % 100)
        sm.changeAvatar(face)
        sm.sayNext("Ok, the surgery's over. See for it yourself.. What do you think? Quite fantastic, if I should say so myself. Please come again when you want another look, okay?")
    else:
        sm.sayNext("Hmmm...it looks like you don't have our designated coupon...I'm afraid I can't perform plastic surgery for you without it. I'm sorry...")
