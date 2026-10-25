package com.axplatform.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;
import java.nio.file.*;
import java.time.Clock;
import static org.assertj.core.api.Assertions.*;

class PortableStorageTest {
    @TempDir Path temp;

    @Test void automaticRootsCoverWindowsWithAndWithoutDDriveAndMac() {
        assertThat(ManagedStorage.automaticRoot(true, true, "C:/Users/test", "dev").toString().replace('\\','/')).isEqualTo("D:/AxPlatform/dev");
        assertThat(ManagedStorage.automaticRoot(true, false, "C:/Users/test", "dev").toString().replace('\\','/')).isEqualTo("C:/Users/test/AxPlatform/dev");
        assertThat(ManagedStorage.automaticRoot(false, false, "/Users/test", "dev")).isEqualTo(Path.of("/Users/test/AxPlatform/dev"));
        assertThat(ManagedStorage.automaticRoot(false, true, "/Users/test", "test")).isEqualTo(Path.of("/Users/test/AxPlatform/test"));
    }

    @Test void freshLocalRootIsCreatedAndMarkerSurvivesRestart() throws Exception {
        var env = new MockEnvironment().withProperty("ax.storage.root", temp.resolve("new").toString())
                .withProperty("ax.storage.binding-mode", "per-device");
        var first = new ManagedStorage(env, Clock.systemUTC());
        var second = new ManagedStorage(env, Clock.systemUTC());
        assertThat(first.perDevice()).isTrue();
        assertThat(first.volumeId()).isEqualTo(second.volumeId());
        first.memberFolders(5);
        assertThat(temp.resolve("new/members/5/EXCEL")).isDirectory();
    }

    @Test void badModeAndEnvironmentStillFailClearly() {
        var env = new MockEnvironment().withProperty("ax.storage.root", temp.toString())
                .withProperty("ax.storage.binding-mode", "typo");
        assertThatThrownBy(() -> new ManagedStorage(env, Clock.systemUTC())).hasMessageContaining("binding-mode");
        env.withProperty("ax.storage.binding-mode", "per-device").withProperty("ax.storage.environment", "../escape");
        assertThatThrownBy(() -> new ManagedStorage(env, Clock.systemUTC())).hasMessageContaining("환경");
    }
}
