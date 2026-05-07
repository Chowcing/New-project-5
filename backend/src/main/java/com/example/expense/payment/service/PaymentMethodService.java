package com.example.expense.payment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.expense.payment.dto.PaymentMethodRequest;
import com.example.expense.payment.entity.PaymentMethod;
import com.example.expense.payment.mapper.PaymentMethodMapper;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PaymentMethodService {
    private final PaymentMethodMapper paymentMethodMapper;

    public PaymentMethodService(PaymentMethodMapper paymentMethodMapper) {
        this.paymentMethodMapper = paymentMethodMapper;
    }

    public List<PaymentMethod> list(Long userId) {
        return selectOwnedList(userId);
    }

    public PaymentMethod create(Long userId, PaymentMethodRequest request) {
        PaymentMethod method = toEntity(new PaymentMethod(), userId, request);
        paymentMethodMapper.insert(method);
        return method;
    }

    public PaymentMethod update(Long userId, Long id, PaymentMethodRequest request) {
        PaymentMethod method = requireOwned(userId, id);
        toEntity(method, userId, request);
        paymentMethodMapper.updateById(method);
        return method;
    }

    public void delete(Long userId, Long id) {
        requireOwned(userId, id);
        paymentMethodMapper.deleteById(id);
    }

    public PaymentMethod requireOwned(Long userId, Long id) {
        PaymentMethod method = paymentMethodMapper.selectOne(new LambdaQueryWrapper<PaymentMethod>()
                .eq(PaymentMethod::getId, id)
                .eq(PaymentMethod::getUserId, userId));
        if (method == null) {
            throw new IllegalArgumentException("支付方式不存在");
        }
        return method;
    }

    public void createDefaults(Long userId) {
        createDefault(userId, "微信", "wechat-pay", 10);
        createDefault(userId, "支付宝", "alipay", 20);
        createDefault(userId, "现金", "cash-back-record", 30);
        createDefault(userId, "银行卡转账", "balance-o", 40);
    }

    private List<PaymentMethod> selectOwnedList(Long userId) {
        return paymentMethodMapper.selectList(new LambdaQueryWrapper<PaymentMethod>()
                .eq(PaymentMethod::getUserId, userId)
                .orderByAsc(PaymentMethod::getSortOrder)
                .orderByDesc(PaymentMethod::getId));
    }

    private void createDefault(Long userId, String name, String icon, int sortOrder) {
        PaymentMethod method = new PaymentMethod();
        method.setUserId(userId);
        method.setName(name);
        method.setIcon(icon);
        method.setSortOrder(sortOrder);
        paymentMethodMapper.insert(method);
    }

    private PaymentMethod toEntity(PaymentMethod method, Long userId, PaymentMethodRequest request) {
        method.setUserId(userId);
        method.setName(request.name());
        method.setIcon(request.icon());
        method.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        return method;
    }
}
