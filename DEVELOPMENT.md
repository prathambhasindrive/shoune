# Development

The active project is `E:\Projects\Assistant`. The old D-drive paths and initial milestone notes are obsolete.

Start with README.md, docs/ARCHITECTURE.md and docs/TESTING.md. Android Studio can open this folder directly. Use the Gradle wrapper and JDK 17; do not use the system Java 8 installation. `local.properties` points to the machine-local Android SDK and must not be committed as a portable SDK path.

Android component identities are intentionally stable across upgrades. Pure command parsing, speech utilities and local inference now live in separate packages. Keep Android component files focused on their lifecycle and delegate actions to the shared controller.
