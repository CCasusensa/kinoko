# Intern Shakihands : Visage Nurse (9201019)
#   Amoria : Amoria Plastic Surgery  (680000003)

VIP_FACE_M = [
    20018, # Champion Focus
    20019, # Irritable Face
    20000, # Motivated Look
    20001, # Perplexed Stare
    20003, # Dramatic Face
    20004, # Rebel's Fire
    20005, # Alert Face
    20006, # Babyface Pout
    20008, # Worrisome Glare
]
VIP_FACE_F = [
    21018, # Athena's Grace
    21019, # Hera's Radiance
    21001, # Fearful Stare
    21002, # Leisure Look
    21003, # Strong Stare
    21004, # Angel Glow
    21005, # Babyface Pout
    21006, # Pucker Up Face
    21007, # Dollface Look
    21012, # Soul's Window
]

FACE_COUPON_REG = 5152056

if sm.askYesNo("If you use the regular coupon, you may end up with a random new look for your face...do you still want to do it using #b#t5152056##k?"):
    if sm.removeItem(FACE_COUPON_REG, 1):
        choices = VIP_FACE_M if sm.getGender() == 0 else VIP_FACE_F
        face = choices[sm.getRandom(0, len(choices) - 1)] + (sm.getFace() % 1000) - (sm.getFace() % 100)
        sm.changeAvatar(face)
        sm.sayNext("The surgery's complete. Don't you like it? I think it came out great!")
    else:
        sm.sayNext("Hmmm...it looks like you don't have our designated coupon...I'm afraid I can't perform plastic surgery for you without it. I'm sorry...")
