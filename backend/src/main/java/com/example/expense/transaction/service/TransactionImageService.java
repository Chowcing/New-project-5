package com.example.expense.transaction.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.expense.common.config.StorageProperties;
import com.example.expense.transaction.dto.TransactionImageContent;
import com.example.expense.transaction.dto.TransactionImageResponse;
import com.example.expense.transaction.entity.ExpenseTransaction;
import com.example.expense.transaction.entity.TransactionImage;
import com.example.expense.transaction.mapper.TransactionImageMapper;
import com.example.expense.transaction.mapper.TransactionMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.Normalizer;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.PathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class TransactionImageService {
    private static final Logger log = LoggerFactory.getLogger(TransactionImageService.class);
    public static final int MAX_IMAGES_PER_TRANSACTION = 3;
    public static final long MAX_IMAGE_SIZE_BYTES = 5L * 1024 * 1024;
    private static final int CLEANUP_BATCH_LIMIT = 100;
    private static final DateTimeFormatter DIRECTORY_DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final Map<String, String> ALLOWED_TYPES = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp",
            "image/heic", "heic",
            "image/heif", "heif",
            "image/heic-sequence", "heic",
            "image/heif-sequence", "heif"
    );

    private final TransactionImageMapper imageMapper;
    private final TransactionMapper transactionMapper;
    private final Path imageRoot;
    private final int imageRetentionDays;
    private final Clock clock;

    public TransactionImageService(
            TransactionImageMapper imageMapper,
            TransactionMapper transactionMapper,
            StorageProperties storageProperties,
            Clock clock
    ) {
        this.imageMapper = imageMapper;
        this.transactionMapper = transactionMapper;
        this.imageRoot = Path.of(storageProperties.getTransactionImageDir()).normalize().toAbsolutePath();
        this.imageRetentionDays = Math.max(storageProperties.getTransactionImageRetentionDays(), 0);
        this.clock = clock;
    }

    public void validateFiles(List<MultipartFile> files) {
        List<MultipartFile> validFiles = normalizeFiles(files);
        if (validFiles.size() > MAX_IMAGES_PER_TRANSACTION) {
            throw new IllegalArgumentException("单笔记录最多上传 3 张图片");
        }
        for (MultipartFile file : validFiles) {
            validateFile(file);
        }
    }

    @Transactional
    public List<TransactionImageResponse> appendImages(Long userId, Long transactionId, List<MultipartFile> files) {
        ExpenseTransaction transaction = requireOwnedTransaction(userId, transactionId);
        return storeImages(userId, transaction, files);
    }

    @Transactional
    public List<TransactionImageResponse> storeImages(Long userId, ExpenseTransaction transaction, List<MultipartFile> files) {
        List<MultipartFile> validFiles = normalizeFiles(files);
        if (validFiles.isEmpty()) {
            return List.of();
        }
        for (MultipartFile file : validFiles) {
            validateFile(file);
        }
        int existingCount = countActiveImages(userId, transaction.getId());
        if (existingCount + validFiles.size() > MAX_IMAGES_PER_TRANSACTION) {
            throw new IllegalArgumentException("单笔记录最多上传 3 张图片");
        }

        List<TransactionImageResponse> responses = new ArrayList<>();
        List<TransactionImage> savedImages = new ArrayList<>();
        try {
            for (int index = 0; index < validFiles.size(); index++) {
                MultipartFile file = validFiles.get(index);
                TransactionImage image = saveImageFile(userId, transaction, file, existingCount + index + 1);
                savedImages.add(image);
                responses.add(toResponse(image));
            }
            return responses;
        } catch (RuntimeException ex) {
            try {
                deletePhysicalFiles(savedImages);
            } catch (RuntimeException cleanupEx) {
                ex.addSuppressed(cleanupEx);
            }
            throw ex;
        }
    }

    public List<TransactionImageResponse> listImages(Long userId, Long transactionId) {
        return selectActiveImages(userId, transactionId).stream()
                .map(this::toResponse)
                .toList();
    }

    public Map<Long, List<TransactionImageResponse>> listImagesByTransactionIds(Long userId, List<Long> transactionIds) {
        if (transactionIds == null || transactionIds.isEmpty()) {
            return Map.of();
        }
        List<TransactionImage> rows = imageMapper.selectList(new LambdaQueryWrapper<TransactionImage>()
                .eq(TransactionImage::getUserId, userId)
                .in(TransactionImage::getTransactionId, transactionIds)
                .eq(TransactionImage::getDeleted, 0)
                .orderByAsc(TransactionImage::getTransactionId)
                .orderByAsc(TransactionImage::getSortOrder)
                .orderByAsc(TransactionImage::getId));
        Map<Long, List<TransactionImageResponse>> result = new HashMap<>();
        for (TransactionImage row : rows) {
            result.computeIfAbsent(row.getTransactionId(), ignored -> new ArrayList<>()).add(toResponse(row));
        }
        return result;
    }

    @Transactional
    public void deleteImage(Long userId, Long transactionId, Long imageId) {
        requireOwnedTransaction(userId, transactionId);
        TransactionImage image = requireOwnedImage(userId, transactionId, imageId);
        imageMapper.deleteById(image.getId());
    }

    @Transactional
    public void softDeleteByTransaction(Long userId, Long transactionId) {
        List<TransactionImage> rows = selectActiveImages(userId, transactionId);
        for (TransactionImage row : rows) {
            imageMapper.deleteById(row.getId());
        }
    }

    public int cleanupDeletedPhysicalFiles() {
        LocalDateTime cutoff = LocalDateTime.now(clock).minusDays(imageRetentionDays);
        int cleaned = 0;
        while (true) {
            List<TransactionImage> rows = imageMapper.selectPhysicalCleanupCandidates(cutoff, CLEANUP_BATCH_LIMIT);
            if (rows.isEmpty()) {
                return cleaned;
            }

            int batchCleaned = 0;
            for (TransactionImage image : rows) {
                if (deletePhysicalFileForCleanup(image)) {
                    imageMapper.markPhysicalDeleted(image.getId(), LocalDateTime.now(clock));
                    cleaned++;
                    batchCleaned++;
                }
            }
            if (batchCleaned == 0) {
                return cleaned;
            }
        }
    }

    public TransactionImageContent readImage(Long userId, Long transactionId, Long imageId) {
        requireOwnedTransaction(userId, transactionId);
        TransactionImage image = requireOwnedImage(userId, transactionId, imageId);
        Path path = resolveImagePath(image);
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("图片不存在");
        }
        return new TransactionImageContent(
                new PathResource(path),
                image.getContentType(),
                image.getSizeBytes(),
                image.getOriginalFilename()
        );
    }

    private TransactionImage saveImageFile(Long userId, ExpenseTransaction transaction, MultipartFile file, int sortOrder) {
        String contentType = requireImageContentType(file);
        String extension = ALLOWED_TYPES.get(contentType);
        String dateDir = transaction.getOccurredAt().toLocalDate().format(DIRECTORY_DATE_FORMATTER);
        String userDir = "user-" + userId;
        Path targetDir = imageRoot.resolve(dateDir).resolve(userDir).normalize();
        if (!targetDir.startsWith(imageRoot)) {
            throw new IllegalArgumentException("图片目录无效");
        }

        String storedFilename = storedFilename(transaction, sortOrder, extension);
        Path targetPath = targetDir.resolve(storedFilename).normalize();
        try {
            Files.createDirectories(targetDir);
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
            TransactionImage image = new TransactionImage();
            image.setUserId(userId);
            image.setTransactionId(transaction.getId());
            image.setOriginalFilename(truncateOriginalFilename(file.getOriginalFilename()));
            image.setStoredFilename(storedFilename);
            image.setRelativePath(imageRoot.relativize(targetPath).toString().replace('\\', '/'));
            image.setContentType(contentType);
            image.setSizeBytes(file.getSize());
            image.setSortOrder(sortOrder);
            imageMapper.insert(image);
            return image;
        } catch (IOException ex) {
            log.error(
                    "交易图片保存失败 userId={} transactionId={} imageRoot={} targetPath={} contentType={} sizeBytes={}",
                    userId,
                    transaction.getId(),
                    imageRoot,
                    targetPath,
                    contentType,
                    file.getSize(),
                    ex
            );
            throw new IllegalArgumentException("图片保存失败");
        } catch (RuntimeException ex) {
            try {
                Files.deleteIfExists(targetPath);
            } catch (IOException ignored) {
                // 数据库写入失败时尽量清理本次生成的文件，清理失败不覆盖原始错误。
            }
            throw ex;
        }
    }

    private void deletePhysicalFiles(List<TransactionImage> images) {
        for (TransactionImage image : images) {
            deletePhysicalFile(image);
        }
    }

    private void deletePhysicalFile(TransactionImage image) {
        Path path = resolveImagePath(image);
        try {
            Files.deleteIfExists(path);
        } catch (IOException ex) {
            log.error(
                    "交易图片文件删除失败 imageId={} userId={} transactionId={} imageRoot={} path={}",
                    image.getId(),
                    image.getUserId(),
                    image.getTransactionId(),
                    imageRoot,
                    path,
                    ex
            );
            throw new IllegalArgumentException("图片文件删除失败");
        }
    }

    private boolean deletePhysicalFileForCleanup(TransactionImage image) {
        Path path;
        try {
            path = resolveImagePath(image);
        } catch (RuntimeException ex) {
            log.warn(
                    "交易图片清理跳过 imageId={} userId={} transactionId={} relativePath={}",
                    image.getId(),
                    image.getUserId(),
                    image.getTransactionId(),
                    image.getRelativePath(),
                    ex
            );
            return false;
        }
        try {
            Files.deleteIfExists(path);
            return true;
        } catch (IOException ex) {
            log.warn(
                    "交易图片物理文件延迟清理失败 imageId={} userId={} transactionId={} path={}",
                    image.getId(),
                    image.getUserId(),
                    image.getTransactionId(),
                    path,
                    ex
            );
            return false;
        }
    }

    private Path resolveImagePath(TransactionImage image) {
        Path path = imageRoot.resolve(image.getRelativePath()).normalize();
        if (!path.startsWith(imageRoot)) {
            throw new IllegalArgumentException("图片路径无效");
        }
        return path;
    }

    private ExpenseTransaction requireOwnedTransaction(Long userId, Long transactionId) {
        ExpenseTransaction transaction = transactionMapper.selectOne(new LambdaQueryWrapper<ExpenseTransaction>()
                .eq(ExpenseTransaction::getId, transactionId)
                .eq(ExpenseTransaction::getUserId, userId)
                .eq(ExpenseTransaction::getDeleted, 0));
        if (transaction == null) {
            throw new IllegalArgumentException("记录不存在");
        }
        return transaction;
    }

    private TransactionImage requireOwnedImage(Long userId, Long transactionId, Long imageId) {
        TransactionImage image = imageMapper.selectOne(new LambdaQueryWrapper<TransactionImage>()
                .eq(TransactionImage::getId, imageId)
                .eq(TransactionImage::getUserId, userId)
                .eq(TransactionImage::getTransactionId, transactionId)
                .eq(TransactionImage::getDeleted, 0));
        if (image == null) {
            throw new IllegalArgumentException("图片不存在");
        }
        return image;
    }

    private List<TransactionImage> selectActiveImages(Long userId, Long transactionId) {
        return imageMapper.selectList(new LambdaQueryWrapper<TransactionImage>()
                .eq(TransactionImage::getUserId, userId)
                .eq(TransactionImage::getTransactionId, transactionId)
                .eq(TransactionImage::getDeleted, 0)
                .orderByAsc(TransactionImage::getSortOrder)
                .orderByAsc(TransactionImage::getId));
    }

    private int countActiveImages(Long userId, Long transactionId) {
        return Math.toIntExact(imageMapper.selectCount(new LambdaQueryWrapper<TransactionImage>()
                .eq(TransactionImage::getUserId, userId)
                .eq(TransactionImage::getTransactionId, transactionId)
                .eq(TransactionImage::getDeleted, 0)));
    }

    private List<MultipartFile> normalizeFiles(List<MultipartFile> files) {
        if (files == null) {
            return List.of();
        }
        return files.stream()
                .filter(file -> file != null && !file.isEmpty())
                .toList();
    }

    private void validateFile(MultipartFile file) {
        String contentType = normalizeContentType(file.getContentType());
        if (file.getSize() > MAX_IMAGE_SIZE_BYTES) {
            throw new IllegalArgumentException("单张图片不能超过 5MB");
        }
        String detectedContentType = detectImageContentType(file);
        if (detectedContentType == null) {
            if (!ALLOWED_TYPES.containsKey(contentType)) {
                throw new IllegalArgumentException("仅支持 JPG、PNG、WebP、HEIC/HEIF 图片");
            }
            throw new IllegalArgumentException("图片文件内容与类型不匹配");
        }
    }

    private String requireImageContentType(MultipartFile file) {
        String contentType = detectImageContentType(file);
        if (contentType == null) {
            throw new IllegalArgumentException("图片文件内容与类型不匹配");
        }
        return contentType;
    }

    private String detectImageContentType(MultipartFile file) {
        byte[] header;
        try (InputStream inputStream = file.getInputStream()) {
            header = inputStream.readNBytes(16);
        } catch (IOException ex) {
            throw new IllegalArgumentException("图片读取失败");
        }
        if (header.length >= 3
                && (header[0] & 0xFF) == 0xFF
                && (header[1] & 0xFF) == 0xD8
                && (header[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (header.length >= 8
                && (header[0] & 0xFF) == 0x89
                && header[1] == 0x50
                && header[2] == 0x4E
                && header[3] == 0x47
                && header[4] == 0x0D
                && header[5] == 0x0A
                && header[6] == 0x1A
                && header[7] == 0x0A) {
            return "image/png";
        }
        if (header.length >= 12
                && header[0] == 0x52
                && header[1] == 0x49
                && header[2] == 0x46
                && header[3] == 0x46
                && header[8] == 0x57
                && header[9] == 0x45
                && header[10] == 0x42
                && header[11] == 0x50) {
            return "image/webp";
        }
        if (header.length >= 12
                && header[4] == 0x66
                && header[5] == 0x74
                && header[6] == 0x79
                && header[7] == 0x70) {
            String brand = new String(header, 8, 4, java.nio.charset.StandardCharsets.US_ASCII);
            if (List.of("heic", "heix", "hevc", "hevx", "heim", "heis", "hevm", "hevs").contains(brand)) {
                return "image/heic";
            }
            if (List.of("mif1", "msf1", "heif").contains(brand)) {
                return "image/heif";
            }
        }
        return null;
    }

    private String normalizeContentType(String contentType) {
        return contentType == null ? "" : contentType.trim().toLowerCase(Locale.ROOT);
    }

    private String storedFilename(ExpenseTransaction transaction, int sortOrder, String extension) {
        return "transaction-%d-%s-%02d-%s.%s".formatted(
                transaction.getId(),
                sanitizeItemName(transaction.getItemName()),
                sortOrder,
                UUID.randomUUID().toString().replace("-", "").substring(0, 8),
                extension
        );
    }

    private String sanitizeItemName(String value) {
        String normalized = Normalizer.normalize(value == null ? "" : value.trim(), Normalizer.Form.NFKC)
                .replaceAll("[^\\p{IsHan}\\p{IsAlphabetic}\\p{IsDigit}_-]+", "-")
                .replaceAll("-{2,}", "-")
                .replaceAll("^-|-$", "");
        if (normalized.isBlank()) {
            return "record";
        }
        return normalized.length() > 24 ? normalized.substring(0, 24) : normalized;
    }

    private String truncateOriginalFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "image";
        }
        String normalized = Path.of(filename).getFileName().toString();
        return normalized.length() > 255 ? normalized.substring(0, 255) : normalized;
    }

    private TransactionImageResponse toResponse(TransactionImage image) {
        return new TransactionImageResponse(
                image.getId(),
                image.getOriginalFilename(),
                image.getContentType(),
                image.getSizeBytes(),
                "/api/v1/transactions/%d/images/%d".formatted(image.getTransactionId(), image.getId()),
                image.getSortOrder()
        );
    }
}
