package eu.materadios;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MateradiosApplication {
    public static void main(String[] args) {
        // Robust .env loader: locate .env by looking in current directory and up the parents, then user.home
        java.nio.file.Path found = null;
        try {
            java.nio.file.Path cur = java.nio.file.Paths.get(".").toAbsolutePath().normalize();
            java.nio.file.Path p = cur;
            while (p != null) {
                java.nio.file.Path cand = p.resolve(".env");
                if (java.nio.file.Files.exists(cand)) { found = cand; break; }
                p = p.getParent();
            }
            if (found == null) {
                java.nio.file.Path home = java.nio.file.Paths.get(System.getProperty("user.home", ""));
                if (home != null) {
                    java.nio.file.Path cand = home.resolve(".env");
                    if (java.nio.file.Files.exists(cand)) found = cand;
                }
            }

            if (found != null) {
                String content = java.nio.file.Files.readString(found);
                java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(?m)(MATERA_[A-Z_]+)\\s*=\\s*(\"([^\"]*)\"|'([^']*)'|([^\\r\\n#]+))");
                java.util.regex.Matcher m = pattern.matcher(content);
                while (m.find()) {
                    String key = m.group(1);
                    String val = null;
                    if (m.group(3) != null) val = m.group(3);
                    else if (m.group(4) != null) val = m.group(4);
                    else if (m.group(5) != null) val = m.group(5);
                    if (val == null) val = "";
                    val = val.trim();
                    String prop;
                    if ("MATERA_USERNAME".equals(key)) prop = "matera.username";
                    else if ("MATERA_PASSWORD".equals(key)) prop = "matera.password";
                    else if ("MATERA_BASE_URL".equals(key)) prop = "matera.base.url";
					else if ("MATERA_API_URL".equals(key))
						prop = "matera.api.url";
                    else prop = key.toLowerCase().replace('_', '.');
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
