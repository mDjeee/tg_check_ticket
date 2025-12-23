package com.example.demo.service;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.stereotype.Service;

@Service
public class CsrfService {

    public String[] getCsrfTokenAndCookie() {
        WebDriverManager.chromedriver().setup();
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless"); // run headless
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");

        WebDriver driver = new ChromeDriver(options);
        try {
            driver.get("https://e-ticket.railway.uz/");

            // Wait for the page to load and JS to set cookies
            Thread.sleep(3000);

            // Get XSRF-TOKEN cookie
            Cookie xsrfCookie = driver.manage().getCookieNamed("XSRF-TOKEN");
            if (xsrfCookie == null) return null;

            String token = xsrfCookie.getValue();

            // Build cookie string (add other cookies if needed)
            StringBuilder cookieHeader = new StringBuilder();
            for (Cookie c : driver.manage().getCookies()) {
                cookieHeader.append(c.getName()).append("=").append(c.getValue()).append("; ");
            }

            return new String[]{token, cookieHeader.toString()};
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            driver.quit();
        }
    }
}
