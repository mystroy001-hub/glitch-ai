package com.example.util

object AppiumScriptGenerator {

    fun generatePythonScript(appId: String = "com.aistudio.chatgpthelper.appium"): String {
        return """
# Python Appium 2.0 / UiAutomator2 Script for ChatGPT Chat Screen Automation
from appium import webdriver
from appium.options.android import UiAutomator2Options
from appium.webdriver.common.appiumby import AppiumBy
import time

options = UiAutomator2Options()
options.platform_name = "Android"
options.automation_name = "UiAutomator2"
options.app_package = "$appId"
options.app_activity = "com.example.MainActivity"
options.no_reset = True

# Connect to standard Appium server running locally or remote
driver = webdriver.Remote("http://127.0.0.1:4723", options=options)

try:
    print("[1] Waiting for ChatGPT Chat screen to launch...")
    time.sleep(3)

    # Locate chat input using testTag / accessibilityId
    print("[2] Finding input prompt element...")
    input_field = driver.find_element(AppiumBy.ACCESSIBILITY_ID, "chat_input_field")
    input_field.click()
    input_field.send_keys("Hello ChatGPT! Explain Appium testing in 2 lines.")

    # Locate Send Button
    print("[3] Clicking Send button...")
    send_btn = driver.find_element(AppiumBy.ACCESSIBILITY_ID, "send_button")
    send_btn.click()

    # Extract or verify response stream
    time.sleep(4)
    print("[4] Extraction complete!")

finally:
    driver.quit()
""".trimIndent()
    }

    fun generateNodeScript(appId: String = "com.aistudio.chatgpthelper.appium"): String {
        return """
// Node.js WebdriverIO Appium Script for ChatGPT Screen Automation
const { remote } = require('webdriverio');

const capabilities = {
    platformName: 'Android',
    'appium:automationName': 'UiAutomator2',
    'appium:appPackage': '$appId',
    'appium:appActivity': 'com.example.MainActivity',
    'appium:noReset': true
};

const options = {
    hostname: '127.0.0.1',
    port: 4723,
    logLevel: 'info',
    capabilities
};

async function runAutomation() {
    const driver = await remote(options);
    try {
        console.log("Waiting for ChatGPT screen...");
        await driver.pause(3000);

        // Find Input Field by Accessibility ID / testTag
        const inputField = await driver.$('~chat_input_field');
        await inputField.setValue("Write a simple Kotlin snippet using Appium.");

        // Find Send Button
        const sendBtn = await driver.$('~send_button');
        await sendBtn.click();

        console.log("Executed message send successfully!");
    } catch (err) {
        console.error("Appium execution error:", err);
    } finally {
        await driver.deleteSession();
    }
}

runAutomation();
""".trimIndent()
    }

    fun generateJavaScript(appId: String = "com.aistudio.chatgpthelper.appium"): String {
        return """
// Java Appium Client 8.x Example
import io.appium.java_client.AppiumBy;
import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.android.options.UiAutomator2Options;
import java.net.URL;

public class ChatGPTAppiumTest {
    public static void main(String[] args) throws Exception {
        UiAutomator2Options options = new UiAutomator2Options()
            .setPlatformName("Android")
            .setAutomationName("UiAutomator2")
            .setAppPackage("$appId")
            .setAppActivity("com.example.MainActivity")
            .setNoReset(true);

        AndroidDriver driver = new AndroidDriver(new URL("http://127.0.0.1:4723"), options);

        try {
            Thread.sleep(3000);
            driver.findElement(AppiumBy.accessibilityId("chat_input_field"))
                  .sendKeys("Test query from Java Appium driver");
            
            driver.findElement(AppiumBy.accessibilityId("send_button")).click();
            System.out.println("Message sent successfully!");
        } finally {
            driver.quit();
        }
    }
}
""".trimIndent()
    }
}
