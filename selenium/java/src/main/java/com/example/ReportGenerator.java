package com.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

public class ReportGenerator {

    private static class Config {
        String username = "";
        String framework = "(unknown)";
        String language = "(unknown)";
    }

    public static void main(String[] args) {
        Path repoRoot = Path.of("").toAbsolutePath();
        Path jsonPath = repoRoot.resolve("report.json");
        Path reportDir = repoRoot.resolve("docs");

        try {
            if (!Files.exists(reportDir)) Files.createDirectories(reportDir);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        Config cfg = readConfig(jsonPath);
        String username = cfg.username;
        String framework = cfg.framework;
        String language = cfg.language;

        System.out.printf("🌐 Running test for %s in %s%n", framework.toUpperCase(), language.toUpperCase());

        WebDriverManager.chromedriver().setup();
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new", "--no-sandbox", "--disable-dev-shm-usage", "--window-size=1920,1080", "--disable-gpu");

        WebDriver driver = null;
        boolean isFilled = false;
        boolean isCleared = false;

        Path step1 = reportDir.resolve("step1.png");
        Path step2 = reportDir.resolve("step2.png");

        try {
            driver = new ChromeDriver(options);
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));

            driver.get("https://login.salesforce.com/");
            System.out.println("✅ Opened Salesforce login page");

            WebElement usernameField = null;
            try {
                usernameField = driver.findElement(By.id("username"));
            } catch (NoSuchElementException e) {
                System.err.println("❌ Username field not found on page.");
            }

            if (usernameField != null) {
                usernameField.sendKeys(username);
                String filledValue = usernameField.getAttribute("value");
                isFilled = !username.isEmpty() && filledValue.equals(username);
            }
            takeScreenshot(driver, step1.toFile());
            System.out.println("📸 step1.png captured — username filled check: " + (isFilled ? "PASS" : "FAIL"));

            if (usernameField != null) {
                usernameField.clear();
                String clearedValue = usernameField.getAttribute("value");
                isCleared = clearedValue.isEmpty();
            }
            takeScreenshot(driver, step2.toFile());
            System.out.println("📸 step2.png captured — username cleared check: " + (isCleared ? "PASS" : "FAIL"));

            Path reportHtml = reportDir.resolve("report.html");
            generateHtmlReport(reportHtml, step1.getFileName().toString(), step2.getFileName().toString(),
                    isFilled, isCleared, username, framework, language);
            System.out.println("📄 Report generated at: " + reportHtml.toAbsolutePath());

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (driver != null) {
                try { driver.quit(); } catch (Exception ignored) {}
            }
        }
    }

    private static Config readConfig(Path jsonPath) {
        Config cfg = new Config();
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(jsonPath.toFile());
            if (root.has("username")) cfg.username = root.get("username").asText();
            if (root.has("framework")) cfg.framework = root.get("framework").asText();
            if (root.has("language")) cfg.language = root.get("language").asText();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return cfg;
    }

    private static void takeScreenshot(WebDriver driver, File outFile) throws IOException {
        if (driver instanceof TakesScreenshot ts) {
            byte[] bytes = ts.getScreenshotAs(OutputType.BYTES);
            Files.write(outFile.toPath(), bytes);
        }
    }

    private static void generateHtmlReport(Path target, String step1Name, String step2Name,
                                           boolean filledOk, boolean clearedOk,
                                           String username, String framework, String language) throws IOException {
        String html = """
            <!doctype html>
            <html lang="en">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <title>Automation Test Report</title>
              <style>
                body { font-family: Arial, sans-serif; padding: 18px; }
                table { border-collapse: collapse; width: 100%%; max-width: 900px; }
                th, td { border: 1px solid #ccc; padding: 8px; text-align: left; }
                th { background: #f2f2f2; }
                .pass { color: green; font-weight: bold; }
                .fail { color: red; font-weight: bold; }
                img.sshot { max-width: 320px; border: 1px solid #999; }
              </style>
            </head>
            <body>
            <h1>Automated Test Report</h1>
            <p><strong>Username value used:</strong> %s</p>
            <p><strong>Framework:</strong> %s</p>
            <p><strong>Language:</strong> %s</p>

            <table>
              <thead>
                <tr><th>No.</th><th>Step</th><th>Result</th><th>Screenshot</th></tr>
              </thead>
              <tbody>
                <tr>
                  <td>1.</td>
                  <td>Fill value in username field.</td>
                  <td class="%s">%s</td>
                  <td><img class="sshot" src="%s" alt="step1" /></td>
                </tr>
                <tr>
                  <td>2.</td>
                  <td>Empty value in username field.</td>
                  <td class="%s">%s</td>
                  <td><img class="sshot" src="%s" alt="step2" /></td>
                </tr>
              </tbody>
            </table>
            <p>Generated automatically by <b>%s</b> (%s).</p>
            </body>
            </html>
            """;

        String finalHtml = String.format(
                html,
                username.isEmpty() ? "(Not Found in report.json)" : username,
                framework, language,
                filledOk ? "pass" : "fail", filledOk ? "PASS" : "FAIL", step1Name,
                clearedOk ? "pass" : "fail", clearedOk ? "PASS" : "FAIL", step2Name,
                framework, language
        );

        Files.writeString(target, finalHtml,
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
    }
}
