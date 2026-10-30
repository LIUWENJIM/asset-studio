package dev.liuwenjie.assetstudio.asset;

import dev.liuwenjie.assetstudio.identity.CurrentUserId;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/assets")
public class AssetController {

    private final AssetService assetService;

    public AssetController(AssetService assetService) {
        this.assetService = assetService;
    }

    public record AssetResponse(String assetId, String url, String mimeType, long sizeBytes) {
        static AssetResponse from(Asset asset) {
            return new AssetResponse(asset.id(), "/files/" + asset.storedPath(), asset.mimeType(), asset.sizeBytes());
        }
    }

    @PostMapping
    public AssetResponse upload(@CurrentUserId String userId, @RequestParam("file") MultipartFile file) {
        return AssetResponse.from(assetService.store(userId, file));
    }
}