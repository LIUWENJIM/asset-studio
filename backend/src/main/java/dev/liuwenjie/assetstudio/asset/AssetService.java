package dev.liuwenjie.assetstudio.asset;

import dev.liuwenjie.assetstudio.common.ApiException;
import dev.liuwenjie.assetstudio.common.ErrorCode;
import dev.liuwenjie.assetstudio.config.StorageProperties;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 素材落盘与归属校验。
 * 文件写在 {@code {storage.root}/assets/{userId}/{assetId}.{ext}}，库里只存相对路径，
 * 这样迁移/挂卷时不用改数据。
 */
@Service
public class AssetService {

    /** 站点允许上传的图片类型；模板还能在此基础上再收紧。 */
    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp");

    private static final long MAX_UPLOAD_BYTES = 10L * 1024 * 1024;

    private final AssetMapper assetMapper;
    private final Path root;

    public AssetService(AssetMapper assetMapper, StorageProperties storageProperties) {
        this.assetMapper = assetMapper;
        this.root = Path.of(storageProperties.root()).toAbsolutePath().normalize();
    }

    public Path root() {
        return root;
    }

    public Asset store(String userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(ErrorCode.ASSET_EMPTY, "请选择要上传的图片", "file");
        }
        String mimeType = file.getContentType();
        String extension = mimeType == null ? null : EXTENSIONS.get(mimeType);
        if (extension == null) {
            throw new ApiException(ErrorCode.ASSET_TYPE_NOT_SUPPORTED, "只支持 JPG、PNG、WebP 图片", "file");
        }
        if (file.getSize() > MAX_UPLOAD_BYTES) {
            throw new ApiException(ErrorCode.ASSET_TOO_LARGE, "单张图片不能超过 10 MB", "file");
        }

        String assetId = UUID.randomUUID().toString();
        String storedPath = "assets/" + userId + "/" + assetId + "." + extension;
        Path target = root.resolve(storedPath).normalize();
        if (!target.startsWith(root)) {
            throw new ApiException(ErrorCode.UPLOAD_FAILED, "非法的存储路径", "file");
        }

        try (InputStream in = file.getInputStream()) {
            Files.createDirectories(target.getParent());
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new ApiException(ErrorCode.UPLOAD_FAILED, "图片保存失败，请重试", "file");
        }

        String originalName = file.getOriginalFilename() == null ? "image." + extension : file.getOriginalFilename();
        assetMapper.insert(assetId, userId, originalName, storedPath, mimeType, file.getSize(), LocalDateTime.now());

        return new Asset(assetId, userId, originalName, storedPath, mimeType, file.getSize(), LocalDateTime.now());
    }

    /** 读取并校验归属：拿别人的素材 id 提交任务必须被拦住。 */
    public Asset requireOwned(String userId, String assetId) {
        Asset asset = assetMapper.findById(assetId);
        if (asset == null) {
            throw new ApiException(ErrorCode.ASSET_NOT_FOUND, "素材不存在：" + assetId, "assetIds");
        }
        if (!asset.userId().equals(userId)) {
            throw new ApiException(ErrorCode.ASSET_NOT_OWNED, "素材不属于当前用户", "assetIds");
        }
        return asset;
    }
}