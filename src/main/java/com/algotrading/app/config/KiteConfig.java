package com.algotrading.app.config;

import com.zerodhatech.kiteconnect.KiteConnect;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Produces the {@link KiteConnect} Spring bean.
 *
 * <p>On startup the bean is initialised with only the {@code apiKey} and
 * {@code userId}.  It has <em>no</em> access token yet — that arrives later
 * via the OAuth callback flow handled by {@link com.algotrading.app.auth.KiteAuthService}.
 * The {@link com.algotrading.app.auth.KiteSessionStore} owns the live token
 * and keeps the shared bean up to date after every successful login.</p>
 */
@Configuration
@EnableConfigurationProperties(KiteProperties.class)
public class KiteConfig {

    private final KiteProperties props;

    public KiteConfig(KiteProperties props) {
        this.props = props;
    }

    /**
     * Shared {@link KiteConnect} instance.
     * Access token is injected at runtime by
     * {@link com.algotrading.app.auth.KiteAuthService#handleCallback(String)}.
     */
    @Bean
    public KiteConnect kiteConnect() {
        KiteConnect kite = new KiteConnect(props.apiKey());
        kite.setUserId(props.userId());
        // Access token is NOT set here – it is set after the OAuth callback.
        // Any API call before login will throw Kite's "Incorrect api_key or
        // access_token" error, which is the expected behaviour pre-auth.
        return kite;
    }
}