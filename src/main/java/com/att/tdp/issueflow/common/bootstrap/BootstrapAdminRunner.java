package com.att.tdp.issueflow.common.bootstrap;

import com.att.tdp.issueflow.user.Role;
import com.att.tdp.issueflow.user.User;
import com.att.tdp.issueflow.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Seeds an initial ADMIN user when the {@code users} table is empty so that the smoke-test
 * environment has a credential to authenticate with. Disabled by setting
 * {@code issueflow.bootstrap.admin.enabled=false}.
 */
@Component
@ConditionalOnProperty(prefix = "issueflow.bootstrap.admin", name = "enabled", havingValue = "true", matchIfMissing = true)
public class BootstrapAdminRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminRunner.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final BootstrapAdminProperties props;

    public BootstrapAdminRunner(UserRepository userRepository,
                                PasswordEncoder passwordEncoder,
                                BootstrapAdminProperties props) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.props = props;
    }

    @Override
    public void run(String... args) {
        if (userRepository.count() > 0) {
            return;
        }
        User admin = User.builder()
            .username(props.getUsername())
            .email(props.getEmail())
            .fullName(props.getFullName())
            .role(Role.ADMIN)
            .passwordHash(passwordEncoder.encode(props.getPassword()))
            .build();
        userRepository.save(admin);
        log.warn("Bootstrap admin created: username={} (override via issueflow.bootstrap.admin.*)", props.getUsername());
    }

    @Component
    @ConfigurationProperties(prefix = "issueflow.bootstrap.admin")
    public static class BootstrapAdminProperties {
        private boolean enabled = true;
        private String username = "admin";
        private String email = "admin@issueflow.local";
        private String fullName = "Bootstrap Admin";
        private String password = "admin12345";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getFullName() { return fullName; }
        public void setFullName(String fullName) { this.fullName = fullName; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }
}
