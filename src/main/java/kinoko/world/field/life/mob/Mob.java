package kinoko.world.field.life.mob;

import kinoko.packet.field.MobPacket;
import kinoko.provider.map.LifeInfo;
import kinoko.provider.mob.MobAttack;
import kinoko.provider.mob.MobInfo;
import kinoko.provider.mob.MobSkill;
import kinoko.server.packet.OutPacket;
import kinoko.util.Lockable;
import kinoko.world.field.ControlledObject;
import kinoko.world.field.life.Life;
import kinoko.world.user.User;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public final class Mob extends Life implements ControlledObject, Lockable<Mob> {
    private final Lock lock = new ReentrantLock();
    private final MobStatManager mobStatManager = new MobStatManager();
    private final Map<MobSkill, Instant> skillCooltimes = new ConcurrentHashMap<>();
    private final AtomicInteger attackCounter = new AtomicInteger(0);
    private final MobInfo mobInfo;
    private final MobAppearType appearType;

    private User controller;
    private int currentFh;
    private int hp;
    private int mp;

    public Mob(int x, int y, int fh, MobInfo mobInfo, MobAppearType appearType) {
        this.mobInfo = mobInfo;
        this.appearType = appearType;

        // Life initialization
        setX(x);
        setY(y);
        setFoothold(fh);
        setMoveAction(5); // idk

        // Mob initialization
        setCurrentFh(fh);
        setHp(mobInfo.getMaxHp());
        setMp(mobInfo.getMaxMp());
    }

    public int getTemplateId() {
        return this.mobInfo.getTemplateId();
    }

    public int getLevel() {
        return mobInfo.getLevel();
    }

    public int getMaxHp() {
        return mobInfo.getMaxHp();
    }

    public boolean isBoss() {
        return mobInfo.isBoss();
    }

    public boolean isDamagedByMob() {
        return mobInfo.isDamagedByMob();
    }

    public MobStatManager getMobStatManager() {
        return mobStatManager;
    }

    public Optional<MobAttack> getAttack(int attackIndex) {
        return Optional.ofNullable(mobInfo.getAttacks().get(attackIndex));
    }

    public Optional<MobSkill> getSkill(int skillIndex) {
        return Optional.ofNullable(mobInfo.getSkills().get(skillIndex));
    }

    public boolean isSkillAvailable(MobSkill mobSkill) {
        return skillCooltimes.getOrDefault(mobSkill, Instant.MIN).isBefore(Instant.now());
    }

    public void setSkillOnCooltime(MobSkill mobSkill, Instant nextAvailableTime) {
        skillCooltimes.put(mobSkill, nextAvailableTime);
    }

    public int getAndDecrementAttackCounter() {
        return attackCounter.getAndDecrement();
    }

    public void setAttackCounter(int value) {
        attackCounter.set(value);
    }

    public int getCurrentFh() {
        return currentFh;
    }

    public void setCurrentFh(int currentFh) {
        this.currentFh = currentFh;
    }

    public int getHp() {
        return hp;
    }

    public void setHp(int hp) {
        this.hp = hp;
    }

    public int getMp() {
        return mp;
    }

    public void setMp(int mp) {
        this.mp = mp;
    }

    @Override
    public User getController() {
        return controller;
    }

    @Override
    public void setController(User controller) {
        this.controller = controller;
    }

    @Override
    public OutPacket changeControllerPacket(boolean forController) {
        return MobPacket.mobChangeController(this, forController);
    }

    @Override
    public OutPacket enterFieldPacket() {
        return MobPacket.mobEnterField(this);
    }

    @Override
    public OutPacket leaveFieldPacket() {
        return MobPacket.mobLeaveField(this);
    }

    @Override
    public String toString() {
        return String.format("Mob { %d, oid : %d, hp : %d, mp : %d }", getTemplateId(), getId(), getHp(), getMp());
    }

    public void encodeInit(OutPacket outPacket) {
        // CMob::Init
        outPacket.encodeShort(getX()); // ptPosPrev.x
        outPacket.encodeShort(getY()); // ptPosPrev.y
        outPacket.encodeByte(getMoveAction()); // nMoveAction
        outPacket.encodeShort(getCurrentFh()); // pvcMobActiveObj (current foothold)
        outPacket.encodeShort(getFoothold()); // Foothold (start foothold)
        outPacket.encodeByte(appearType.getValue()); // nAppearType
        if (appearType == MobAppearType.REVIVED || appearType.getValue() >= 0) {
            outPacket.encodeInt(0); // dwOption
        }
        outPacket.encodeByte(0); // nTeamForMCarnival
        outPacket.encodeInt(0); // nEffectItemID
        outPacket.encodeInt(0); // nPhase
    }

    @Override
    public void lock() {
        lock.lock();
    }

    @Override
    public void unlock() {
        lock.unlock();
    }

    public static Mob from(LifeInfo lifeInfo, MobInfo mobInfo) {
        return new Mob(
                lifeInfo.getX(),
                lifeInfo.getY(),
                lifeInfo.getFh(),
                mobInfo,
                MobAppearType.NORMAL
        );
    }
}
