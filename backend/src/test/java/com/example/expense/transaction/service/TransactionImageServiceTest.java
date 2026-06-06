package com.example.expense.transaction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.expense.common.config.StorageProperties;
import com.example.expense.transaction.dto.TransactionImageContent;
import com.example.expense.transaction.entity.ExpenseTransaction;
import com.example.expense.transaction.entity.TransactionImage;
import com.example.expense.transaction.mapper.TransactionImageMapper;
import com.example.expense.transaction.mapper.TransactionMapper;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class TransactionImageServiceTest {
    private static final Long USER_ID = 1001L;
    private static final Long TRANSACTION_ID = 88L;
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-05-27T00:00:00Z"), ZoneId.of("Asia/Shanghai"));
    private static final byte[] JPEG_BYTES = new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x01};
    private static final byte[] PNG_BYTES = new byte[] {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x01
    };
    private static final byte[] HEIC_BYTES = new byte[] {
            0x00, 0x00, 0x00, 0x18, 0x66, 0x74, 0x79, 0x70, 0x68, 0x65, 0x69, 0x63,
            0x00, 0x00, 0x00, 0x00
    };

    @TempDir
    private Path tempDir;
    @Mock
    private TransactionImageMapper imageMapper;
    @Mock
    private TransactionMapper transactionMapper;

    private TransactionImageService service;

    @BeforeEach
    void setUp() {
        StorageProperties properties = new StorageProperties();
        properties.setTransactionImageDir(tempDir.resolve("transaction-images").toString());
        service = new TransactionImageService(imageMapper, transactionMapper, properties, CLOCK);
    }

    @Test
    void storeImagesWritesFileAndMetadataUnderDateAndUserDirectory() {
        ExpenseTransaction transaction = transaction();
        MockMultipartFile file = new MockMultipartFile(
                "images",
                "receipt.png",
                "image/png",
                PNG_BYTES);
        when(imageMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        doAnswer(invocation -> {
            TransactionImage image = invocation.getArgument(0);
            image.setId(501L);
            return 1;
        }).when(imageMapper).insert(any(TransactionImage.class));

        service.storeImages(USER_ID, transaction, List.of(file));

        ArgumentCaptor<TransactionImage> captor = ArgumentCaptor.forClass(TransactionImage.class);
        org.mockito.Mockito.verify(imageMapper).insert(captor.capture());
        TransactionImage image = captor.getValue();
        assertThat(image.getOriginalFilename()).isEqualTo("receipt.png");
        assertThat(image.getContentType()).isEqualTo("image/png");
        assertThat(image.getSizeBytes()).isEqualTo((long) PNG_BYTES.length);
        assertThat(image.getSortOrder()).isEqualTo(1);
        assertThat(image.getRelativePath()).startsWith("2026-05-14/user-1001/");
        assertThat(image.getStoredFilename()).startsWith("transaction-88-午餐-01-");
        assertThat(Files.exists(tempDir.resolve("transaction-images").resolve(image.getRelativePath()))).isTrue();
    }

    @Test
    void validateRejectsUnsupportedTypeAndOversizeFiles() {
        MockMultipartFile text = new MockMultipartFile("images", "a.txt", "text/plain", new byte[] {1});
        MockMultipartFile large = new MockMultipartFile(
                "images",
                "large.jpg",
                "image/jpeg",
                new byte[(int) TransactionImageService.MAX_IMAGE_SIZE_BYTES + 1]);

        assertThatThrownBy(() -> service.validateFiles(List.of(text)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("仅支持 JPG、PNG、WebP、HEIC/HEIF 图片");
        assertThatThrownBy(() -> service.validateFiles(List.of(large)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("单张图片不能超过 5MB");
    }

    @Test
    void validateRejectsImageWhenContentDoesNotMatchDeclaredType() {
        MockMultipartFile spoofed = new MockMultipartFile("images", "fake.jpg", "image/jpeg", "not an image".getBytes());

        assertThatThrownBy(() -> service.validateFiles(List.of(spoofed)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("图片文件内容与类型不匹配");
    }

    @Test
    void validateAcceptsImagesUpToFiveMb() {
        byte[] image = new byte[4 * 1024 * 1024];
        image[0] = (byte) 0xFF;
        image[1] = (byte) 0xD8;
        image[2] = (byte) 0xFF;

        service.validateFiles(List.of(new MockMultipartFile("images", "receipt.jpg", "image/jpeg", image)));
    }

    @Test
    void validateAcceptsHeicPhotosFromIos() {
        MockMultipartFile heic = new MockMultipartFile("images", "phone.heic", "image/heic", HEIC_BYTES);

        service.validateFiles(List.of(heic));
    }

    @Test
    void appendRejectsWhenTotalImageCountExceedsLimit() {
        when(transactionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(transaction());
        when(imageMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(2L);
        MockMultipartFile first = new MockMultipartFile("images", "a.jpg", "image/jpeg", JPEG_BYTES);
        MockMultipartFile second = new MockMultipartFile("images", "b.jpg", "image/jpeg", JPEG_BYTES);

        assertThatThrownBy(() -> service.appendImages(USER_ID, TRANSACTION_ID, List.of(first, second)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("单笔记录最多上传 3 张图片");
    }

    @Test
    void storeImagesDeletesAlreadyWrittenFilesWhenLaterImageFails() throws Exception {
        ExpenseTransaction transaction = transaction();
        MockMultipartFile first = new MockMultipartFile("images", "a.jpg", "image/jpeg", JPEG_BYTES);
        MockMultipartFile second = new MockMultipartFile("images", "b.jpg", "image/jpeg", JPEG_BYTES);
        when(imageMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        doAnswer(invocation -> {
            TransactionImage image = invocation.getArgument(0);
            if (image.getSortOrder() == 2) {
                throw new IllegalStateException("数据库写入失败");
            }
            image.setId(501L);
            return 1;
        }).when(imageMapper).insert(any(TransactionImage.class));

        assertThatThrownBy(() -> service.storeImages(USER_ID, transaction, List.of(first, second)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("数据库写入失败");

        assertThat(countStoredFiles()).isZero();
    }

    @Test
    void deleteImageSoftDeletesDatabaseRecordAndKeepsPhysicalFileForDelayedCleanup() throws Exception {
        ExpenseTransaction transaction = transaction();
        TransactionImage image = image("2026-05-14/user-1001/receipt.jpg");
        Path file = tempDir.resolve("transaction-images").resolve(image.getRelativePath());
        Files.createDirectories(file.getParent());
        Files.write(file, new byte[] {1, 2});
        when(transactionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(transaction);
        when(imageMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(image);

        service.deleteImage(USER_ID, TRANSACTION_ID, 501L);

        verify(imageMapper).deleteById(501L);
        assertThat(Files.exists(file)).isTrue();
    }

    @Test
    void cleanupDeletedPhysicalFilesDeletesExpiredFileAndMarksMetadata() throws Exception {
        TransactionImage image = image("2026-05-14/user-1001/receipt.jpg");
        image.setDeleted(1);
        Path file = tempDir.resolve("transaction-images").resolve(image.getRelativePath());
        Files.createDirectories(file.getParent());
        Files.write(file, new byte[] {1, 2});
        when(imageMapper.selectPhysicalCleanupCandidates(any(LocalDateTime.class), anyInt()))
                .thenReturn(List.of(image), List.of());

        int cleaned = service.cleanupDeletedPhysicalFiles();

        assertThat(cleaned).isEqualTo(1);
        assertThat(Files.exists(file)).isFalse();
        verify(imageMapper).markPhysicalDeleted(eq(501L), any(LocalDateTime.class));
    }

    @Test
    void readImageRequiresOwnedTransactionAndImage() throws Exception {
        ExpenseTransaction transaction = transaction();
        TransactionImage image = image("2026-05-14/user-1001/receipt.jpg");
        Path file = tempDir.resolve("transaction-images").resolve(image.getRelativePath());
        Files.createDirectories(file.getParent());
        Files.write(file, new byte[] {1, 2});
        when(transactionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(transaction);
        when(imageMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(image);

        TransactionImageContent content = service.readImage(USER_ID, TRANSACTION_ID, 501L);

        assertThat(content.contentType()).isEqualTo("image/jpeg");
        assertThat(content.sizeBytes()).isEqualTo(2L);
        assertThat(content.resource().exists()).isTrue();
    }

    private long countStoredFiles() throws Exception {
        Path root = tempDir.resolve("transaction-images");
        if (!Files.exists(root)) {
            return 0;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile).count();
        }
    }

    private TransactionImage image(String relativePath) {
        TransactionImage image = new TransactionImage();
        image.setId(501L);
        image.setUserId(USER_ID);
        image.setTransactionId(TRANSACTION_ID);
        image.setOriginalFilename("receipt.jpg");
        image.setContentType("image/jpeg");
        image.setSizeBytes(2L);
        image.setRelativePath(relativePath);
        return image;
    }

    private ExpenseTransaction transaction() {
        ExpenseTransaction transaction = new ExpenseTransaction();
        transaction.setId(TRANSACTION_ID);
        transaction.setUserId(USER_ID);
        transaction.setType("EXPENSE");
        transaction.setItemName("午餐");
        transaction.setAmount(new BigDecimal("12.50"));
        transaction.setOccurredAt(LocalDateTime.of(2026, 5, 14, 12, 0));
        transaction.setChannel("ONLINE");
        transaction.setOnlineApp("美团");
        transaction.setPaymentMethodId(3001L);
        transaction.setPaymentMethodName("微信");
        transaction.setCategoryId(2001L);
        transaction.setDeleted(0);
        return transaction;
    }
}
