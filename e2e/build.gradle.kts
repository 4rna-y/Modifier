import java.time.Duration

plugins {
    java
}

group = "io.github.modifier"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.14.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 25
}

val botDir = layout.projectDirectory.dir("bot")

/**
 * 併せて載せる wiah プラグインの置き場所。
 *
 * <p>死亡でワールドをリセットするのは向こうの役目なので、その噛み合わせを見るテスト
 * (WorldResetE2eTest) だけが要る。別リポジトリなので、無ければそのテストは飛ばす。
 * 場所を変えたいときは -Pmodifier.wiahDir=... で差せる。
 */
val wiahDir: File = (findProperty("modifier.wiahDir") as String?)?.let(::file)
    ?: rootProject.projectDir.parentFile.resolve("world_is_also_hardcore")

/** wiah を (あれば) ビルドする。無くても・失敗しても e2e 自体は続ける。 */
val buildWiah = tasks.register<Exec>("buildWiah") {
    description = "併せて載せる wiah プラグインをビルドする (別リポジトリ。無ければ何もしない)"
    workingDir = wiahDir
    // 別ビルドなので gradle を素で呼ぶ。dev shell の外だと gradle が居ないが、
    // そのときは jar が見つからずテスト側が skip するので、ここでは失敗させない。
    commandLine("sh", "-c", "gradle --console=plain -q :plugin:jar || true")
    onlyIf { wiahDir.resolve("settings.gradle.kts").isFile }
    isIgnoreExitValue = true
}

/** wiah のビルド成果物。見つからなければ null。 */
fun wiahJar(): File? = wiahDir.resolve("plugin/build/libs").listFiles()
    ?.firstOrNull { it.name.endsWith(".jar") && !it.name.endsWith("-sources.jar") }

/** ヘッドレスクライアントの依存を入れる。入っていればスキップする。 */
val installBot = tasks.register<Exec>("installBot") {
    description = "e2e 用ヘッドレスクライアントの npm 依存を入れる"
    workingDir = botDir.asFile
    commandLine("npm", "install", "--no-audit", "--no-fund")
    onlyIf { !botDir.dir("node_modules/mineflayer").asFile.isDirectory }
    // npm が無い環境ではテスト側が assumeTrue で飛ばすので、ここでは失敗させない
    isIgnoreExitValue = true
}

// 実サーバーを起動するので数分かかる。gradle build には載せない。
tasks.test {
    enabled = false
}

tasks.register<Test>("e2eTest") {
    group = "verification"
    description = "本番の jar を 26.1 のサーバーに載せ、ヘッドレスクライアントで通し検証する (数分かかる)"

    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    dependsOn(installBot, buildWiah, ":plugin:jar")

    useJUnitPlatform()
    // 1つのサーバーの一生を順番に検証するので、並列実行はしない。
    maxParallelForks = 1

    // 外部プロセス (サーバー / ボット) と実ファイルを相手にするので、
    // Gradle の入力だけでは結果の鮮度を判断できない。呼ばれたら必ず走らせる。
    outputs.upToDateWhen { false }
    // それとは別に、プラグインを直せば再実行すべきなのを入力として明示しておく。
    inputs.file(project(":plugin").tasks.named<Jar>("jar").flatMap { it.archiveFile })
        .withPropertyName("pluginJar")
    timeout = Duration.ofMinutes(30)

    systemProperty("modifier.buildDir", layout.buildDirectory.get().asFile.absolutePath)
    systemProperty("modifier.botDir", botDir.asFile.absolutePath)
    doFirst {
        val jar = project(":plugin").tasks.named<Jar>("jar").get().archiveFile.get().asFile
        systemProperty("modifier.pluginJar", jar.absolutePath)

        val wiah = wiahJar()
        systemProperty("modifier.wiahJar", wiah?.absolutePath ?: "")
        // 黙って飛ばすと「検証した」ように読めてしまうので、飛ばす理由は必ず出す。
        if (wiah == null) {
            logger.warn("wiah の jar が $wiahDir に無いため、"
                + "ワールドリセットとの噛み合わせの検証は飛ばします。")
        }
    }

    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = false
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
