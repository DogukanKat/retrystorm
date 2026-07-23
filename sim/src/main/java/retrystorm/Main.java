package retrystorm;

public final class Main {

    private static final int EXIT_USAGE = 2;

    private Main() {
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("usage: retrystorm <scenario.yaml>");
            System.exit(EXIT_USAGE);
            return;
        }

        System.out.println("retrystorm " + version());
        System.out.println("scenario: " + args[0]);
        System.out.println("simulation core not implemented yet");
    }

    private static String version() {
        String v = Main.class.getPackage().getImplementationVersion();
        return v != null ? v : "dev";
    }
}
