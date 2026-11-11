package com.example.parkngo.feedback;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.mail.MailProperties;
import org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThat;

class MailConfigurationTest {
    @TempDir Path directory;

    private ApplicationContextRunner runner(boolean privateFile) throws Exception {
        String source = Files.readString(Path.of("src/main/resources/application.properties"));
        Path mail = directory.resolve(".env.mail.properties");
        if (privateFile) Files.writeString(mail, "app.mail.enabled=true\nspring.mail.username=sender@example.com\nspring.mail.password=test-only-password\n");
        Path config = directory.resolve("application.properties");
        Files.writeString(config, source.replace("./.env.mail.properties", mail.toString()));
        return new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withPropertyValues("spring.config.location=" + config.toUri())
            .withConfiguration(AutoConfigurations.of(MailSenderAutoConfiguration.class))
            .withUserConfiguration(EmailService.class);
    }

    @Test void privateMailFileEnablesDelivery() throws Exception {
        runner(true).run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(EmailService.class);
            assertThat(context.getBean(MailProperties.class).getUsername()).isEqualTo("sender@example.com");
            assertThat(context.getBean(MailProperties.class).getPassword()).isEqualTo("test-only-password");
        });
    }

    @Test void absentPrivateFileLeavesMailDisabled() throws Exception {
        runner(false).run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(EmailService.class));
    }

    @Test void explicitDisableOverridesPrivateFile() throws Exception {
        runner(true).withPropertyValues("app.mail.enabled=false")
            .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(EmailService.class));
    }
}
