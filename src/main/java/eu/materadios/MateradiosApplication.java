package eu.materadios;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MateradiosApplication {
    public static void main(String[] args) {
        // Robust .env loader: locate .env by looking in current directory and up the
        // parents, then user.home
        Path found = null;
        try {
            Path cur = Paths.get(".").toAbsolutePath().normalize();
            Path p = cur;
            while (p != null) {
                Path cand = p.resolve(".env");
                if (Files.exists(cand)) {
                    found = cand;
                    break;
                }
                p = p.getParent();
            }
            if (found == null) {
                Path home = Paths.get(System.getProperty("user.home", ""));
                if (home != null) {
                    Path cand = home.resolve(".env");
                    if (Files.exists(cand))
                        found = cand;
                }
            }

            if (found != null) {
                String content = Files.readString(found);
                Pattern pattern = Pattern
                        .compile("(?m)((?:MATERA|GOOGLE)_[A-Z_]+)\\s*=\\s*(\"([^\"]*)\"|'([^']*)'|([^\\r\\n#]+))");
                Matcher m = pattern.matcher(content);
                while (m.find()) {
                    String key = m.group(1);
                    String val = null;
                    if (m.group(3) != null)
                        val = m.group(3);
                    else if (m.group(4) != null)
                        val = m.group(4);
                    else if (m.group(5) != null)
                        val = m.group(5);
                    if (val == null)
                        val = "";
                    val = val.trim();
                    String prop;
                    if ("MATERA_USERNAME".equals(key))
                        prop = "matera.username";
                    else if ("MATERA_PASSWORD".equals(key))
                        prop = "matera.password";
                    else if ("MATERA_BASE_URL".equals(key))
                        prop = "matera.base.url";
                    else if ("MATERA_API_URL".equals(key))
                        prop = "matera.api.url";
                    else
                        prop = key;
                    System.setProperty(prop, val);
                }
                System.out.println("Loaded .env into system properties from: " + found.toString());
            } else {
                System.out.println("No .env found in current dir, parents, or user.home");
            }
        } catch (Exception e) {
            System.err.println("Failed to read .env: " + e.getMessage());
        }

        SpringApplication.run(MateradiosApplication.class, args);
    }
}
