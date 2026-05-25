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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.Normalizer;
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
    public static final long MAX_IMAGE_SIZE_BYTES = 3L * 1024 * 1024;
    private static final DateTimeFormatter DIRECTORY_DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final Map<String, String> ALLOWED_TYPES = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp"
    );

    private final TransactionImageMapper imageMapper;
    private final TransactionMapper transactionMapper;
    private final Path imageRoot;

    public TransactionImageService(
            TransactionImageMapper imageMapper,
            TransactionMapper transactionMapper,
            StorageProperties storageProperties
    ) {
        this.imageMapper = imageMapper;
        this.transactionMapper = transactionMapper;
        this.imageRoot = Path.of(storageProperties.getTransactionImageDir()).normalize().toAbsolutePath();
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
        for (int index = 0; index < validFiles.size(); index++) {
            MultipartFile file = validFiles.get(index);
            TransactionImage image = saveImageFile(userId, transaction, file, existingCount + index + 1);
            responses.add(toResponse(image));
        }
        return responses;
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

    public TransactionImageContent readImage(Long userId, Long transactionId, Long imageId) {
        requireOwnedTransaction(userId, transactionId);
        TransactionImage image = requireOwnedImage(userId, transactionId, imageId);
        Path path = imageRoot.resolve(image.getRelativePath()).normalize();
        if (!path.startsWith(imageRoot) || !Files.isRegularFile(path)) {
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
        String contentType = normalizeContentType(file.getContentType());
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
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
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
        if (!ALLOWED_TYPES.containsKey(contentType)) {
            throw new IllegalArgumentException("仅支持 JPG、PNG、WebP 图片");
        }
        if (file.getSize() > MAX_IMAGE_SIZE_BYTES) {
            throw new IllegalArgumentException("单张图片不能超过 3MB");
        }
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
