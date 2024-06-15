package dev.h2cone.pubimg;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

import java.io.IOException;
import java.util.Objects;
import java.util.Properties;
import java.util.logging.LogManager;

import static picocli.CommandLine.IFactory;

/**
 * Application
 *
 * @author h2cone
 */
@Component
@SpringBootApplication
public class App implements CommandLineRunner, ExitCodeGenerator {
    private final Cmd cmd;
    private final IFactory factory;
    private int exitCode;

    public App(Cmd cmd, IFactory factory) {
        this.cmd = cmd;
        this.factory = factory;
    }

    @Override
    public void run(String... args) throws Exception {
        exitCode = new CommandLine(cmd, factory).execute(args);
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }

    public static void main(String[] args) {
        System.exit(SpringApplication.exit(SpringApplication.run(App.class, args)));
    }

    static final Properties registries;

    static {
        registries = new Properties();
        try (var is = App.class.getResourceAsStream("/registry.properties")) {
            if (Objects.nonNull(is)) {
                registries.load(is);
            }
        } catch (IOException e) {
            // ignore
        }
        try (var is = App.class.getResourceAsStream("/logging.properties")) {
            if (Objects.nonNull(is)) {
                LogManager.getLogManager().readConfiguration(is);
            }
        } catch (IOException | SecurityException | ExceptionInInitializerError ex) {
            // ignore
        }
    }
}
