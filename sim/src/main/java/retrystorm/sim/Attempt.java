package retrystorm.sim;

/**
 * Guard for one attempt. Its completion and its timeout are both scheduled;
 * whichever fires first resolves the attempt and the other becomes a no-op.
 */
final class Attempt {

    private boolean resolved;

    boolean resolve() {
        if (resolved) {
            return false;
        }
        resolved = true;
        return true;
    }
}
