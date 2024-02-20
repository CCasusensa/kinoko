package kinoko.world.user.stat;

import kinoko.world.job.Job;
import kinoko.world.job.JobConstants;

public class StatConstants {
    public static final int INC_HP_VARIANCE = 4;
    public static final int INC_MP_VARIANCE = 2; // 4 for pirates?

    public static int getIncAp(int jobId) {
        return 5;
    }

    public static int getIncSp(int jobId) {
        return 3;
    }

    public static int getIncHp(int jobId) {
        // may be inaccurate as we are just using minHp multiplier
        if (JobConstants.isBeginnerJob(jobId)) {
            return 12;
        }
        if (JobConstants.isEvanJob(jobId)) {
            return 16;
        }
        if (JobConstants.isBattleMageJob(jobId)) {
            return 34;
        }
        switch (jobId % 1000 / 100) {
            case 1 -> {
                return 24;
            }
            case 2 -> {
                return 10;
            }
            case 3, 4 -> {
                return 20;
            }
            case 5 -> {
                return 22;
            }
            default -> {
                return 0;
            }
        }
    }

    public static int getIncMp(int jobId) {
        // may be inaccurate as we are just using minMp multiplier
        if (JobConstants.isBeginnerJob(jobId)) {
            return 10;
        }
        if (JobConstants.isEvanJob(jobId)) {
            return 35;
        }
        if (JobConstants.isBattleMageJob(jobId)) {
            return 22;
        }
        switch (jobId % 1000 / 100) {
            case 1 -> {
                return 4;
            }
            case 2 -> {
                return 22;
            }
            case 3, 4 -> {
                return 14;
            }
            case 5 -> {
                return 18;
            }
            default -> {
                return 0;
            }
        }
    }

    public static int getIncHpByAp(int jobId) {
        // IncHPVal
        switch (jobId % 1000 / 100) {
            case 0 -> {
                return 8;
            }
            case 1 -> {
                return 20;
            }
            case 2 -> {
                if (JobConstants.isBattleMageJob(jobId)) {
                    return 20;
                }
                return JobConstants.isEvanJob(jobId) ? 12 : 6;
            }
            case 3, 4 -> {
                return 16;
            }
            case 5 -> {
                return 18;
            }
            default -> {
                return 0;
            }
        }
    }

    public static int getIncMpByAp(int jobId) {
        // IncMPVal
        switch (jobId % 1000 / 100) {
            case 0 -> {
                return 6;
            }
            case 1 -> {
                return 2;
            }
            case 2 -> {
                return 18;
            }
            case 3, 4 -> {
                return 10;
            }
            case 5 -> {
                return 14;
            }
            default -> {
                return 0;
            }
        }
    }

    public static int getMinHp(int level, int jobId) {
        // CUIStatChange::IsUnderMinHP
        switch (Job.getById(jobId)) {
            case EVAN_1, EVAN_2, EVAN_3, EVAN_4, EVAN_5, EVAN_6, EVAN_7, EVAN_8, EVAN_9, EVAN_10 -> {
                return 16 * level - 2;
            }
            case MECHANIC_1 -> {
                return 22 * (level + 4);
            }
            case MECHANIC_2, MECHANIC_3, MECHANIC_4 -> {
                return 22 * level + 238;
            }
            case WILD_HUNTER_1 -> {
                return 20 * level + 108;
            }
            case WILD_HUNTER_2, WILD_HUNTER_3, WILD_HUNTER_4 -> {
                return 20 * level + 258;
            }
            case BATTLE_MAGE_1 -> {
                return 34 * level - 32;
            }
            case BATTLE_MAGE_2, BATTLE_MAGE_3, BATTLE_MAGE_4 -> {
                return 34 * level + 168;
            }
            default -> {
                final int jobSuffix = jobId % 1000;
                switch (jobSuffix) {
                    case 100, 120, 121, 122, 130, 131, 132 -> {
                        return 24 * level + 118;
                    }
                    case 110, 111, 112 -> {
                        return 24 * level + 418;
                    }
                    case 200, 210, 211, 212, 220, 221, 222, 230, 231, 232 -> {
                        return 10 * level + 54;
                    }
                    case 300, 400 -> {
                        return 20 * level + 58;
                    }
                    case 310, 311, 312, 320, 321, 322, 410, 411, 412, 420, 421, 422 -> {
                        return 20 * level + 358;
                    }
                    case 430, 431, 432, 433, 434 -> {
                        return 20 * level + 808;
                    }
                    case 500 -> {
                        return 22 * level + 38;
                    }
                    case 510, 511, 512, 520, 521, 522 -> {
                        return 22 * level + 338;
                    }
                    case 0, 1 -> {
                        return 12 * level + 38;
                    }
                    default -> {
                        return 0;
                    }
                }
            }
        }
    }

    public static int getMinMp(int level, int jobId) {
        // CUIStatChange::IsUnderMinMP
        switch (Job.getById(jobId)) {
            case EVAN_1, EVAN_2, EVAN_3, EVAN_4, EVAN_5, EVAN_6, EVAN_7, EVAN_8, EVAN_9, EVAN_10 -> {
                int mp = 150;
                if (jobId < 2210 || jobId > 2214) {
                    if (jobId >= 2215) {
                        mp = 650;
                    }
                } else {
                    return 100 * (jobId % 10) + 250;
                }
                return 35 * level + mp - 275;
            }
            case MECHANIC_1 -> {
                return 18 * level - 17;
            }
            case MECHANIC_2, MECHANIC_3, MECHANIC_4 -> {
                return 18 * level + 83;
            }
            case WILD_HUNTER_1 -> {
                return 14 * level + 23;
            }
            case WILD_HUNTER_2, WILD_HUNTER_3, WILD_HUNTER_4 -> {
                return 14 * level + 123;
            }
            case BATTLE_MAGE_1 -> {
                return 22 * level + 43;
            }
            case BATTLE_MAGE_2, BATTLE_MAGE_3, BATTLE_MAGE_4 -> {
                return 22 * level + 143;
            }
            default -> {
                final int jobSuffix = jobId % 1000;
                switch (jobSuffix) {
                    case 100, 110, 111, 112 -> {
                        return 4 * level + 55;
                    }
                    case 120, 121, 122, 130, 131, 132 -> {
                        return 4 * level + 155;
                    }
                    case 200 -> {
                        return 22 * level - 1;
                    }
                    case 210, 211, 212, 220, 221, 222, 230, 231, 232 -> {
                        return 22 * level + 449;
                    }
                    case 300, 400 -> {
                        return 14 * level - 15;
                    }
                    case 310, 311, 312, 320, 321, 322, 410, 411, 412, 420, 421, 422 -> {
                        return 14 * level + 135;
                    }
                    case 430, 431, 432, 433, 434 -> {
                        return 14 * level + 355;
                    }
                    case 500 -> {
                        return 18 * level - 55;
                    }
                    case 510, 511, 512, 520, 521, 522 -> {
                        return 18 * level + 95;
                    }
                    case 0, 1 -> {
                        return 10 * level - 5;
                    }
                    default -> {
                        return 0;
                    }
                }
            }
        }
    }

}
