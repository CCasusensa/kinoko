package kinoko.util;

import java.security.SecureRandom;
import java.util.Collection;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Random;
import java.util.function.ToDoubleFunction;

public final class Util {
    private static final HexFormat hexFormat = HexFormat.ofDelimiter(" ").withUpperCase();
    private static final Random random = new SecureRandom();

    public static String readableByteArray(byte[] array) {
        return hexFormat.formatHex(array);
    }

    public static String opToString(short op) {
        return String.format("%d/0x%X", op, op);
    }

    public static Random getRandom() {
        return random;
    }

    public static int getRandom(int bound) {
        return random.nextInt(bound);
    }

    public static int getRandom(int origin, int bound) {
        return random.nextInt(origin, bound);
    }

    public static boolean succeedProp(int chance, int max) {
        return random.nextInt(max) < chance;
    }

    public static boolean succeedProp(int chance) {
        return succeedProp(chance, 100);
    }

    public static <T> Optional<T> getRandomFromCollection(Collection<T> collection) {
        return getRandomFromCollection(collection, (ignored) -> 1.0);
    }

    public static <T> Optional<T> getRandomFromCollection(Collection<T> collection, ToDoubleFunction<T> weightFunction) {
        if (collection.isEmpty()) {
            return Optional.empty();
        }
        final double totalWeight = collection.stream().mapToDouble(weightFunction).sum();
        double r = random.nextDouble() * totalWeight;
        for (T item : collection) {
            r -= weightFunction.applyAsDouble(item);
            if (r <= 0.0) {
                return Optional.of(item);
            }
        }
        return Optional.empty();
    }

    public static double distance(int x1, int y1, int x2, int y2) {
        final int dx = x1 - x2;
        final int dy = y1 - y2;
        return Math.sqrt(dx * dx + dy * dy);
    }

    public static boolean isInteger(String string) {
        return string != null && string.matches("^-?\\d+$");
    }
}
