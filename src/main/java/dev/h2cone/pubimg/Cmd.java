package dev.h2cone.pubimg;

import com.google.cloud.tools.jib.api.*;
import com.google.cloud.tools.jib.event.events.ProgressEvent;
import com.google.cloud.tools.jib.event.progress.Allocation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static picocli.CommandLine.*;

/**
 * Command
 *
 * @author h2cone
 */
@Slf4j
@Component
@Command(
        name = "pubimg", description = "Publish image from source registry to target registries",
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
    @Parameters(description = "Target registry aliases")
    ArrayList<String> targets;
    @Option(names = {"-rp"}, description = "Path to registry.properties")
    String regConf;

    Map<String, Progress> targetToProgress;

    static class Progress {
        public double value;
        public String desc;

        @Override
        public String toString() {
            return desc + "(" + value + ")";
        }
    }

    ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    ScheduledExecutorService scheduled = Executors.newSingleThreadScheduledExecutor();

    @Override
    public Integer call() {
        if (Objects.isNull(regConf)) {
            if (App.registries.isEmpty()) {
                log.error("registry properties not found, please specify -rp=path/to/registry.properties");
                return 1;
            } else {
                log.warn("registry properties not specified, use built-in");
            }
        } else {
            try (var is = new FileInputStream(regConf)) {
                App.registries.load(is);
            } catch (IOException e) {
                log.error("load registry properties failed", e);
                return 1;
            }
        }
        var sourceImgRef = getImageRef(this.source, this.name);
        if (Objects.isNull(sourceImgRef)) {
            log.error("source registry image not found: {} {}", source, name);
            return 0;
        }
        var imageName = sourceImgRef.substring(sourceImgRef.lastIndexOf("/") + 1);
        targetToProgress = targets.stream().collect(Collectors.toMap(target -> target, _ -> new Progress() {{
            value = 0;
            desc = "0.00%";
        }}));
        targets.forEach(target -> executor.submit(() -> publish(sourceImgRef, target, namespace, imageName)));
        scheduled.scheduleWithFixedDelay(() -> log.info("{}", targetToProgress), 0, delay, TimeUnit.SECONDS);
        while (!targetToProgress.isEmpty()) {
            Thread.onSpinWait();
        }
        log.info("all tasks completed");
        return 0;
    }

    void publish(String sourceImgRef, String target, String imageDir, String imageName) {
        var targetImgRef = getImageRef(target, imageDir, imageName);
        if (Objects.isNull(targetImgRef)) {
            log.error("target registry image invalid {}", target);
            return;
        }
        try {
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
                        var progress = targetToProgress.get(target);
                        progress.value += fractionOfRoot * units;
                        progress.desc = String.format("%.2f%%", progress.value * 100);
                    }));
            if (jc.isImagePushed()) {
                log.info("publish image completed: {}", target);
                targetToProgress.remove(target);
            } else {
                log.error("publish image failed: {}", target);
            }
        } catch (InterruptedException | RegistryException | IOException | CacheDirectoryCreationException |
                 ExecutionException | InvalidImageReferenceException e) {
            log.error("publish image failed: " + target, e);
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
