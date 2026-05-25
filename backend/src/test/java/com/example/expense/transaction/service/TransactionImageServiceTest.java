package com.example.expense.transaction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
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
import java.time.LocalDateTime;
import java.util.List;
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
        service = new TransactionImageService(imageMapper, transactionMapper, properties);
    }

    @Test
    void storeImagesWritesFileAndMetadataUnderDateAndUserDirectory() {
        ExpenseTransaction transaction = transaction();
        MockMultipartFile file = new MockMultipartFile(
                "images",
                "receipt.png",
                "image/png",
                new byte[] {1, 2, 3});
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
        assertThat(image.getSizeBytes()).isEqualTo(3L);
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
                .hasMessage("仅支持 JPG、PNG、WebP 图片");
        assertThatThrownBy(() -> service.validateFiles(List.of(large)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("单张图片不能超过 3MB");
    }

    @Test
    void appendRejectsWhenTotalImageCountExceedsLimit() {
        when(transactionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(transaction());
        when(imageMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(2L);
        MockMultipartFile first = new MockMultipartFile("images", "a.jpg", "image/jpeg", new byte[] {1});
        MockMultipartFile second = new MockMultipartFile("images", "b.jpg", "image/jpeg", new byte[] {1});

        assertThatThrownBy(() -> service.appendImages(USER_ID, TRANSACTION_ID, List.of(first, second)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("单笔记录最多上传 3 张图片");
    }

    @Test
    void readImageRequiresOwnedTransactionAndImage() throws Exception {
        ExpenseTransaction transaction = transaction();
        TransactionImage image = new TransactionImage();
        image.setId(501L);
        image.setUserId(USER_ID);
        image.setTransactionId(TRANSACTION_ID);
        image.setOriginalFilename("receipt.jpg");
        image.setContentType("image/jpeg");
        image.setSizeBytes(2L);
        image.setRelativePath("2026-05-14/user-1001/receipt.jpg");
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
