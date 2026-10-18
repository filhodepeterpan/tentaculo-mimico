package br.com.tentaculomimico.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.Transformation;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/** Upload de imagens pro Cloudinary (mesma lógica que estava privada no CursoController). */
@Service
public class ImagemService {

    private static final int TAMANHO_MAXIMO_PX = 1600;

    private final Cloudinary cloudinary;

    public ImagemService(Cloudinary cloudinary) {
        this.cloudinary = cloudinary;
    }

    /** Devolve a URL https da imagem, ou null se nenhum arquivo foi enviado. */
    public String enviar(MultipartFile arquivo, String pasta) {
        if (arquivo == null || arquivo.isEmpty()) {
            return null;
        }
        String tipo = arquivo.getContentType();
        if (tipo == null || !tipo.startsWith("image/")) {
            throw new RuntimeException("O arquivo enviado não é uma imagem válida.");
        }
        try {
            Map<String, Object> opcoes = ObjectUtils.asMap(
                    "folder", "tentaculo-mimico/" + pasta,
                    "resource_type", "image",
                    // Comprime no upload: reduz só se passar de 1600px e usa qualidade automática
                    "transformation", new Transformation()
                            .width(TAMANHO_MAXIMO_PX).height(TAMANHO_MAXIMO_PX).crop("limit")
                            .quality("auto"));
            Map<?, ?> resultado = cloudinary.uploader().upload(arquivo.getBytes(), opcoes);
            String url = (String) resultado.get("secure_url");
            // Na entrega, serve WebP/AVIF quando o navegador suporta
            return url.replace("/upload/", "/upload/f_auto,q_auto/");
        } catch (IOException e) {
            throw new RuntimeException("Não foi possível enviar a imagem. Tente novamente.");
        }
    }
}