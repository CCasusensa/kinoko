package kinoko.world.field;

import kinoko.provider.map.PortalInfo;
import kinoko.world.GameConstants;
import kinoko.world.user.User;

import java.util.List;
import java.util.Optional;

public final class TownPortal extends FieldObjectImpl {
    private final User owner;
    private final int skillId;
    private final Field townField;

    public TownPortal(User owner, int skillId, Field townField) {
        this.owner = owner;
        this.skillId = skillId;
        this.townField = townField;
    }

    public User getOwner() {
        return owner;
    }

    public int getSkillId() {
        return skillId;
    }

    public Field getTownField() {
        return townField;
    }

    public Optional<PortalInfo> getTownPortalPoint() {
        final List<PortalInfo> townPortalPoints = townField.getMapInfo().getTownPortalPoints();
        if (townPortalPoints.isEmpty()) {
            return townField.getPortalByName(GameConstants.DEFAULT_PORTAL_NAME);
        }
        final PortalInfo portalInfo = townPortalPoints.get(owner.getTownPortalIndex() % townPortalPoints.size());
        return Optional.of(portalInfo);
    }

    public void destroy() {
        getField().getTownPortalPool().removeTownPortal(this);
        getTownField().getTownPortalPool().removeTownPortal(this);
    }

    @Override
    public String toString() {
        return "TownPortal{" +
                "owner=" + owner.getCharacterId() +
                ", index=" + owner.getTownPortalIndex() +
                ", field=" + getField().getFieldId() +
                ", townField=" + townField.getFieldId() +
                '}';
    }

    public static TownPortal from(User owner, int skillId, Field townField, Field targetField, int targetX, int targetY) {
        final TownPortal townPortal = new TownPortal(owner, skillId, townField);
        townPortal.setId(owner.getId());
        townPortal.setField(targetField);
        townPortal.setX(targetX);
        townPortal.setY(targetY);
        return townPortal;
    }
}
