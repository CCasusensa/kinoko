# Mel Nomie : Skin Care Expert (2150004)
#   Black Wing Territory : Edelstein (310000000)

CHOICES = [ 0, 1, 2, 3, 4, 5, 9, 10, 11 ]
SKIN_COUPON = 5153015

answer = sm.askAvatar("We have the latest in beauty equipment. With our technology, you can preview what your skin will look like in advance! Which treatment would you like?", CHOICES)
if answer >= 0 and answer < len(CHOICES):
    if sm.removeItem(SKIN_COUPON, 1):
        sm.changeAvatar(CHOICES[answer])
        sm.sayNext("Here's the mirror, check it out! Doesn't your skin look beautiful and glowing like mine? Hehe, it's wonderful. Please come again!")
    else:
        sm.sayNext("Hmmm...it looks like you don't have our designated coupon...I'm afraid I can't give you a treatment without it. I'm sorry...")
