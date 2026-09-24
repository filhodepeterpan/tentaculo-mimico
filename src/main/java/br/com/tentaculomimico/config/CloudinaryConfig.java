package br.com.tentaculomimico.config;

import com.cloudinary.Cloudinary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CloudinaryConfig {

    // Lido da variável de ambiente CLOUDINARY_URL, no formato
    // cloudinary://API_KEY:API_SECRET@CLOUD_NAME — o Cloudinary te dá essa
    // string pronta no painel deles (Dashboard > API Environment variable).
    // Mesmo esquema que já usamos com MONGO_URI: nunca hardcoded, nunca no Git.
    @Value("${CLOUDINARY_URL}")
    private String cloudinaryUrl;

    @Bean
    public Cloudinary cloudinary() {
        return new Cloudinary(cloudinaryUrl);
    }
}
