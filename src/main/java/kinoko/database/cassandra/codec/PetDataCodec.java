package kinoko.database.cassandra.codec;

import com.datastax.oss.driver.api.core.data.UdtValue;
import com.datastax.oss.driver.api.core.type.UserDefinedType;
import com.datastax.oss.driver.api.core.type.codec.MappingCodec;
import com.datastax.oss.driver.api.core.type.codec.TypeCodec;
import com.datastax.oss.driver.api.core.type.reflect.GenericType;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import kinoko.database.cassandra.type.PetDataUDT;
import kinoko.world.item.PetData;

public final class PetDataCodec extends MappingCodec<UdtValue, PetData> {
    public PetDataCodec(@NonNull TypeCodec<UdtValue> innerCodec, @NonNull GenericType<PetData> outerJavaType) {
        super(innerCodec, outerJavaType);
    }

    @NonNull
    @Override
    public UserDefinedType getCqlType() {
        return (UserDefinedType) super.getCqlType();
    }

    @Nullable
    @Override
    protected PetData innerToOuter(@Nullable UdtValue value) {
        if (value == null) {
            return null;
        }
        final PetData petData = new PetData();
        petData.setPetName(value.getString(PetDataUDT.PET_NAME));
        petData.setLevel(value.getByte(PetDataUDT.LEVEL));
        petData.setFullness(value.getByte(PetDataUDT.FULLNESS));
        petData.setTameness(value.getShort(PetDataUDT.TAMENESS));
        petData.setPetSkill(value.getShort(PetDataUDT.PET_SKILL));
        petData.setPetAttribute(value.getShort(PetDataUDT.PET_ATTRIBUTE));
        petData.setRemainLife(value.getInt(PetDataUDT.REMAIN_LIFE));
        return petData;
    }

    @Nullable
    @Override
    protected UdtValue outerToInner(@Nullable PetData petData) {
        if (petData == null) {
            return null;
        }
        return getCqlType().newValue()
                .setString(PetDataUDT.PET_NAME, petData.getPetName())
                .setByte(PetDataUDT.LEVEL, petData.getLevel())
                .setByte(PetDataUDT.FULLNESS, petData.getFullness())
                .setShort(PetDataUDT.TAMENESS, petData.getTameness())
                .setShort(PetDataUDT.PET_SKILL, petData.getPetSkill())
                .setShort(PetDataUDT.PET_ATTRIBUTE, petData.getPetAttribute())
                .setInt(PetDataUDT.REMAIN_LIFE, petData.getRemainLife());
    }
}
