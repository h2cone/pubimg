package dev.h2cone.pubimg;

import com.google.cloud.tools.jib.api.*;
import com.google.cloud.tools.jib.event.events.ProgressEvent;
import com.google.cloud.tools.jib.event.progress.Allocation;
import dev.failsafe.Failsafe;
import dev.failsafe.RetryPolicy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.SocketException;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static picocli.CommandLine.*;

/**
 * Command
 *
 * @author h2cone
 */
@Slf4j
@Component
@Command(
        name = "pubimg", description = "Publish image from source registry to target registry",
        mixinStandardHelpOptions = true,
        versionProvider = Ver.class
)
public class Cmd implements Callable<Integer> {
    @Option(names = {"-s"}, required = true, description = "Source registry alias")
    String source;
    @Option(names = {"-n"}, required = true, description = "Source name")
    String name;
    @Option(names = {"-prd"}, defaultValue = "3", description = "Progress refresh delay in seconds")
    int delay;
    @Option(names = {"-ns"}, required = true, description = "Target namespace")
    String namespace;
    @Parameters(description = "Target registry aliase")
    String target;
    @Option(names = {"-rp"}, description = "Path to registry.properties")
    String regConf;

    Progress progress;
    transient AtomicBoolean completed = new AtomicBoolean(false);

    static class Progress {
        public double value;
        public String desc;

        @Override
        public String toString() {
            return desc + "(" + value + ")";
        }
    }

    ScheduledExecutorService scheduled = Executors.newSingleThreadScheduledExecutor();

    @Override
    public Integer call() {
        if (Objects.isNull(regConf)) {
            if (App.registries.isEmpty()) {
                log.error("Registry properties not found, please specify -rp=path/to/registry.properties");
                return 1;
            } else {
                log.warn("Registry properties not specified, use built-in");
            }
        } else {
            try (var is = new FileInputStream(regConf)) {
                App.registries.load(is);
            } catch (IOException e) {
                log.error("Load registry properties failed", e);
                return 1;
            }
        }
        var sourceImgRef = getImageRef(this.source, this.name);
        if (Objects.isNull(sourceImgRef)) {
            log.error("Source registry image not found: {} {}", source, name);
            return 0;
        }
        var imageName = sourceImgRef.substring(sourceImgRef.lastIndexOf("/") + 1);
        progress = new Progress() {{
            value = 0;
            desc = "0.00%";
        }};
        RetryPolicy<Object> policy = RetryPolicy.builder()
                .handle(SocketException.class)
                .withDelay(Duration.ofSeconds(3))
                .withMaxRetries(Integer.MAX_VALUE)
                .build();
        Failsafe.with(policy).runAsync(() -> publish(sourceImgRef, target, namespace, imageName));
        scheduled.scheduleWithFixedDelay(() -> log.info("{}", progress), 0, delay, TimeUnit.SECONDS);
        while (!completed.get()) {
            Thread.onSpinWait();
        }
        scheduled.shutdown();
        log.info("All tasks completed");
        return 0;
    }

    void publish(String sourceImgRef, String target, String imageDir, String imageName) throws InvalidImageReferenceException, CacheDirectoryCreationException, IOException, ExecutionException, InterruptedException, RegistryException {
        var targetImgRef = getImageRef(target, imageDir, imageName);
        if (Objects.isNull(targetImgRef)) {
            log.error("Target registry image invalid {}", target);
            return;
        }
        var targetUsername = App.registries.getProperty(target + ".username");
        var targetPassword = App.registries.getProperty(target + ".password");
        var sourceUsername = App.registries.getProperty(target + ".username");
        var sourcePassword = App.registries.getProperty(target + ".password");
        var sourceImage = RegistryImage.named(sourceImgRef).addCredential(sourceUsername, sourcePassword);
        var targetImage = RegistryImage.named(targetImgRef).addCredential(targetUsername, targetPassword);
        JibContainer jc = Jib.from(sourceImage).containerize(Containerizer.to(targetImage)
                .addEventHandler(LogEvent.class, evt -> log.info(evt.getMessage()))
                .addEventHandler(ProgressEvent.class, evt -> {
                    Allocation allocation = evt.getAllocation();
                    var fractionOfRoot = allocation.getFractionOfRoot();
                    var units = evt.getUnits();
                    progress.value += fractionOfRoot * units;
                    progress.desc = String.format("%.2f%%", progress.value * 100);
                }));
        if (jc.isImagePushed()) {
            log.info("Publish image completed: {}", target);
            completed.set(true);
        } else {
            log.error("Publish image failed: {}", target);
        }
    }

    String getImageRef(String registry, String... names) {
        var host = App.registries.getProperty(registry + ".host");
        if (Objects.isNull(host)) {
            return null;
        }
        return host + "/" + String.join("/", names);
    }
}
