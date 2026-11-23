package com.collectionhub.infrastructure.fineract;

import java.net.http.HttpClient;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FineractConfiguration {
    @Bean
    HttpClient fineractHttpClient(@Value("${fineract.insecure-ssl:false}") boolean insecureSsl) throws Exception {
        HttpClient.Builder builder = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER);
        if (insecureSsl) {
            // ÚNICAMENTE desarrollo local. En producción usar truststore y validación TLS.
            TrustManager[] trustAll = {new X509TrustManager() {
                public void checkClientTrusted(X509Certificate[] chain, String authType) {}
                public void checkServerTrusted(X509Certificate[] chain, String authType) {}
                public X509Certificate[] getAcceptedIssuers() {return new X509Certificate[0];}
            }};
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, trustAll, new SecureRandom());
            builder.sslContext(context);
            // El certificado de Fineract local puede no coincidir con localhost.
            System.setProperty("jdk.internal.httpclient.disableHostnameVerification", "true");
        }
        return builder.build();
    }
}
