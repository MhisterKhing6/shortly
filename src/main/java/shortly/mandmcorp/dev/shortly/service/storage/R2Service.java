package shortly.mandmcorp.dev.shortly.service.storage;

import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Service
public class R2Service {

    private final S3Client r2Client;

    @Value("${r2.bucket}")
    private String bucket;

    /** Public URL base for serving objects — an r2.dev subdomain or a custom domain bound to the bucket. */
    @Value("${r2.public-base-url}")
    private String publicBaseUrl;

    public R2Service(S3Client r2Client) {
        this.r2Client = r2Client;
    }

    public String uploadBase64Image(String base64) {
        String contentType = "image/jpeg";
        String extension = "jpg";
        String data = base64;

        if (base64.contains(",")) {
            String prefix = base64.substring(0, base64.indexOf(","));
            data = base64.substring(base64.indexOf(",") + 1);
            if (prefix.contains("image/png")) { contentType = "image/png"; extension = "png"; }
            else if (prefix.contains("image/gif")) { contentType = "image/gif"; extension = "gif"; }
            else if (prefix.contains("image/webp")) { contentType = "image/webp"; extension = "webp"; }
        }

        byte[] bytes = Base64.getDecoder().decode(data);
        String key = "parcels/" + UUID.randomUUID() + "." + extension;

        r2Client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(contentType)
                        .build(),
                RequestBody.fromBytes(bytes));

        String base = publicBaseUrl.endsWith("/") ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1) : publicBaseUrl;
        return base + "/" + key;
    }

    public List<String> uploadImages(List<String> base64Images) {
        if (base64Images == null || base64Images.isEmpty()) return Collections.emptyList();
        return base64Images.stream().map(this::uploadBase64Image).collect(Collectors.toList());
    }
}
