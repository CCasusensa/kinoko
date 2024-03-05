package kinoko.world.skill;

import kinoko.provider.SkillProvider;
import kinoko.provider.skill.SkillInfo;
import kinoko.provider.skill.SkillStat;
import kinoko.world.job.JobConstants;
import kinoko.world.job.resistance.Mechanic;
import kinoko.world.user.stat.BasicStat;
import kinoko.world.user.stat.SecondaryStat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class PassiveSkillData {
    public final List<AdditionPsd> additionPsd = new ArrayList<>();
    public int mhpR;
    public int mmpR;
    public int cr;
    public int cdMin;
    public int accR;
    public int evaR;
    public int ar;
    public int er;
    public int pddR;
    public int mddR;
    public int pdR;
    public int mdR;
    public int dipR;
    public int pdamR;
    public int mdamR;
    public int padR;
    public int madR;
    public int expR;
    public int impR;
    public int asrR;
    public int terR;
    public int mesoR;
    public int padX;
    public int madX;
    public int imdR;
    public int psdJump;
    public int psdSpeed;
    public int ocR;
    public int dcR;

    public void setFrom(BasicStat bs, SecondaryStat ss, SkillManager sm) {
        clearData();
        // TODO: guild skill

        // Add passive skill data
        for (SkillRecord skillRecord : sm.getSkillRecords()) {
            final Optional<SkillInfo> skillInfoResult = SkillProvider.getSkillInfoById(skillRecord.getSkillId());
            if (skillInfoResult.isEmpty()) {
                continue;
            }
            final SkillInfo si = skillInfoResult.get();
            if (si.isPsd() && (si.getSkillId() != Mechanic.PERFECT_ARMOR || ss.getRidingVehicle() == SkillConstants.MECHANIC_VEHICLE)) {
                if (si.getSkillId() == Mechanic.MECH_SIEGE_MODE_2) {
                    addPassiveSkillData(si, sm.getSkillLevel(Mechanic.MECH_SIEGE_MODE));
                } else {
                    addPassiveSkillData(si, skillRecord.getSkillLevel());
                }
            }
        }

        // Special handling for Mech: Siege Mode
        if (JobConstants.isMechanicJob(bs.getJob())) {
            if (sm.getSkillLevel(Mechanic.MECH_MISSILE_TANK) > 0) {
                final Optional<SkillInfo> skillInfoResult = SkillProvider.getSkillInfoById(Mechanic.MECH_SIEGE_MODE_2);
                if (skillInfoResult.isPresent()) {
                    addPassiveSkillData(skillInfoResult.get(), sm.getSkillLevel(Mechanic.MECH_SIEGE_MODE));
                }
            }
        }

        // Handle dice info
        final int[] diceInfo = ss.getDiceInfo().getInfoArray();
        this.mhpR += diceInfo[0];
        this.mmpR += diceInfo[1];
        this.cr += diceInfo[2];
        this.cdMin += diceInfo[3]; // 4 not used
        this.evaR += diceInfo[5];
        this.ar += diceInfo[6];
        this.er += diceInfo[7];
        this.pddR += diceInfo[8];
        this.mddR += diceInfo[9];
        this.pdR += diceInfo[10];
        this.mdR += diceInfo[11];
        this.dipR += diceInfo[12];
        this.pdamR += diceInfo[13];
        this.mdamR += diceInfo[14];
        this.padR += diceInfo[15];
        this.madR += diceInfo[16];
        this.expR += diceInfo[17];
        this.impR += diceInfo[18];
        this.asrR += diceInfo[19];
        this.terR += diceInfo[20];
        this.mesoR += diceInfo[21];

        // Revise passive skill data
        revisePassiveSkillData();
    }

    private void addPassiveSkillData(SkillInfo si, int slv) {
        this.mhpR += si.getValue(SkillStat.mhpR, slv);
        this.mmpR += si.getValue(SkillStat.mmpR, slv);
        if (this.additionPsd.isEmpty()) {
            this.cr += si.getValue(SkillStat.cr, slv);
            this.cdMin += si.getValue(SkillStat.criticaldamageMin, slv);
        }
        this.accR += si.getValue(SkillStat.accR, slv);
        this.evaR += si.getValue(SkillStat.evaR, slv);
        if (this.additionPsd.isEmpty()) {
            this.ar += si.getValue(SkillStat.ar, slv);
        }
        this.er += si.getValue(SkillStat.er, slv);
        this.pddR += si.getValue(SkillStat.pddR, slv);
        this.mddR += si.getValue(SkillStat.mddR, slv);
        this.pdR += si.getValue(SkillStat.pdr, slv);
        this.mdR += si.getValue(SkillStat.mdr, slv);
        if (this.additionPsd.isEmpty()) {
            this.dipR += si.getValue(SkillStat.damR, slv);
            this.pdamR += si.getValue(SkillStat.pdr, slv);
            this.mdamR += si.getValue(SkillStat.mdr, slv);
        }
        this.padR += si.getValue(SkillStat.padR, slv);
        this.madR += si.getValue(SkillStat.madR, slv);
        this.expR += si.getValue(SkillStat.expR, slv);
        this.impR += si.getValue(SkillStat.ignoreMobpdpR, slv);
        this.asrR += si.getValue(SkillStat.asrR, slv);
        this.terR += si.getValue(SkillStat.terR, slv);
        this.mesoR += si.getValue(SkillStat.mesoR, slv);
        this.padX += si.getValue(SkillStat.padX, slv);
        this.madX += si.getValue(SkillStat.madX, slv);
        this.imdR += si.getValue(SkillStat.ignoreMobDamR, slv);
        this.psdJump += si.getValue(SkillStat.psdJump, slv);
        this.psdSpeed += si.getValue(SkillStat.psdSpeed, slv);
        this.ocR += si.getValue(SkillStat.overChargeR, slv);
        this.dcR += si.getValue(SkillStat.disCountR, slv);

        if (!this.additionPsd.isEmpty()) {
            final AdditionPsd apsd = new AdditionPsd();
            apsd.cr += si.getValue(SkillStat.cr, slv);
            apsd.cdMin += si.getValue(SkillStat.criticaldamageMin, slv);
            apsd.ar += si.getValue(SkillStat.ar, slv);
            apsd.dipR += si.getValue(SkillStat.damR, slv);
            apsd.pdamR += si.getValue(SkillStat.pdr, slv);
            apsd.mdamR += si.getValue(SkillStat.mdr, slv);
            apsd.impR += si.getValue(SkillStat.ignoreMobpdpR, slv); // could be bug in client
            this.additionPsd.add(apsd);
        }
    }

    private void revisePassiveSkillData() {
        // Meso rate
        if (this.mesoR > 0) {
            this.mesoR = Math.max(this.mesoR, 100);
        } else {
            this.mesoR = 0;
        }
        // Overcharge rate
        if (this.ocR > 0) {
            this.ocR = Math.max(this.ocR, 50);
        } else {
            this.ocR = 0;
        }
        // Discount rate
        if (this.dcR > 0) {
            this.dcR = Math.max(this.dcR, 50);
        } else {
            this.dcR = 0;
        }
    }

    private void clearData() {
        this.mhpR = 0;
        this.mmpR = 0;
        this.cr = 0;
        this.cdMin = 0;
        this.accR = 0;
        this.evaR = 0;
        this.ar = 0;
        this.er = 0;
        this.pddR = 0;
        this.mddR = 0;
        this.pdR = 0;
        this.mdR = 0;
        this.dipR = 0;
        this.pdamR = 0;
        this.mdamR = 0;
        this.padR = 0;
        this.madR = 0;
        this.expR = 0;
        this.impR = 0;
        this.asrR = 0;
        this.terR = 0;
        this.mesoR = 0;
        this.padX = 0;
        this.madX = 0;
        this.imdR = 0;
        this.psdJump = 0;
        this.psdSpeed = 0;
        this.ocR = 0;
        this.dcR = 0;
        this.additionPsd.clear();
    }
}
