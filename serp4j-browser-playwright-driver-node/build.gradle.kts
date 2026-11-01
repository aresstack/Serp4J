plugins {
    id("serp4j.java8")
    id("serp4j.published")
    id("serp4j.browser-tests")
}

description = "Serp4J Playwright driver module: official Node driver (com.microsoft.playwright:driver-bundle), Java 8"

serp4j {
    automaticModuleName.set("com.aresstack.serp4j.browser.playwright.driver.node")
}

dependencies {
    implementation(project(":serp4j-browser-playwright"))
    runtimeOnly(libs.playwright.driver)
    runtimeOnly(libs.playwright.driver.bundle)

    "browserTestImplementation"(testFixtures(project(":serp4j-browser-playwright")))
}

// Explicit, never implicit: installs the Playwright browser build that matches this Playwright version.
// CI: ./gradlew :serp4j-browser-playwright-driver-node:installPlaywrightBrowsers -Pserp4j.playwrightInstall="install --with-deps chromium"
tasks.register<JavaExec>("installPlaywrightBrowsers") {
    description = "Installs Playwright browsers with the Playwright CLI (explicit; Serp4J never downloads browsers itself)."
    group = "browser"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.microsoft.playwright.CLI")
    args = providers.gradleProperty("serp4j.playwrightInstall").getOrElse("install chromium").split(" ")
}
