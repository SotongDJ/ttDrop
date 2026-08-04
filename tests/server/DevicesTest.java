import java.nio.file.Files;
import java.nio.file.Path;

import ttdrop.server.Devices;

/**
 * Headless registry test: name validation at pairing (checked before
 * the code is consumed), uniqueness, and rename — including the
 * folder rename and the legacy-uppercase case. Run:
 * java -cp dist/ttdrop.jar tests/server/DevicesTest.java
 */
public final class DevicesTest {
    static int pass = 0;
    static int fail = 0;

    public static void main(String[] args) throws Exception {
        Path configDir = Files.createTempDirectory("devices-config");
        Path root = Files.createTempDirectory("devices-root");
        Devices devices = new Devices(configDir);

        String code = devices.newPairingCode();
        check("bad name rejected", "name".equals(devices.pair(code, "Bad-Name", root).error()));
        check("bad name does not burn the code",
            devices.pair(code, "dev_a", root).token() != null);
        check("device folder created", Files.isDirectory(root.resolve("dev_a")));
        String idA = devices.list().get(0).id();

        String code2 = devices.newPairingCode();
        check("duplicate name rejected", "taken".equals(devices.pair(code2, "dev_a", root).error()));
        check("used code rejected", "code".equals(devices.pair(code, "dev_b", root).error()));
        check("fresh code still valid after rejections",
            devices.pair(code2, "dev_b", root).token() != null);

        check("rename rejects upper case", "name".equals(devices.rename(idA, "DevA", root)));
        check("rename rejects taken name", "taken".equals(devices.rename(idA, "dev_b", root)));
        check("rename succeeds", devices.rename(idA, "dev_c", root) == null);
        check("folder renamed with the device",
            Files.isDirectory(root.resolve("dev_c")) && !Files.exists(root.resolve("dev_a")));
        check("registry carries the new name",
            devices.get(idA).name().equals("dev_c")
                && devices.get(idA).relPath().equals("dev_c"));

        // Legacy pre-v0.18 devices can carry upper-case names/folders;
        // renaming them must move the old folder to the new lower-case name.
        Files.writeString(configDir.resolve("devices.properties"), String.join("\n",
            "d.legacy1.name=Windows",
            "d.legacy1.hash=" + "0".repeat(64),
            "d.legacy1.path=Windows",
            "d.legacy1.read=true", "d.legacy1.write=true", "d.legacy1.browse=true") + "\n");
        Files.createDirectories(root.resolve("Windows"));
        Devices legacy = new Devices(configDir);
        check("legacy uppercase device loads", legacy.get("legacy1") != null);
        check("legacy rename succeeds", legacy.rename("legacy1", "win_pc", root) == null);
        check("legacy folder moved to the new name",
            Files.isDirectory(root.resolve("win_pc")) && !Files.exists(root.resolve("Windows")));

        System.out.println(fail == 0 ? "TEST PASS" : "TEST FAIL");
        System.exit(fail == 0 ? 0 : 1);
    }

    static void check(String label, boolean cond) {
        if (cond) {
            pass++;
            System.out.println("PASS: " + label);
        } else {
            fail++;
            System.out.println("FAIL: " + label);
        }
    }
}
