package com.example.projet.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.net.ssl.*;
import java.net.Socket;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

@Configuration
public class SSLConfig {

    @Bean
    public SSLContext sslContext() throws NoSuchAlgorithmException, KeyManagementException {
        SSLContext sslContext = SSLContext.getInstance("TLS");

        // Create a trust manager that does not validate certificate chains
        TrustManager[] trustManagers = new TrustManager[] {
                new X509ExtendedTrustManager() {
                    @Override
                    public void checkClientTrusted(X509Certificate[] chain, String authType) {}
                    @Override
                    public void checkServerTrusted(X509Certificate[] chain, String authType) {}
                    @Override
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                    @Override
                    public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) {}
                    @Override
                    public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) {}
                    @Override
                    public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {}
                    @Override
                    public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {}
                }
        };

        sslContext.init(null, trustManagers, new SecureRandom());
        return sslContext;
    }
}
