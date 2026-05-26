package com.example.expense.common.init;

import java.util.List;

public final class DefaultDataSeeds {
    public static final List<CategorySeed> CATEGORY_SEEDS = List.of(
            new CategorySeed("餐饮", "EXPENSE", "shop-o", 10),
            new CategorySeed("交通", "EXPENSE", "logistics", 20),
            new CategorySeed("购物", "EXPENSE", "cart-o", 30),
            new CategorySeed("买菜", "EXPENSE", "bag-o", 40),
            new CategorySeed("外卖", "EXPENSE", "shop-o", 50),
            new CategorySeed("零食饮料", "EXPENSE", "cart-circle-o", 60),
            new CategorySeed("居住", "EXPENSE", "home-o", 70),
            new CategorySeed("水电燃气", "EXPENSE", "fire-o", 80),
            new CategorySeed("通讯网络", "EXPENSE", "phone-o", 90),
            new CategorySeed("娱乐", "EXPENSE", "music-o", 100),
            new CategorySeed("社交", "EXPENSE", "friends-o", 110),
            new CategorySeed("人情", "EXPENSE", "gift-o", 120),
            new CategorySeed("医疗", "EXPENSE", "shield-o", 130),
            new CategorySeed("教育", "EXPENSE", "bookmark-o", 140),
            new CategorySeed("旅行", "EXPENSE", "hotel-o", 150),
            new CategorySeed("宠物", "EXPENSE", "smile-o", 160),
            new CategorySeed("育儿", "EXPENSE", "like-o", 170),
            new CategorySeed("数码", "EXPENSE", "desktop-o", 180),
            new CategorySeed("服饰", "EXPENSE", "bag-o", 190),
            new CategorySeed("美妆", "EXPENSE", "flower-o", 200),
            new CategorySeed("运动健身", "EXPENSE", "fire-o", 210),
            new CategorySeed("金融保险", "EXPENSE", "shield-o", 220),
            new CategorySeed("会员订阅", "EXPENSE", "gem-o", 230),
            new CategorySeed("办公学习", "EXPENSE", "records-o", 240),
            new CategorySeed("汽车", "EXPENSE", "logistics", 250),
            new CategorySeed("家居", "EXPENSE", "wap-home-o", 260),
            new CategorySeed("烟酒", "EXPENSE", "hot-o", 270),
            new CategorySeed("公益捐赠", "EXPENSE", "good-job-o", 280),
            new CategorySeed("其他", "EXPENSE", "records-o", 990),
            new CategorySeed("工资", "INCOME", "paid", 10),
            new CategorySeed("奖金", "INCOME", "gold-coin-o", 20),
            new CategorySeed("兼职", "INCOME", "manager-o", 30),
            new CategorySeed("投资理财", "INCOME", "chart-trending-o", 40),
            new CategorySeed("报销", "INCOME", "balance-list-o", 50),
            new CategorySeed("退款", "INCOME", "refund-o", 60),
            new CategorySeed("其他收入", "INCOME", "cash-back-record", 990)
    );

    public static final List<PaymentMethodSeed> PAYMENT_METHOD_SEEDS = List.of(
            new PaymentMethodSeed("微信", "wechat-pay", 10),
            new PaymentMethodSeed("支付宝", "alipay", 20),
            new PaymentMethodSeed("银行卡", "balance-o", 30),
            new PaymentMethodSeed("云闪付", "ecard-pay", 40),
            new PaymentMethodSeed("现金", "cash-back-record", 50),
            new PaymentMethodSeed("数字人民币", "gold-coin-o", 60)
    );

    public static final List<OnlinePlatformSeed> ONLINE_PLATFORM_SEEDS = List.of(
            new OnlinePlatformSeed("淘宝", "shop-o", 10),
            new OnlinePlatformSeed("天猫", "cart-o", 20),
            new OnlinePlatformSeed("京东", "goods-collect-o", 30),
            new OnlinePlatformSeed("抖音", "video-o", 40),
            new OnlinePlatformSeed("小红书", "notes-o", 50),
            new OnlinePlatformSeed("携程旅行", "hotel-o", 60),
            new OnlinePlatformSeed("拼多多", "cart-circle-o", 70),
            new OnlinePlatformSeed("美团", "shop-o", 80),
            new OnlinePlatformSeed("闲鱼", "exchange", 90),
            new OnlinePlatformSeed("高德", "location-o", 100),
            new OnlinePlatformSeed("滴滴", "logistics", 110),
            new OnlinePlatformSeed("铁路12306", "logistics", 120),
            new OnlinePlatformSeed("哔哩哔哩", "tv-o", 130),
            new OnlinePlatformSeed("微信", "wechat-pay", 140),
            new OnlinePlatformSeed("支付宝", "alipay", 150),
            new OnlinePlatformSeed("饿了么", "shop-o", 160),
            new OnlinePlatformSeed("百度地图", "location-o", 170)
    );

    private DefaultDataSeeds() {
    }

    public record CategorySeed(String name, String type, String icon, int sortOrder) {
    }

    public record PaymentMethodSeed(String name, String icon, int sortOrder) {
    }

    public record OnlinePlatformSeed(String name, String icon, int sortOrder) {
    }
}
